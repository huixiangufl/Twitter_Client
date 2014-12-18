#!/bin/bash

memory=$1
echo -J-Xmx"$memory" -J-Xms"$memory"
echo "./run_client.sh memSize serverIP:port sendMode T"
sbt -J-Xmx"$memory" -J-Xms"$memory" "run 10.227.56.44:8080 0 0 10.0"
