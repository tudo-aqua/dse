#!/usr/bin/env bash

find src/test/resources/examples/ -name "ConstructurBluePrint.txt" -o \
     -name "ConstructurDeclarations.txt" -o \
     -name "ConstructurObjects.txt" | xargs rm