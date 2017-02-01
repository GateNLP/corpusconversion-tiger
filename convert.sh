#!/bin/bash

export JAVA_OPTS=-Xmx3500M

groovy -cp $GATE_HOME/bin/gate.jar:$GATE_HOME/lib/'*' convert.groovy "$@"
