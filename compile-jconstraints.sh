#!/bin/bash
set -e
mkdir jconstraints
pushd jconstraints;
    git clone https://github.com/tudo-aqua/jconstraints.git
    cd jconstraints;
    git checkout bv-support;
    ./gradlew publishToMavenLocal;
popd;
rm -rf jconstraints
