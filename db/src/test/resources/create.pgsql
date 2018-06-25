CREATE TABLE IF NOT EXISTS metadata (
    id VARCHAR(1024) NOT NULL PRIMARY KEY,
    interaction_model VARCHAR(255) NOT NULL,
    modified BIGINT NOT NULL,
    is_part_of VARCHAR(1024),
    deleted BOOLEAN default FALSE,
    acl BOOLEAN default FALSE
);

CREATE TABLE IF NOT EXISTS resource (
    id VARCHAR(1024) NOT NULL,
    subject VARCHAR(1024) NOT NULL,
    predicate VARCHAR(1024) NOT NULL,
    object TEXT NOT NULL,
    lang VARCHAR(10),
    datatype VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS resource_idx ON resource (id);

CREATE TABLE IF NOT EXISTS log (
    id VARCHAR(1024) NOT NULL,
    subject VARCHAR(1024) NOT NULL,
    predicate VARCHAR(1024) NOT NULL,
    object TEXT NOT NULL,
    lang VARCHAR(10),
    datatype VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS log_idx ON log (id);

CREATE TABLE IF NOT EXISTS acl (
    id VARCHAR(1024) NOT NULL,
    subject VARCHAR(1024) NOT NULL,
    predicate VARCHAR(1024) NOT NULL,
    object TEXT NOT NULL,
    lang VARCHAR(10),
    datatype VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS acl_idx ON acl (id);

CREATE TABLE IF NOT EXISTS ldp (
    id VARCHAR(1024) NOT NULL PRIMARY KEY,
    member VARCHAR(1024) NOT NULL,
    membership_resource VARCHAR(1024) NOT NULL,
    has_member_relation VARCHAR(255),
    is_member_of_relation VARCHAR(255),
    inserted_content_relation VARCHAR(255) default 'http://www.w3.org/ns/ldp#MemberSubject'
);

CREATE INDEX IF NOT EXISTS ldp_idx ON ldp (member);

CREATE TABLE IF NOT EXISTS extra (
    subject VARCHAR(1024) NOT NULL,
    predicate VARCHAR(1024) NOT NULL,
    object VARCHAR(1024) NOT NULL
);

CREATE INDEX IF NOT EXISTS extra_idx ON extra (subject);

CREATE TABLE IF NOT EXISTS nonrdf (
    id VARCHAR(1024) NOT NULL PRIMARY KEY,
    location VARCHAR(1024) NOT NULL,
    modified BIGINT NOT NULL,
    format VARCHAR(255),
    size BIGINT
);

INSERT INTO metadata (id, interaction_model, modified, acl)
    VALUES ('trellis:data/', 'http://www.w3.org/ns/ldp#BasicContainer', 0, TRUE);

INSERT INTO acl (id, subject, predicate, object)
    VALUES ('trellis:data/', 'trellis:data/#auth', 'http://www.w3.org/ns/auth/acl#mode', 'http://www.w3.org/ns/auth/acl#Read'),
        ('trellis:data/', 'trellis:data/#auth', 'http://www.w3.org/ns/auth/acl#mode', 'http://www.w3.org/ns/auth/acl#Write'),
        ('trellis:data/', 'trellis:data/#auth', 'http://www.w3.org/ns/auth/acl#mode', 'http://www.w3.org/ns/auth/acl#Control'),
        ('trellis:data/', 'trellis:data/#auth', 'http://www.w3.org/ns/auth/acl#agentClass', 'http://xmlns.com/foaf/0.1/Agent'),
        ('trellis:data/', 'trellis:data/#auth', 'http://www.w3.org/ns/auth/acl#accessTo', 'trellis:data/');

