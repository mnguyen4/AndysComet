#!/bin/bash

mvn versions:use-latest-versions
mvn clean install -Dmaven.test.skip=true
sh startup.sh
