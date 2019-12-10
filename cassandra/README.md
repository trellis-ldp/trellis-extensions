# Trellis Cassandra Extension

The Cassandra extension implements the core Trellis services: ResourceService,
BinaryService and MementoService which can be folded into any of various deployable
frameworks.

## Tests

The Cassandra integration testing code relies on an external Cassandra cluster, but these
integration tests are disabled by default. To enable them, please set the following
environment variable:

    TEST_CASSANDRA=true

or, in `~/.gradle/gradle.properties`, add this setting:

    trellis.test.cassandra=true

The tests also rely on having the database schema already set up. The schema document can be
loaded into the cluster via:

    $ cqlsh -f cassandra/src/main/resources/trellis.cql

For testing, one may choose to run a Docker-based Cassandra cluster locally.
The container can be launched with the following command:

    docker run -p 9042:9042 -p 9160:9160 --name cassandra -d \
        -v "$(pwd)"/cassandra/src/main/resources:/app \
        -v "$(pwd)"/buildtools/src/main/resources/cassandra:/init cassandra:latest

Once launched, the Cassandra system can be initialized with:

    docker exec -it cassandra sh -c "/init/initialize.sh /app"

