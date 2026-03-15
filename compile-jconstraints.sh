#!/bin/bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home
set -e
mkdir jconstraints
pushd jconstraints;
    git clone https://github.com/tudo-aqua/jconstraints.git
    cd jconstraints;
    git checkout function-parsing-support;
    ./gradlew publishToMavenLocal;
popd;
rm -rf jconstraints
