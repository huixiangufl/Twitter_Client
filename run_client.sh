#!/bin/bash

memory=$1
echo -J-Xmx"$memory" -J-Xms"$memory"
echo "./run_client.sh memSize serverIP:port testAPImode sendMode T"
sbt -J-Xmx"$memory" -J-Xms"$memory" "run $2 $3 $4 $5 $6 $7 $8"
