package org.cometd.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.cometd.bayeux.Bayeux;
import org.cometd.bayeux.ChannelId;
import org.cometd.bayeux.Session;
import org.cometd.bayeux.server.Authorizer;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
//import org.cometd.bayeux.
import org.cometd.bayeux.server.ServerSession;
import org.eclipse.jetty.util.AttributesMap;
import java.sql.*;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

//Added for some testing:
import org.cometd.bayeux.client.*;

public class ServerChannelImpl implements ServerChannel, ConfigurableServerChannel
{
    private final BayeuxServerImpl _bayeux;
    private final ChannelId _id;
    private final AttributesMap _attributes = new AttributesMap();
    private final Set<ServerSession> _subscribers = new CopyOnWriteArraySet<ServerSession>();
    private final List<ServerChannelListener> _listeners = new CopyOnWriteArrayList<ServerChannelListener>();
    private final List<Authorizer> _authorizers = new CopyOnWriteArrayList<Authorizer>();
    private final boolean _meta;					//true if it is a meta channel or not
    private final boolean _broadcast;				//true if it is a broadcast-type channel
    private final boolean _service;					//true if it is a service channel.  Service channels don't broadcast.
    private final CountDownLatch _initialized;
    private boolean _lazy;
    private boolean _persistent;					//true if it is a persistent channel.  Persistent channels outlast and outlive their subscribers.
    private volatile int _sweeperPasses = 0;
    private boolean _parse;							//true if this is a whiteboard channel that needs to be parsed
    private HashMap<String, String> _conflictMap;	//This conflict map stores the objects in a channel that are locked, so that no other sessions can use the object while it is locked.

    /* ------------------------------------------------------------ */
    /**
     * Added by Jadiel:
     * 
     * This is a comment on when or under which circumstances this constructor is called:
     * Six channels are created when the server is initialized:
     * 1. meta
     * 2. meta/handshake
     * 3. meta/connect
     * 4. meta/subscribe
     * 5. meta/unsubscribe
     * 6. meta/disconnect
     * 
     * When the first person joins the server, it will create two new channels:
     * 1. chat
     * 2. whiteboard
     * 
     * When the first person joins a section, it will create two new channels:
     * Note that section and session are not the same thing.  A section can have multiple sessions as subscribers.
     * A section is essentially a channel.  Each time that a new section is created, a new ServerChannel class is instantiated.
     * 1. chat/sectionName
     * 2. whiteboard/sectionName
     * 
     */
    protected ServerChannelImpl(BayeuxServerImpl bayeux, ChannelId id)
    {
    	
    	_bayeux=bayeux;
        _id=id;
        _meta=_id.isMeta();
        _service=_id.isService();
        _broadcast=!isMeta()&&!isService();
        _initialized=new CountDownLatch(1);
        setPersistent(!_broadcast);
        _parse=false;
        
        if (id.getSegment(0).equalsIgnoreCase("whiteboard")){
        	        	
        	if (id.depth()==3){
        		
        		_parse=true;
        		_conflictMap=new HashMap<String, String>();
        	}
        }
    }

    /* ------------------------------------------------------------ */
    /* wait for initialised call.
     * wait for bayeux max interval for the channel to be initialised,
     * which means waiting for addChild to finish calling bayeux.addChannel,
     * which calls all the listeners.
     *
     */
    void waitForInitialized()
    {
    	try
        {
            if (!_initialized.await(5,TimeUnit.SECONDS))
                throw new IllegalStateException("Not Initialized: "+this);
        }
        catch(InterruptedException e)
        {
            throw new IllegalStateException("Initialization interrupted: "+this);
        }
    }

    /* ------------------------------------------------------------ */
    void initialized()
    {
    	_initialized.countDown();
    }

    /* ------------------------------------------------------------ */
    /**
     * Added by Jadiel
     * Every time a new person joins the whiteboard subscribe will be called twice: 
     * a) Once for the chat/sectionName channel
     * b) another for the whiteboard/sectionName channel
     * @param session
     * @return true if the subscribe succeeded.
     */
    protected boolean subscribe(ServerSessionImpl session)
    {
    	if (!session.isHandshook())
            return false;

        if (_subscribers.add(session))
        {
	    	    
            session.subscribedTo(this);
            for (ServerChannelListener listener : _listeners)
                if (listener instanceof SubscriptionListener)
                    ((SubscriptionListener)listener).subscribed(session,this);
            for (BayeuxServer.BayeuxServerListener listener : _bayeux.getListeners())
                if (listener instanceof BayeuxServer.SubscriptionListener)
                    ((BayeuxServer.SubscriptionListener)listener).subscribed(session,this);
        }
        _sweeperPasses = 0;
        return true;
    }

    /**
     * Added by Jadiel.
     * This method is called each time a user leaves the Andes page.
     * It is called twice, one for each channel:
     * a) unsubscribe : /chat/sectionName
     * b) unsubscribe : /whiteboard/sectionName
     */
    protected void unsubscribe(ServerSessionImpl session)
    {
    	if(_subscribers.remove(session))
        {
            session.unsubscribedTo(this);
            for (ServerChannelListener listener : _listeners)
                if (listener instanceof SubscriptionListener)
                    ((SubscriptionListener)listener).unsubscribed(session,this);
            for (BayeuxServer.BayeuxServerListener listener : _bayeux.getListeners())
                if (listener instanceof BayeuxServer.SubscriptionListener)
                    ((BayeuxServer.SubscriptionListener)listener).unsubscribed(session,this);
        }
    }

    /* ------------------------------------------------------------ */
    public Set<ServerSession> getSubscribers()
    {
    	return Collections.unmodifiableSet(_subscribers);
    }

    /* ------------------------------------------------------------ */
    public boolean isBroadcast()
    {
    	return _broadcast;
    }

    /* ------------------------------------------------------------ */
    public boolean isDeepWild()
    {
    	return _id.isDeepWild();
    }

    /* ------------------------------------------------------------ */
    public boolean isLazy()
    {
        return _lazy;
    }

    /* ------------------------------------------------------------ */
    public boolean isPersistent()
    {
    	return _persistent;
    }

    /* ------------------------------------------------------------ */
    public boolean isWild()
    {
    	return _id.isWild();
    }

    /* ------------------------------------------------------------ */
    public void setLazy(boolean lazy)
    {
    	 
        _lazy=lazy;
    }

    /* ------------------------------------------------------------ */
    public void setPersistent(boolean persistent)
    {
        _persistent=persistent;
    }

    /* ------------------------------------------------------------ */
    public void addListener(ServerChannelListener listener)
    {
        _listeners.add(listener);
        _sweeperPasses = 0;
    }

    /* ------------------------------------------------------------ */
    public void removeListener(ServerChannelListener listener)
    {
    	_listeners.remove(listener);
    }

    /* ------------------------------------------------------------ */
    public List<ServerChannelListener> getListeners()
    {
    	return Collections.unmodifiableList(_listeners);
    }

    /* ------------------------------------------------------------ */
    public ChannelId getChannelId()
    {
        return _id;
    }

    /* ------------------------------------------------------------ */
    public String getId()
    {
        return _id.toString();
    }

    /* ------------------------------------------------------------ */
    public boolean isMeta()
    {
        return _meta;
    }

    /* ------------------------------------------------------------ */
    public boolean isService()
    {
        return _service;
    }

    /* ------------------------------------------------------------ */
    /**
     * Added by Jadiel
     * This method is called by both the whiteboard and the chat service.
     * Whiteboard: Currently is called when an object is created, modified (moved, size-changed, added letters), deleted
     * Chat: Called whenever somebody says something, including the first message ("Jadiel joined room xxxxx") and the last message ("Jadiel left room xxxxxx") 
     * 
     */
    public void publish(Session from, ServerMessage.Mutable mutable)
    {
        if (isWild())
	         throw new IllegalStateException("Wild publish");
    
        ServerSessionImpl session=(from instanceof ServerSessionImpl)
        ?(ServerSessionImpl)from
        :((from instanceof LocalSession)?(ServerSessionImpl)((LocalSession)from).getServerSession():null);
        
        //BEGIN of code added for testing:
      
        
        //END of code added for testing

        //We only parse if this is a whiteboard/session channel.
        if (_parse){
    		
    		//the JSON object looks like the following:
    		/*
    		 * {"id":"16","data":{"action":1,"user":"jadiel",
    		 * "drawing":{"id":"andes.Combo1","text":"","x-statement":517,"height":141,
    		 * "symbol":"","width":179,"action":"new-object","y-statement":80,
    		 * "type":"ellipse","y":80,"mode":"unknown","x":328}},
    		 * "channel":"/whiteboard/drawing/vec1ay54321",
    		 * "clientId":"1txt6cjrkxw4rjo6v88upmyuc"}
    		 */
        	
        	//action 1: new object, modify object
        	//action 2: object selected
        	//action 3: object deselected
        	//action 4: conflict
    		
    		JSONObject json = (JSONObject) JSONSerializer.toJSON(mutable.getJSON());
    		json =json.getJSONObject("data");
    		json=json.getJSONObject("drawing");
    		String objectAction=json.getString("action");
    		//parse the field that will uniquely identify the object.
    		if (objectAction.equals("set-score"))
    		{
				mutable.setClientId(null);
                
				if(_bayeux.extendSend(session,null,mutable))
					_bayeux.doPublish(session,this,mutable);
					
				return;
			}
			json = (JSONObject) JSONSerializer.toJSON(mutable.getJSON());
    		json =json.getJSONObject("data");
    		String ownerId=json.getString("user");
    		json=json.getJSONObject("drawing");
    		String objectId=json.getString("id");
    		objectAction=json.getString("action");
    		
    		//1. Object-selected: 
    		//1.1 Add to map if it does not exist.
    		//1.2 If it exists, send info that there is collision. 
    		if (objectAction.equalsIgnoreCase("delete-object")){
				
				if (_conflictMap.containsKey(objectId)){
					_conflictMap.remove(objectId);
				}
				mutable.setClientId(null);
                
				if(_bayeux.extendSend(session,null,mutable))
					_bayeux.doPublish(session,this,mutable);
				System.out.println(mutable.toString());
			}
    		else if (objectAction.equalsIgnoreCase("object-selected")) {
    	
    			//1.2 if the object already exists
    			if (_conflictMap.containsKey(objectId)){
    	
    				JSONObject json1 = (JSONObject) JSONSerializer.toJSON(mutable.getJSON());
    				json1.element("data", ((JSONObject)json1.get("data")).element("action", 4));
    				mutable.setData(json1.get("data"));
    				
    				_bayeux.doPublish(session,this,mutable);
    				System.out.println(mutable.toString());
    			}
    			
    			//1.1 if object does not exists 
    			else {
    				_bayeux.doPublish(session,this,mutable);
    				System.out.println(mutable.toString());
    				_conflictMap.put(objectId, ownerId);
    			}
    		}
    		
    		//2. Object-deselected
    		//2.1 if the object is deselected by the owner, send a modify-object to the client.
    		else if (objectAction.equalsIgnoreCase("object-deselected")){
    			
    			//TODO: Remove the potential of null-pointer exception in this method
    		
    			String ownerId1=_conflictMap.get(objectId);
    			
    			if(ownerId1!=null){
    				
    				if (ownerId1.equals(ownerId)){
    				
    					_conflictMap.remove(objectId);
    					JSONObject json1 = (JSONObject) JSONSerializer.toJSON(mutable.getJSON());
    					JSONObject json2 = json1.getJSONObject("data");
    					json2.element("drawing", json2.getJSONObject("drawing").element("action", "modify-object"));
    					json1.element("data", json2);	
    					mutable.setData(json1.get("data"));
    					
    					mutable.setClientId(null);
                
						if(_bayeux.extendSend(session,null,mutable))
							_bayeux.doPublish(session,this,mutable);
						System.out.println(mutable.toString());

    				}
    			}
    			
    		} else {
    		
    			if (_conflictMap.containsKey(objectId)){
    				String ownerIdNew=_conflictMap.get(objectId);
    				if (ownerIdNew!=null){
    					if (!ownerIdNew.equalsIgnoreCase(ownerId)) return;	
    				}
       			}
    			mutable.setClientId(null);
                
                if(_bayeux.extendSend(session,null,mutable))
                    _bayeux.doPublish(session,this,mutable);
                System.out.println(mutable.toString());
    			
    		}
    		
    	} else {
    		
    		mutable.setClientId(null);
            
            if(_bayeux.extendSend(session,null,mutable))
                _bayeux.doPublish(session,this,mutable);
            System.out.println(mutable.toString());
    
    	}
        
        //all this stuff right here is rather interesting.  I still marvel on how is that this
        //gets published to everybody when it really seems to be sent to only one person.
        
        	    
    	
    
    	//TODO: Uncomment this, it is commented because I am not using a database at all right now.
    	/*
    	// JVM: Added code for ConnectorJ 
        try{
        	CallableStatement csSProc = null;
        	Connection con = null;	
        	con = getConnection();	
        	Statement stmt=con.createStatement();
        	//	ResultSet rs;
        	stmt.executeUpdate("INSERT INTO testadd(remember) VALUES(1);");
        }
        catch (Exception e){
        	
        }
        */
		
	    // Do not leak the clientId to other subscribers
        // as we are now "sending" this message
        
        System.out.println("\n\n");
        
    }

    public void publishLock(){
    	
    }
    /* ------------------------------------------------------------ */
    public void publish(Session from, Object data, String id)
    {
    	
        ServerMessage.Mutable mutable = _bayeux.newMessage();
        mutable.setChannel(getId());
        if(from!=null)
            mutable.setClientId(from.getId());
        mutable.setData(data);
        mutable.setId(id);
        publish(from,mutable);
    }

    /* This function removes all the subscribers  */
    protected void doSweep()
    {
    	for (ServerSession session : _subscribers)
        {
            if (!session.isHandshook())
                unsubscribe((ServerSessionImpl)session);
        }

        if (isPersistent())
            return;

        if (_subscribers.size() > 0 || _listeners.size() > 0)
            return;

        if (isWild() || isDeepWild())
        {
            // Wild, check if has authorizers that can match other channels
            if (_authorizers.size() > 0)
                return;
        }
        else
        {
            // Not wild, then check if it has children
            for (ServerChannel channel : _bayeux.getChannels())
                if (_id.isParentOf(channel.getChannelId()))
                    return;
        }

        if (++_sweeperPasses < 3)
            return;

        remove();
    }

    /**
     * The method is called whenever there is no user using that channel, that is, when all the users leave.
     * For example.  Suppose that we have three users.  Two of them in section A, and one in section B.
     * If the two users of section A leave, the following will happen:
     * remove /chat/sectionA
     * remove /whiteboard/sectionA
     * 
     * If now the user of section B leave, the following will happen:
     * remove /chat/SectionB
     * remove /chat
     * remove /whiteboard/SectionB
     * remove /whiteboard
     */
    /* ------------------------------------------------------------ */
    public void remove()
    {
    	for (ServerChannelImpl child : _bayeux.getChannelChildren(_id))
            child.remove();

        if (_bayeux.removeServerChannel(this))
        {
            for (ServerSession subscriber: _subscribers)
                ((ServerSessionImpl)subscriber).unsubscribedTo(this);
            _subscribers.clear();
        }

        _listeners.clear();
    }

    public void setAttribute(String name, Object value)
    {
        _attributes.setAttribute(name, value);
    }

    public Object getAttribute(String name)
    {
        return _attributes.getAttribute(name);
    }

    public Set<String> getAttributeNames()
    {
        return _attributes.keySet();
    }

    public Object removeAttribute(String name)
    {
        Object old = getAttribute(name);
        _attributes.removeAttribute(name);
        return old;
    }

    /* ------------------------------------------------------------ */
    protected void dump(StringBuilder b,String indent)
    {
    	b.append(toString());
        b.append(isLazy()?" lazy":"");
        b.append('\n');

        List<ServerChannelImpl> children =_bayeux.getChannelChildren(_id);
        int leaves=children.size()+_subscribers.size()+_listeners.size();
        int i=0;
        for (ServerChannelImpl child : children)
        {
            b.append(indent);
            b.append(" +-");
            child.dump(b,indent+((++i==leaves)?"   ":" | "));
        }
        for (ServerSession child : _subscribers)
        {
            b.append(indent);
            b.append(" +-");
            ((ServerSessionImpl)child).dump(b,indent+((++i==leaves)?"   ":" | "));
        }
        for (ServerChannelListener child : _listeners)
        {
            b.append(indent);
            b.append(" +-");
            b.append(child);
            b.append('\n');
        }
    }

    /* ------------------------------------------------------------ */
    public void addAuthorizer(Authorizer authorizer)
    {
    	_authorizers.add(authorizer);
    }

    /* ------------------------------------------------------------ */
    public void removeAuthorizer(Authorizer authorizer)
    {
    	System.out.println("ServerChannelImpl : removeAuthorizer");
        _authorizers.remove(authorizer);
    }

    /**
     * Added by Jadiel:
     * This method is called everytime somebody wants to publish something, immediately before
     * calling the method publish.
     */
    /* ------------------------------------------------------------ */
    /**
     * Added by Jadiel:
     * This method is called everytime somebody wants to publish something, immediately before
     * calling the method publish.
     */
    public List<Authorizer> getAuthorizers()
    {
    	return Collections.unmodifiableList(_authorizers);
    }

    /* ------------------------------------------------------------ */
    @Override
    public String toString()
    {
        return _id.toString();
    }
    
     /**
     * Creates a connection to the local mysql database
     */
    private Connection getConnection() throws SQLException
    {
        com.mysql.jdbc.jdbc2.optional.MysqlDataSource ds =
            new com.mysql.jdbc.jdbc2.optional.MysqlDataSource();
        ds.setUser("andyscomet");
        ds.setPassword("");
        ds.setDatabaseName("andyscomet");
        ds.setServerName("localhost");
        ds.setPort(3306); //Most commonly 3306
        ds.setNoAccessToProcedureBodies(true);
        return ds.getConnection();
    }
}

