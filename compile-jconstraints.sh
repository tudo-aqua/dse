#!/bin/bash
set -e
mkdir jconstraints
pushd jconstraints;
    git clone https://github.com/tudo-aqua/jconstraints.git
    cd jconstraints;
    git checkout bv-support;
    gradle publishToMavenLocal;
popd;
rm -rf jconstraints
