#!/bin/bash
set -e
mkdir jconstraints
pushd jconstraints;
    git clone https://github.com/tudo-aqua/jconstraints.git
    cd jconstraints;
    git checkout 5d76e10;
    ./gradlew publishToMavenLocal;
popd;
rm -rf jconstraints
