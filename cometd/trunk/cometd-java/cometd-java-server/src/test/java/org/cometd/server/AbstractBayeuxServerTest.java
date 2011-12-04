package org.cometd.server;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @version $Revision: 1603 $ $Date: 2011-01-24 10:32:18 -0700 (Mon, 24 Jan 2011) $
 */
public abstract class AbstractBayeuxServerTest extends TestCase
{
    protected Server server;
    protected int port;
    protected ServletContextHandler context;
    protected String cometdURL;
    protected long timeout = 5000;

    protected void setUp() throws Exception
    {
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        server.addConnector(connector);

        HandlerCollection handlers = new HandlerCollection();
        server.setHandler(handlers);

        String contextPath = "/cometd";
        context = new ServletContextHandler(handlers, contextPath, ServletContextHandler.SESSIONS);

        // Setup comet servlet
        CometdServlet cometdServlet = new CometdServlet();
        ServletHolder cometdServletHolder = new ServletHolder(cometdServlet);
        Map<String, String> options = new HashMap<String, String>();
        options.put("timeout", String.valueOf(timeout));
        options.put("logLevel", "3");
        options.put("jsonDebug", "true");
        customizeOptions(options);
        for (Map.Entry<String, String> entry : options.entrySet())
            cometdServletHolder.setInitParameter(entry.getKey(), entry.getValue());
        String cometdServletPath = "/cometd";
        context.addServlet(cometdServletHolder, cometdServletPath + "/*");

        server.start();
        port = connector.getLocalPort();

        String contextURL = "http://localhost:" + port + contextPath;
        cometdURL = contextURL + cometdServletPath;

        BayeuxServerImpl bayeux = cometdServlet.getBayeux();
        customizeBayeux(bayeux);
    }

    protected void tearDown() throws Exception
    {
        server.stop();
        server.join();
    }

    protected void customizeOptions(Map<String, String> options)
    {
    }

    protected void customizeBayeux(BayeuxServerImpl bayeux)
    {
    }
}
