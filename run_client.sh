#!/bin/bash

memory=$1
echo -J-Xmx"$memory" -J-Xms"$memory"
echo "./run_client.sh memSize serverIP:port sendMode T"
sbt -J-Xmx"$memory" -J-Xms"$memory" "run 10.227.56.44:8080 0 10.0"

#parameter for testing RESTAPI
#sbt -J-Xmx"$memory" -J-Xms"$memory" "run 192.168.1.5:9056 0 10.0"

#parameter for testing single machine throughput
#sbt -J-Xmx"$memory" -J-Xms"$memory" "run 192.168.1.5:9056 1 12.5"

#parameter for testing two-machine throughput
#sbt -J-Xmx"$memory" -J-Xms"$memory" "run 192.168.1.5:9065 0 1 12.5 50000 0 100000"
#sbt -J-Xmx"$memory" -J-Xms"$memory" "run 192.168.1.5:9065 0 1 12.5 50000 50000 100000"

#ip address and port
#192.168.1.5
#2322
#ssh -p 2322 huilingzhang@pcluster.cise.ufl.edu