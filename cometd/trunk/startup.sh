#!/bin/sh

# exports PATH to maven /bin/ directory, builds jetty server, then runs it.



# We MUST have a PATH environment variable
PATH=pwd+apache-maven-3.0.3/bin:$PATH

# PATH = /home/joseph/apache-maven-3.0.3/bin:$PATH

export PATH

# cd into andes

cd andes/

# run jetty

mvn -Djetty.port=8081 jetty:run

# exit 0

