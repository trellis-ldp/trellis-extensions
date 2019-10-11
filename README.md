# Trellis Linked Data Server Extensions

Trellis is a scalable platform for building [linked data](https://www.w3.org/TR/ldp/) applications.
The `trellis-extensions` projects implement additional persistence layers and service components.
For example, one extension uses a relational database to persist resources.

[![Build Status](https://travis-ci.com/trellis-ldp/trellis-extensions.svg?branch=master)](https://travis-ci.com/trellis-ldp/trellis-extensions)
[![Coverage Status](https://coveralls.io/repos/github/trellis-ldp/trellis-extensions/badge.svg?branch=master)](https://coveralls.io/github/trellis-ldp/trellis-extensions?branch=master)
![Maven Central](https://img.shields.io/maven-central/v/org.trellisldp.ext/trellis-db.svg)

## Database extension

PostgreSQL and MySQL/MariaDB database connections are supported.

A [Docker container](https://hub.docker.com/r/trellisldp/trellis-ext-db/) is available via:

```sh
docker pull trellisldp/trellis-ext-db
```

This container assumes the presence of an external database. Additional information about
[running Trellis in a Docker container](https://github.com/trellis-ldp/trellis/wiki/Dockerized-Trellis)
can be found on the TrellisLDP wiki.

Java 8+ is required to run Trellis. To build this project, use this command:

```sh
$ ./gradlew install
```

### Database setup

In order to connect Trellis to a database, please first ensure that a database server is running and accessible. Please also
ensure that the database itself has been created along with a user account with read/write access. The database schema will
be generated by Trellis via a migration command:

```sh
$ ./bin/trellis-db db migrate ./path/to/config.yml
```

The docker image will handle this migration step automatically.

For more information about Trellis, please visit either the
[main source repository](https://github.com/trellis-ldp/trellis) or the
[project website](https://www.trellisldp.org).
