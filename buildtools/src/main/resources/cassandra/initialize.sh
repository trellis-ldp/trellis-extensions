#!/bin/bash

INIT_PATH=cassandra/src/main/resources
if [ ! -z "$1" ]
then
    INIT_PATH=$1
fi

# Wait for cassandra to be ready
count=0
while ! cqlsh -e "describe cluster;" > /dev/null 2>&1 ; do
    echo "waiting for cassandra to start"
    if [ $count -gt 30 ]
    then
        exit
    fi
    count=$((count+1))
    sleep 5
done
echo "cassandra is ready"

echo "initializing cassandra tablespace"
cqlsh -f "$INIT_PATH"/trellis.cql
