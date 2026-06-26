#!/usr/bin/env bash

find src/test/resources/generated-examples-ICSE2027a-v2 -name "ConstructurBluePrint.txt" -o \
     -name "ConstructurDeclarations.txt" -o \
     -name "ConstructurObjects.txt" | xargs rm