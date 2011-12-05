#!/bin/sh

# Easy setup for Andes on Ubuntu
# Be sure to run this at the location you wish the server to be installed

# Getting all the required stuff
apt-get install git make mvn

cd /cometd/trunk/

# Compile the code 

mvn versions:use-latest-versions
mvn clean install

mvn --v
echo Run sh startup.sh to start the server.

