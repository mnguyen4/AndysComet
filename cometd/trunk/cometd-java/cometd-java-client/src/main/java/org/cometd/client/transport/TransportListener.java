package org.cometd.client.transport;

import java.util.List;

import org.cometd.bayeux.Message;

/**
 * @version $Revision: 902 $ $Date: 2010-10-01 13:45:07 -0700 (Fri, 01 Oct 2010) $
 */
public interface TransportListener
{
    void onSending(Message[] messages);

    void onMessages(List<Message.Mutable> messages);

    void onConnectException(Throwable x, Message[] messages);

    void onException(Throwable x, Message[] messages);

    void onExpire(Message[] messages);

    void onProtocolError(String info, Message[] messages);
}
