/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.ext.db;

import static java.time.Instant.ofEpochMilli;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.builder;
import static java.util.stream.Stream.concat;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;

import java.time.Instant;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.api.Triple;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.vocabulary.XSD;

/**
 * A db-based implementation of the Trellis Resource API.
 */
public class DBResource implements Resource {

    private static final Logger LOGGER = getLogger(DBResource.class);
    private static final RDF rdf = getInstance();

    private static final String OBJECT = "object";
    private static final String LANG = "lang";
    private static final String DATATYPE = "datatype";
    private static final String SUBJECT = "subject";
    private static final String PREDICATE = "predicate";
    private static final String MEMBERSHIP_RESOURCE = "membershipResource";
    private static final String HAS_MEMBER_RELATION = "hasMemberRelation";
    private static final String IS_MEMBER_OF_RELATION = "isMemberOfRelation";
    private static final String INSERTED_CONTENT_RELATION = "insertedContentRelation";

    private final IRI identifier;
    private final Jdbi jdbi;
    private final Map<IRI, Supplier<Stream<Quad>>> graphMapper = new HashMap<>();

    // Resource data fields.
    private ResourceData data = new ResourceData();

    /**
     * Create a DB-based Resource.
     * @param ds the datasource
     * @param identifier the identifier
     */
    public DBResource(final DataSource ds, final IRI identifier) {
        this(Jdbi.create(ds), identifier);
    }

    /**
     * Create a DB-based Resource.
     * @param jdbi the jdbi object
     * @param identifier the identifier
     */
    public DBResource(final Jdbi jdbi, final IRI identifier) {
        this.identifier = identifier;
        this.jdbi = jdbi;
        graphMapper.put(Trellis.PreferUserManaged, this::fetchUserQuads);
        graphMapper.put(Trellis.PreferServerManaged, this::fetchServerQuads);
        graphMapper.put(Trellis.PreferAudit, this::fetchAuditQuads);
        graphMapper.put(Trellis.PreferAccessControl, this::fetchAclQuads);
        graphMapper.put(LDP.PreferContainment, this::fetchContainmentQuads);
        graphMapper.put(LDP.PreferMembership, this::fetchMembershipQuads);
    }

    /**
     * Try to load a Trellis resource.
     * @param ds the datasource
     * @param identifier the identifier
     * @return a Resource, if one exists
     */
    public static Optional<Resource> findResource(final DataSource ds, final IRI identifier) {
        return findResource(Jdbi.create(ds), identifier);
    }

    /**
     * Try to load a Trellis resource.
     * @param jdbi the Jdbi object
     * @param identifier the identifier
     * @return a Resource, if one exists
     */
    public static Optional<Resource> findResource(final Jdbi jdbi, final IRI identifier) {
        final DBResource res = new DBResource(jdbi, identifier);
        res.fetchData();
        return res.exists() ? of(res) : empty();
    }

    /**
     * Test whether this resource exists.
     * @return true if this resource exists; false otherwise
     */
    protected Boolean exists() {
        return nonNull(getModified()) && nonNull(getInteractionModel());
    }

    @Override
    public Stream<Quad> stream() {
        return graphMapper.values().stream().flatMap(Supplier::get);
    }

    @Override
    public Stream<Triple> stream(final Collection<IRI> graphNames) {
        return graphNames.stream().filter(graphMapper::containsKey).map(graphMapper::get).flatMap(Supplier::get)
            .map(Quad::asTriple);
    }

    @Override
    public IRI getIdentifier() {
        return identifier;
    }

    @Override
    public IRI getInteractionModel() {
        return ofNullable(data.interactionModel).map(rdf::createIRI).orElse(null);
    }

    @Override
    public Optional<IRI> getMembershipResource() {
        return ofNullable(data.membershipResource).map(rdf::createIRI);
    }

    @Override
    public Optional<IRI> getMemberRelation() {
        return ofNullable(data.hasMemberRelation).map(rdf::createIRI);
    }

    @Override
    public Optional<IRI> getMemberOfRelation() {
        return ofNullable(data.isMemberOfRelation).map(rdf::createIRI);
    }

    @Override
    public Optional<IRI> getInsertedContentRelation() {
        return ofNullable(data.insertedContentRelation).map(rdf::createIRI);
    }

    @Override
    public Optional<Binary> getBinary() {
        return ofNullable(data.binary);
    }

    @Override
    public Instant getModified() {
        return ofNullable(data.modified).map(Instant::ofEpochMilli).orElse(null);
    }

    @Override
    public Boolean hasAcl() {
        return data.hasAcl;
    }

    @Override
    public Boolean isDeleted() {
        return data.isDeleted;
    }

    @Override
    public Stream<Map.Entry<String,String>> getExtraLinkRelations() {
        return data.extra.entrySet().stream();
    }

    private Stream<Quad> fetchMembershipQuads() {
        return concat(fetchIndirectMemberQuads(),
                concat(fetchDirectMemberQuads(), fetchDirectMemberQuadsInverse()));
    }

    private Stream<Quad> fetchServerQuads() {
        final Stream.Builder<Quad> builder = builder();
        builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), type, getInteractionModel()));
        ofNullable(getModified()).map(m -> rdf.createLiteral(m.toString(), XSD.dateTime)).ifPresent(modified ->
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), DC.modified, modified)));
        ofNullable(data.isPartOf).map(rdf::createIRI).ifPresent(parent ->
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), DC.isPartOf, parent)));
        return builder.build();
    }

    private Stream<Quad> fetchUserQuads() {
        return fetchQuadsFromTable("resource", Trellis.PreferUserManaged);
    }

    private Stream<Quad> fetchAuditQuads() {
        return fetchQuadsFromTable("log", Trellis.PreferAudit);
    }

    private Stream<Quad> fetchAclQuads() {
        return fetchQuadsFromTable("acl", Trellis.PreferAccessControl);
    }

    private Stream<Quad> fetchIndirectMemberQuads() {
        final String query
            = "SELECT l.membershipResource, l.hasMemberRelation, r.object, r.lang, r.datatype "
            + "FROM ldp AS l INNER JOIN metadata AS m ON l.id = m.isPartOf "
            + "INNER JOIN resource AS r ON m.id = r.id AND r.predicate = l.insertedContentRelation "
            + "WHERE l.member = ? AND m.interactionModel = ?";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.IndirectContainer.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership,
                            rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE)),
                            rdf.createIRI(rs.getString(HAS_MEMBER_RELATION)),
                            getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))))
                .stream());
    }

    private Stream<Quad> fetchDirectMemberQuadsInverse() {
        final String query
            = "SELECT l.isMemberOfRelation, l.membershipResource "
            + "FROM ldp AS l INNER JOIN metadata AS m ON l.id = m.isPartOf "
            + "WHERE m.id = ? AND l.insertedContentRelation = ?";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.MemberSubject.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership, getIdentifier(),
                        rdf.createIRI(rs.getString(IS_MEMBER_OF_RELATION)),
                        rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE))))
                .stream());
    }

    private Stream<Quad> fetchDirectMemberQuads() {
        final String query
            = "SELECT l.membershipResource, l.hasMemberRelation, m.id "
            + "FROM ldp AS l INNER JOIN metadata AS m ON l.id = m.isPartOf "
            + "WHERE l.member = ? AND l.insertedContentRelation = ?";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.MemberSubject.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership,
                        rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE)),
                        rdf.createIRI(rs.getString(HAS_MEMBER_RELATION)),
                        rdf.createIRI(rs.getString("id"))))
                .stream());
    }

    private Stream<Quad> fetchContainmentQuads() {
        if (getInteractionModel().getIRIString().endsWith("Container")) {
            final String query = "SELECT id FROM metadata where isPartOf = ?";
            return jdbi.withHandle(handle -> handle.select(query,
                        getIdentifier().getIRIString())
                    .map((rs, ctx) -> rdf.createQuad(LDP.PreferContainment, getIdentifier(),
                            LDP.contains, rdf.createIRI(rs.getString("id"))))
                    .stream());
        }
        return Stream.empty();
    }

    private Stream<Quad> fetchQuadsFromTable(final String tableName, final IRI graphName) {
        final String query = "SELECT subject, predicate, object, lang, datatype "
                           + "FROM " + tableName + " WHERE id = ?";
        return jdbi.withHandle(handle -> handle.select(query, getIdentifier().getIRIString())
                .map((rs, ctx) -> rdf.createQuad(graphName, rdf.createIRI(rs.getString(SUBJECT)),
                        rdf.createIRI(rs.getString(PREDICATE)),
                        getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))))
                .stream());
    }

    /**
     * Fetch data for this resource.
     */
    protected void fetchData() {
        LOGGER.debug("Fetching data for: {}", identifier);
        final String extraQuery = "SELECT predicate, object FROM extra WHERE subject = ?";
        final Map<String, String> extras = new HashMap<>();
        jdbi.useHandle(handle ->
                handle.select(extraQuery, identifier.getIRIString())
                      .map((rs, ctx) -> new SimpleImmutableEntry<>(rs.getString(PREDICATE), rs.getString(OBJECT)))
                      .forEach(entry -> extras.put(entry.getKey(), entry.getValue())));

        final String query
            = "SELECT m.interactionModel, m.modified, m.isPartOf, m.isDeleted, m.hasAcl, "
            + "l.membershipResource, l.hasMemberRelation, l.isMemberOfRelation, l.insertedContentRelation, "
            + "nr.location, nr.modified AS binaryModified, nr.format, nr.size "
            + "FROM metadata AS m "
            + "LEFT JOIN ldp AS l ON m.id = l.id "
            + "LEFT JOIN nonrdf AS nr ON m.id = nr.id "
            + "WHERE m.id = ?";
        jdbi.withHandle(handle -> handle.select(query, identifier.getIRIString())
                .map((rs, ctx) -> {
                    final ResourceData d = new ResourceData();
                    d.interactionModel = rs.getString("interactionModel");
                    d.modified = rs.getLong("modified");
                    d.isPartOf = rs.getString("isPartOf");
                    d.hasAcl = rs.getBoolean("hasAcl");
                    d.isDeleted = rs.getBoolean("isDeleted");

                    d.membershipResource = rs.getString(MEMBERSHIP_RESOURCE);
                    d.hasMemberRelation = rs.getString(HAS_MEMBER_RELATION);
                    d.isMemberOfRelation = rs.getString(IS_MEMBER_OF_RELATION);
                    d.insertedContentRelation = rs.getString(INSERTED_CONTENT_RELATION);
                    final String binaryLocation = rs.getString("location");
                    final Long binaryModified = rs.getLong("binaryModified");
                    final String binaryFormat = rs.getString("format");
                    final Long binarySize = rs.getLong("size");

                    if (LDP.NonRDFSource.getIRIString().equals(d.interactionModel) && nonNull(binaryLocation)
                            && nonNull(binaryModified)) {
                        d.binary = new Binary(rdf.createIRI(binaryLocation), ofEpochMilli(binaryModified),
                                binaryFormat, binarySize);
                    }
                    return d;
                }).findFirst()).ifPresent(d -> this.data = d);
    }

    private static RDFTerm getObject(final String value, final String lang, final String datatype) {
        if (nonNull(lang)) {
            return rdf.createLiteral(value, lang);
        } else if (nonNull(datatype)) {
            return rdf.createLiteral(value, rdf.createIRI(datatype));
        }
        return rdf.createIRI(value);
    }
}
