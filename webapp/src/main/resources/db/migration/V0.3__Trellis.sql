SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;
SET default_tablespace = '';
SET default_with_oids = false;

--
-- Name: acl; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.acl (
    resource_id bigint NOT NULL,
    subject character varying(1024) NOT NULL,
    predicate character varying(1024) NOT NULL,
    object character varying(16383) NOT NULL,
    lang character varying(20),
    datatype character varying(255)
);


--
-- Name: TABLE acl; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.acl IS 'This table stores the WebACL triples for each relevant resource.';


--
-- Name: COLUMN acl.resource_id; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.acl.resource_id IS 'This value points to the relevant item in the resource table.';


--
-- Name: COLUMN acl.subject; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.acl.subject IS 'The RDF subject for the triple.';


--
-- Name: COLUMN acl.predicate; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.acl.predicate IS 'The RDF predicate for the triple.';


--
-- Name: COLUMN acl.object; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.acl.object IS 'The RDF object for the triple.';


--
-- Name: COLUMN acl.lang; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.acl.lang IS 'If the object is a string literal, this holds the language tag, if relevant.';


--
-- Name: COLUMN acl.datatype; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.acl.datatype IS 'If the object is a literal, this holds the datatype IRI of that literal value.';


--
-- Name: description; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.description (
    resource_id bigint NOT NULL,
    subject character varying(1024) NOT NULL,
    predicate character varying(1024) NOT NULL,
    object character varying(16383) NOT NULL,
    lang character varying(20),
    datatype character varying(255)
);


--
-- Name: TABLE description; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.description IS 'This table stores all of the user-managed RDF triples on a resource.';


--
-- Name: COLUMN description.resource_id; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.description.resource_id IS 'This value points to the relevant item in the resource table.';


--
-- Name: COLUMN description.subject; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.description.subject IS 'The RDF subject for the triple.';


--
-- Name: COLUMN description.predicate; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.description.predicate IS 'The RDF predicate for the triple.';


--
-- Name: COLUMN description.object; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.description.object IS 'The RDF object for the triple.';


--
-- Name: COLUMN description.lang; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.description.lang IS 'If the object is a string literal, this holds the language tag, if relevant.';


--
-- Name: COLUMN description.datatype; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.description.datatype IS 'If the object is a literal, this holds the datatype IRI of that literal value.';


--
-- Name: extra; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.extra (
    resource_id bigint NOT NULL,
    predicate character varying(1024) NOT NULL,
    object character varying(1024) NOT NULL
);


--
-- Name: TABLE extra; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.extra IS 'This table stores copies of certain user-managed triples for use in response headers.';


--
-- Name: COLUMN extra.resource_id; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.extra.resource_id IS 'This value points to the relevant item in the resource table.';


--
-- Name: COLUMN extra.predicate; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.extra.predicate IS 'The RDF predicate, which becomes the rel value in a Link header.';


--
-- Name: COLUMN extra.object; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.extra.object IS 'The RDF object, which becomes the URI value in a Link header.';


--
-- Name: log; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.log (
    id character varying(1024) NOT NULL,
    subject character varying(1024) NOT NULL,
    predicate character varying(1024) NOT NULL,
    object character varying(16383) NOT NULL,
    lang character varying(20),
    datatype character varying(255)
);


--
-- Name: TABLE log; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.log IS 'This table stores the complete audit log for each resource.';


--
-- Name: COLUMN log.id; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.log.id IS 'The id column uses the internal IRI for the resource (resource.subject) since the resource.id value changes across updates.';


--
-- Name: COLUMN log.subject; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.log.subject IS 'The RDF subject for the triple.';


--
-- Name: COLUMN log.predicate; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.log.predicate IS 'The RDF predicate for the triple.';


--
-- Name: COLUMN log.object; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.log.object IS 'The RDF object for the triple.';


--
-- Name: COLUMN log.lang; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.log.lang IS 'If the object is a string literal, this holds the language tag, if relevant.';


--
-- Name: COLUMN log.datatype; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.log.datatype IS 'If the object is a literal, this holds the datatype IRI of that literal value.';


--
-- Name: memento; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.memento (
    id bigint NOT NULL,
    subject character varying(1024) NOT NULL,
    moment bigint NOT NULL
);


--
-- Name: TABLE memento; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.memento IS 'This table keeps a record of memento locations';


--
-- Name: COLUMN memento.id; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.memento.id IS 'A unique numerical ID for each memento.';


--
-- Name: COLUMN memento.subject; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.memento.subject IS 'The internal IRI for each resource.';


--
-- Name: COLUMN memento.moment; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.memento.moment IS 'The time of each memento, stored as a long representation of epoch-second.';


--
-- Name: memento_id_seq; Type: SEQUENCE; Schema: public; Owner: trellis
--

CREATE SEQUENCE public.memento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: memento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trellis
--

ALTER SEQUENCE public.memento_id_seq OWNED BY public.memento.id;


--
-- Name: namespaces; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.namespaces (
    prefix character varying(255) NOT NULL,
    namespace character varying(1024) NOT NULL
);


--
-- Name: TABLE namespaces; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.namespaces IS 'This table keeps track of namespace prefixes.';


--
-- Name: COLUMN namespaces.prefix; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.namespaces.prefix IS 'A unique prefix.';


--
-- Name: COLUMN namespaces.namespace; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.namespaces.namespace IS 'The namespace IRI.';


--
-- Name: resource; Type: TABLE; Schema: public; Owner: trellis
--

CREATE TABLE public.resource (
    id bigint NOT NULL,
    subject character varying(1024) NOT NULL,
    interaction_model character varying(255) NOT NULL,
    modified bigint NOT NULL,
    is_part_of character varying(1024),
    deleted boolean DEFAULT false,
    acl boolean DEFAULT false,
    binary_location character varying(1024),
    binary_modified bigint,
    binary_format character varying(255),
    binary_size bigint,
    ldp_member character varying(1024),
    ldp_membership_resource character varying(1024),
    ldp_has_member_relation character varying(1024),
    ldp_is_member_of_relation character varying(1024),
    ldp_inserted_content_relation character varying(1024)
);


--
-- Name: TABLE resource; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON TABLE public.resource IS 'This table keeps track of every resource, along with any server-managed properties for the resource.';


--
-- Name: COLUMN resource.id; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.id IS 'A unique numerical ID for each resource.';


--
-- Name: COLUMN resource.subject; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.subject IS 'The internal IRI for each resource.';


--
-- Name: COLUMN resource.interaction_model; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.interaction_model IS 'The LDP type of each resource.';


--
-- Name: COLUMN resource.modified; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.modified IS 'The modification date for each resource, stored as a long representation of epoch-milliseconds.';


--
-- Name: COLUMN resource.is_part_of; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.is_part_of IS 'The parent resource IRI, if one exists.';


--
-- Name: COLUMN resource.deleted; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.deleted IS 'Whether this resource has been deleted (HTTP 410).';


--
-- Name: COLUMN resource.acl; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.acl IS 'Whether this resource has an ACL resource.';


--
-- Name: COLUMN resource.binary_location; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.binary_location IS 'If this resource is a LDP-NR, this column holds the location of the binary resource.';


--
-- Name: COLUMN resource.binary_modified; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.binary_modified IS 'If this resource is a LDP-NR, this column holds the modification date of the binary resource, stored as a long representation of epoch-milliseconds.';


--
-- Name: COLUMN resource.binary_format; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.binary_format IS 'If this resource is a LDP-NR, this column holds the MIMEtype of the resource, if known.';


--
-- Name: COLUMN resource.binary_size; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.binary_size IS 'If this resource is a LDP-NR, this column holds the size of the binary resource, if known.';


--
-- Name: COLUMN resource.ldp_member; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.ldp_member IS 'If this is a LDP-DC or LDP-IC, this column holds the value of ldp:membershipResource but with any fragment IRI removed.';


--
-- Name: COLUMN resource.ldp_membership_resource; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.ldp_membership_resource IS 'If this resource is a LDP-DC or LDP-IC, this column holds the ldp:membershipResource value.';


--
-- Name: COLUMN resource.ldp_has_member_relation; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.ldp_has_member_relation IS 'If this resource is a LDP-DC or LDP-IC, this column holds the ldp:hasMemberRelation value, if present.';


--
-- Name: COLUMN resource.ldp_is_member_of_relation; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.ldp_is_member_of_relation IS 'If this resource is a LDP-DC or LDP-IC, this column holds the ldp:isMemberOfRelation value, if present.';


--
-- Name: COLUMN resource.ldp_inserted_content_relation; Type: COMMENT; Schema: public; Owner: trellis
--

COMMENT ON COLUMN public.resource.ldp_inserted_content_relation IS 'If this resource is a LDP-DC or LDP-IC, this column holds the ldp:insertedContentRelation value.';


--
-- Name: resource_id_seq; Type: SEQUENCE; Schema: public; Owner: trellis
--

CREATE SEQUENCE public.resource_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: resource_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trellis
--

ALTER SEQUENCE public.resource_id_seq OWNED BY public.resource.id;


--
-- Name: memento id; Type: DEFAULT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.memento ALTER COLUMN id SET DEFAULT nextval('public.memento_id_seq'::regclass);


--
-- Name: resource id; Type: DEFAULT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.resource ALTER COLUMN id SET DEFAULT nextval('public.resource_id_seq'::regclass);


--
-- Data for Name: acl; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.acl (resource_id, subject, predicate, object, lang, datatype) FROM stdin;
0	trellis:data/#auth	http://www.w3.org/ns/auth/acl#mode	http://www.w3.org/ns/auth/acl#Read	\N	\N
0	trellis:data/#auth	http://www.w3.org/ns/auth/acl#mode	http://www.w3.org/ns/auth/acl#Write	\N	\N
0	trellis:data/#auth	http://www.w3.org/ns/auth/acl#mode	http://www.w3.org/ns/auth/acl#Control	\N	\N
0	trellis:data/#auth	http://www.w3.org/ns/auth/acl#agentClass	http://xmlns.com/foaf/0.1/Agent	\N	\N
0	trellis:data/#auth	http://www.w3.org/ns/auth/acl#accessTo	trellis:data/	\N	\N
\.


--
-- Data for Name: description; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.description (resource_id, subject, predicate, object, lang, datatype) FROM stdin;
\.


--
-- Data for Name: extra; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.extra (resource_id, predicate, object) FROM stdin;
\.


--
-- Data for Name: log; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.log (id, subject, predicate, object, lang, datatype) FROM stdin;
\.


--
-- Data for Name: memento; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.memento (id, subject, moment) FROM stdin;
\.


--
-- Data for Name: namespaces; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.namespaces (prefix, namespace) FROM stdin;
acl	http://www.w3.org/ns/auth/acl#
as	https://www.w3.org/ns/activitystreams#
dc	http://purl.org/dc/terms/
dc11	http://purl.org/dc/elements/1.1/
geo	http://www.w3.org/2003/01/geo/wgs84_pos#
ldp	http://www.w3.org/ns/ldp#
memento	http://mementoweb.org/ns#
owl	http://www.w3.org/2002/07/owl#
prov	http://www.w3.org/ns/prov#
rdf	http://www.w3.org/1999/02/22-rdf-syntax-ns#
rdfs	http://www.w3.org/2000/01/rdf-schema#
schema	http://schema.org/
skos	http://www.w3.org/2004/02/skos/core#
time	http://www.w3.org/2006/time#
xsd	http://www.w3.org/2001/XMLSchema#
\.


--
-- Data for Name: resource; Type: TABLE DATA; Schema: public; Owner: trellis
--

COPY public.resource (id, subject, interaction_model, modified, is_part_of, deleted, acl, binary_location, binary_modified, binary_format, binary_size, ldp_member, ldp_membership_resource, ldp_has_member_relation, ldp_is_member_of_relation, ldp_inserted_content_relation) FROM stdin;
0	trellis:data/	http://www.w3.org/ns/ldp#BasicContainer	0	\N	f	t	\N	\N	\N	\N	\N	\N	\N	\N	\N
\.


--
-- Name: memento_id_seq; Type: SEQUENCE SET; Schema: public; Owner: trellis
--

SELECT pg_catalog.setval('public.memento_id_seq', 1, false);


--
-- Name: resource_id_seq; Type: SEQUENCE SET; Schema: public; Owner: trellis
--

SELECT pg_catalog.setval('public.resource_id_seq', 1, false);


--
-- Name: memento memento_pkey; Type: CONSTRAINT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.memento
    ADD CONSTRAINT memento_pkey PRIMARY KEY (id);


--
-- Name: namespaces namespaces_pkey; Type: CONSTRAINT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.namespaces
    ADD CONSTRAINT namespaces_pkey PRIMARY KEY (prefix);


--
-- Name: resource resource_pkey; Type: CONSTRAINT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.resource
    ADD CONSTRAINT resource_pkey PRIMARY KEY (id);


--
-- Name: idx_acl; Type: INDEX; Schema: public; Owner: trellis
--

CREATE INDEX idx_acl ON public.acl USING btree (resource_id);


--
-- Name: idx_description; Type: INDEX; Schema: public; Owner: trellis
--

CREATE INDEX idx_description ON public.description USING btree (resource_id);


--
-- Name: idx_extra; Type: INDEX; Schema: public; Owner: trellis
--

CREATE INDEX idx_extra ON public.extra USING btree (resource_id);


--
-- Name: idx_log; Type: INDEX; Schema: public; Owner: trellis
--

CREATE INDEX idx_log ON public.log USING btree (id);


--
-- Name: idx_memento; Type: INDEX; Schema: public; Owner: trellis
--

CREATE UNIQUE INDEX idx_memento ON public.memento USING btree (subject, moment);


--
-- Name: idx_resource_ldp; Type: INDEX; Schema: public; Owner: trellis
--

CREATE INDEX idx_resource_ldp ON public.resource USING btree (ldp_member);


--
-- Name: idx_resource_parent; Type: INDEX; Schema: public; Owner: trellis
--

CREATE INDEX idx_resource_parent ON public.resource USING btree (is_part_of);


--
-- Name: idx_resource_subject; Type: INDEX; Schema: public; Owner: trellis
--

CREATE UNIQUE INDEX idx_resource_subject ON public.resource USING btree (subject);


--
-- Name: acl fk_resource_acl; Type: FK CONSTRAINT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.acl
    ADD CONSTRAINT fk_resource_acl FOREIGN KEY (resource_id) REFERENCES public.resource(id) ON UPDATE RESTRICT ON DELETE CASCADE;


--
-- Name: description fk_resource_description; Type: FK CONSTRAINT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.description
    ADD CONSTRAINT fk_resource_description FOREIGN KEY (resource_id) REFERENCES public.resource(id) ON UPDATE RESTRICT ON DELETE CASCADE;


--
-- Name: extra fk_resource_extra; Type: FK CONSTRAINT; Schema: public; Owner: trellis
--

ALTER TABLE ONLY public.extra
    ADD CONSTRAINT fk_resource_extra FOREIGN KEY (resource_id) REFERENCES public.resource(id) ON UPDATE RESTRICT ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

