#!/bin/bash

memory=$1
echo -J-Xmx"$memory" -J-Xms"$memory"
sbt -J-Xmx"$memory" -J-Xms"$memory" "run 1.0"
