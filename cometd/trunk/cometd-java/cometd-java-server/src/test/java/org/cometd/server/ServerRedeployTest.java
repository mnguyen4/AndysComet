package org.cometd.server;

import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpStatus;

/**
 * @version $Revision: 1404 $ $Date: 2010-08-20 06:00:01 -0700 (Fri, 20 Aug 2010) $
 */
public class ServerRedeployTest extends AbstractBayeuxClientServerTest
{
    public void testServerRedeploy() throws Exception
    {
        ContentExchange handshake = newBayeuxExchange("" +
                "[{" +
                "\"channel\": \"/meta/handshake\"," +
                "\"version\": \"1.0\"," +
                "\"minimumVersion\": \"1.0\"," +
                "\"supportedConnectionTypes\": [\"long-polling\"]" +
                "}]");
        httpClient.send(handshake);
        assertEquals(HttpExchange.STATUS_COMPLETED, handshake.waitForDone());
        assertEquals(200, handshake.getResponseStatus());

        String clientId = extractClientId(handshake);
        String bayeuxCookie = extractBayeuxCookie(handshake);

        ContentExchange connect = newBayeuxExchange("" +
                "[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"," +
                "\"advice\": { \"timeout\": 0 }" +
                "}]");
        connect.setRequestHeader(HttpHeaders.COOKIE, bayeuxCookie);
        httpClient.send(connect);
        assertEquals(HttpExchange.STATUS_COMPLETED, connect.waitForDone());
        assertEquals(200, connect.getResponseStatus());

        connect = newBayeuxExchange("" +
                "[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]");
        connect.setRequestHeader(HttpHeaders.COOKIE, bayeuxCookie);
        httpClient.send(connect);

        // Wait for the connect to arrive to the server
        Thread.sleep(500);

        // Stop the context; this is the first half of a redeploy
        context.stop();

        // Expect the connect to be back with an exception
        assertEquals(HttpExchange.STATUS_COMPLETED, connect.waitForDone());
        assertEquals(HttpStatus.REQUEST_TIMEOUT_408,connect.getResponseStatus());
    }
}
