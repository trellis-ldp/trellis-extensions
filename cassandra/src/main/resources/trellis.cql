
CREATE KEYSPACE IF NOT EXISTS trellis WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

USE trellis;

-- Main data tables

CREATE TABLE IF NOT EXISTS mutabledata (identifier text, quads text, interactionModel text,
    hasAcl boolean, binaryIdentifier text, mimeType text, container text, modified timestamp,
    created timeuuid,
    PRIMARY KEY (identifier));

CREATE TABLE IF NOT EXISTS mementodata (identifier text, quads text, interactionModel text,
    hasAcl boolean, binaryIdentifier text, mimeType text, container text, created timeuuid,
    modified timestamp, mementomodified timestamp,
    PRIMARY KEY ((identifier), created))
    WITH CLUSTERING ORDER BY (created DESC);

CREATE TABLE IF NOT EXISTS immutabledata (identifier text, quads text, created timestamp,
    PRIMARY KEY ((identifier), created)) WITH CLUSTERING ORDER BY (created DESC);

CREATE TABLE IF NOT EXISTS binarydata (identifier text, size bigint, chunkSize int, chunkIndex int,
    chunk blob,
    PRIMARY KEY (identifier, chunkIndex)) WITH CLUSTERING ORDER BY (chunkIndex ASC);

-- Index for basic containment

CREATE MATERIALIZED VIEW IF NOT EXISTS basiccontainment AS
       SELECT identifier, interactionModel, container FROM mutabledata
       WHERE identifier IS NOT NULL AND container IS NOT NULL
       PRIMARY KEY ((container), identifier);

