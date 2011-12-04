#!/bin/sh

# stop apache2
# echo Stopping apache service...
# service apache2 stop

# We MUST have a PATH environment variable
PATH=pwd+apache-maven-3.0.3/bin:$PATH
export MAVEN_OPTS="-Xmx768m -Xms64m -Djetty.port=8081"

export PATH

# cd into andes
cd cometd/trunk/andes/
echo Starting jetty...

# run jetty
mvn jetty:run

# exit 0
