# Trellis Linked Data Server Extensions

Trellis is a scalable platform for building [linked data](https://www.w3.org/TR/ldp/) applications.
The `trellis-extensions` projects implement additional persistence layers and service components.

[![Build Status](https://travis-ci.com/trellis-ldp/trellis-extensions.svg?branch=main)](https://travis-ci.com/trellis-ldp/trellis-extensions)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.trellisldp%3Atrellis-extensions&metric=coverage)](https://sonarcloud.io/dashboard?id=org.trellisldp%3Atrellis-extensions)
![Maven Central](https://img.shields.io/maven-central/v/org.trellisldp.ext/trellis-aws.svg)

## Cloud-based storage

A cloud-based container can be built with the
`-Pcloud` flag when running gradle.

When using the AWS/Cloud extension with a docker
container, the following environment variables need
to be set (in addition to the database-related
configuration):

```sh
AWS_ACCESS_KEY_ID=ABCDEFG
AWS_SECRET_ACCESS_KEY=HIJKLMNOPQRSTUVWXYZ
AWS_REGION=us-east-1

TRELLIS_S3_MEMENTO_BUCKET=mementos.example.com
TRELLIS_S3_BINARY_BUCKET=binaries.example.com
TRELLIS_SNS_TOPIC=arn:aws:sns:us-east-1:123456789:MyTopic
```

Java 8+ is required to run Trellis. To build this project, use this command:

```sh
$ ./gradlew install
```

For more information about Trellis, please visit either the
[main source repository](https://github.com/trellis-ldp/trellis) or the
[project website](https://www.trellisldp.org).
