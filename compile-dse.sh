#!/bin/bash
echo "Going to include java.base.jmod from this jdk: ${JAVA_HOME}"

mkdir -p src/main/resources/jmods/
cp $JAVA_HOME/jmods/java.base.jmod src/main/resources/jmods/
mvn package -DskipTests
rm -r src/main/resources/jmods
