This is the CometD project home.

bayeux            - The Bayeux Specification
cometd-archetypes - The Maven2 archetypes for quick application development
cometd-demo       - Demo of the Java server and of the Dojo/jQuery clients
cometd-java       - The Java server and java client libraries
cometd-javascript - The JavaScript (Dojo/jQuery) client libraries
cometd-perl       - The Perl server (ALPHA)
cometd-twisted    - The Python server (ALPHA)


RUNNING COMETD DEMO

If you have downloaded the release bundle, then the CometD is already built for you.
To run the demonstration with Maven:

    $ cd cometd-demo
    $ mvn jetty:run

then point your browser at http://localhost:8080

Alternately you can deploy the CometD demo war into
any servlet 2.5 or 3.0 server (eg jetty, tomcat, glassfish)
with commands like

    $ cp cometd-demo/target/cometd-demo/target/cometd-demo-*.war  $MY_SERVER/webapps/cometd.war
    cd $MY_SERVER
    <command to run my server>


BUILDING COMETD

If you have checked out the source release, follow the instructions at
http://cometd.org/documentation/building, or just to

  $ mvn install



LOAD TESTING


To run load testing, open two windows. In the server window run:

  $ cd cometd-java/cometd-java-client
  $ mvn -Pserver exec:exec

In the client window (potentially on another machine), run

  $ cd cometd-java/cometd-java-client
  $ mvn -Pclient exec:java

Then answer the questions (defaults gives a reasonable result)
to generate load. Keep running batches until the reported JIT time
is zero before considering the actual results obtained.




