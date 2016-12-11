#!/bin/bash

export JAVA_OPTS=-Xmx15G

mkdir out
groovy -cp $GATE_HOME/bin/gate.jar:$GATE_HOME/lib/'*' convert.groovy tiger_release_aug07.corrected.16012013.xml out
