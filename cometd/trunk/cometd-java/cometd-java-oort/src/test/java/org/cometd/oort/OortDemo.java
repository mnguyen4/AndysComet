// ========================================================================
// Copyright 2007 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//========================================================================

package org.cometd.oort;


import java.lang.management.ManagementFactory;

import org.cometd.server.CometdServlet;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;



/* ------------------------------------------------------------ */
/** Main class for cometd demo.
 *
 * This is of use when running demo in a terracotta cluster
 *
 * @author gregw
 *
 */
public class OortDemo
{
    private Oort _oort;
    Server _server;

    /* ------------------------------------------------------------ */
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        int port=args.length==0?8080:Integer.valueOf(args[0]);
        OortDemo demo=new OortDemo(port);
        demo._server.join();
    }

    /* ------------------------------------------------------------ */
    public OortDemo(int port) throws Exception
    {
        String base=".";

        // Manually contruct context to avoid hassles with webapp classloaders for now.
        _server = new Server();

        // Setup JMX
        MBeanContainer mbContainer=new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        _server.getContainer().addEventListener(mbContainer);
        _server.addBean(mbContainer);
        mbContainer.addBean(Log.getLog());

        QueuedThreadPool qtp = new QueuedThreadPool();
        qtp.setMinThreads(5);
        qtp.setMaxThreads(200);
        _server.setThreadPool(qtp);

        SelectChannelConnector connector=new SelectChannelConnector();
        // SocketConnector connector=new SocketConnector();
        connector.setPort(port);
        _server.addConnector(connector);

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        _server.setHandler(contexts);

        ServletContextHandler context = new ServletContextHandler(contexts,"/",ServletContextHandler.SESSIONS);
        context.addServlet("org.eclipse.jetty.servlet.DefaultServlet", "/");

        context.setBaseResource(new ResourceCollection(new Resource[]
        {
            Resource.newResource(base+"/../../cometd-demo/src/main/webapp/"),
            Resource.newResource(base+"/../../cometd-demo/target/cometd-demo-2.1.0-SNAPSHOT/"),
        }));

        // Cometd servlet
        ServletHolder cometd_holder = new ServletHolder(CometdServlet.class);
        cometd_holder.setInitParameter("timeout","200000");
        cometd_holder.setInitParameter("interval","100");
        cometd_holder.setInitParameter("maxInterval","100000");
        cometd_holder.setInitParameter("multiFrameInterval","1500");
        cometd_holder.setInitParameter("logLevel","1");
        cometd_holder.setInitOrder(1);
        context.addServlet(cometd_holder, "/cometd/*");

        ServletHolder oort_holder = new ServletHolder(OortServlet.class);
        oort_holder.setInitParameter(OortServlet.OORT_URL_PARAM,"http://localhost:"+port+"/cometd");
        oort_holder.setInitParameter(OortServlet.OORT_CHANNELS_PARAM,"/chat/**");
        oort_holder.setInitParameter(OortServlet.OORT_CLOUD_PARAM,((port==8080)?"http://localhost:"+8081+"/cometd":"http://localhost:"+8080+"/cometd"));
        oort_holder.setInitParameter("clientLogLevel","debug");
        oort_holder.setInitOrder(2);
        context.addServlet(oort_holder, "/oort/*");

        ServletHolder seti_holder = new ServletHolder(SetiServlet.class);
        seti_holder.setInitOrder(2);
        context.addServlet(seti_holder, "/seti/*");

        ServletHolder demo_holder = new ServletHolder(OortDemoServlet.class);
        demo_holder.setInitOrder(3);
        context.getServletHandler().addServlet(demo_holder);

        context.setInitParameter("org.eclipse.jetty.server.context.ManagedAttributes","org.cometd.bayeux,org.cometd.oort.Oort");

        _server.start();

        _oort = (Oort)context.getServletContext().getAttribute(Oort.OORT_ATTRIBUTE);
        assert(_oort!=null);

    }


}
