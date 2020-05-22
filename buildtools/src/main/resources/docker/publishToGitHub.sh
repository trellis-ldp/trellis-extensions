#!/bin/bash

IMAGE_BASE=docker.pkg.github.com/trellis-ldp/trellis-extensions

VERSION=$(./gradlew -q getVersion)

# Publish releases only
if [[ $VERSION != *SNAPSHOT* ]]; then
    cd platform/quarkus

    # Cassandra
    ../../gradlew clean assemble
    docker build -f src/main/docker/Dockerfile.jvm -t "$IMAGE_BASE/trellis-cassandra:$VERSION" .
    docker push "$IMAGE_BASE/trellis-cassandra:$VERSION"
fi

