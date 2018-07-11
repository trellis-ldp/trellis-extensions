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

import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
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
    private static final String MEMBERSHIP_RESOURCE = "membership_resource";
    private static final String HAS_MEMBER_RELATION = "has_member_relation";
    private static final String IS_MEMBER_OF_RELATION = "is_member_of_relation";

    private final IRI identifier;
    private final Jdbi jdbi;
    private final Map<IRI, Supplier<Stream<Quad>>> graphMapper = new HashMap<>();

    private ResourceData data;

    /**
     * Create a DB-based Resource.
     * @param jdbi the jdbi object
     * @param identifier the identifier
     */
    protected DBResource(final Jdbi jdbi, final IRI identifier) {
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
        return res.fetchData() ? of(res) : empty();
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
        return data.getInteractionModel();
    }

    @Override
    public Optional<IRI> getMembershipResource() {
        return data.getMembershipResource();
    }

    @Override
    public Optional<IRI> getMemberRelation() {
        return data.getHasMemberRelation();
    }

    @Override
    public Optional<IRI> getMemberOfRelation() {
        return data.getIsMemberOfRelation();
    }

    @Override
    public Optional<IRI> getInsertedContentRelation() {
        return data.getInsertedContentRelation();
    }

    @Override
    public Optional<Binary> getBinary() {
        return data.getBinary();
    }

    @Override
    public Instant getModified() {
        return data.getModified();
    }

    @Override
    public Boolean hasAcl() {
        return data.hasAcl();
    }

    @Override
    public Boolean isDeleted() {
        return data.isDeleted();
    }

    @Override
    public Stream<Map.Entry<String,String>> getExtraLinkRelations() {
        return data.getExtra().entrySet().stream();
    }

    private Stream<Quad> fetchMembershipQuads() {
        return concat(fetchIndirectMemberQuads(),
                concat(fetchDirectMemberQuads(), fetchDirectMemberQuadsInverse()));
    }

    private Stream<Quad> fetchServerQuads() {
        final Stream.Builder<Quad> builder = builder();
        builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), type, getInteractionModel()));
        of(getModified()).map(m -> rdf.createLiteral(m.toString(), XSD.dateTime)).ifPresent(modified ->
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), DC.modified, modified)));
        data.getIsPartOf().ifPresent(parent ->
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), DC.isPartOf, parent)));
        data.getBinary().ifPresent(binary -> {
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), DC.hasPart,
                            binary.getIdentifier()));
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, binary.getIdentifier(), DC.modified,
                            rdf.createLiteral(binary.getModified().toString(), XSD.dateTime)));
                binary.getMimeType().ifPresent(format ->
                        builder.add(rdf.createQuad(Trellis.PreferServerManaged, binary.getIdentifier(), DC.format,
                                rdf.createLiteral(format))));
                binary.getSize().ifPresent(size ->
                        builder.add(rdf.createQuad(Trellis.PreferServerManaged, binary.getIdentifier(), DC.extent,
                                rdf.createLiteral(size.toString(), XSD.long_))));
        });
        return builder.build();
    }

    private Stream<Quad> fetchUserQuads() {
        return fetchQuadsFromTable("description", Trellis.PreferUserManaged);
    }

    private Stream<Quad> fetchAuditQuads() {
        return fetchQuadsFromTable("log", Trellis.PreferAudit);
    }

    private Stream<Quad> fetchAclQuads() {
        return fetchQuadsFromTable("acl", Trellis.PreferAccessControl);
    }

    private Stream<Quad> fetchIndirectMemberQuads() {
        final String query
            = "SELECT l.membership_resource, l.has_member_relation, d.object, d.lang, d.datatype "
            + "FROM ldp AS l INNER JOIN resource AS r ON l.id = r.is_part_of "
            + "INNER JOIN resource AS r2 ON l.id = r2.id "
            + "INNER JOIN description AS d ON r.id = d.id AND d.predicate = l.inserted_content_relation "
            + "WHERE l.member = ? AND r2.interaction_model = ? AND l.has_member_relation IS NOT NULL";

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
            = "SELECT l.is_member_of_relation, l.membership_resource FROM ldp AS l "
            + "INNER JOIN resource AS r ON l.id = r.is_part_of "
            + "WHERE r.id = ? AND l.inserted_content_relation = ? AND l.is_member_of_relation IS NOT NULL";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.MemberSubject.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership, getIdentifier(),
                        rdf.createIRI(rs.getString(IS_MEMBER_OF_RELATION)),
                        rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE))))
                .stream());
    }

    private Stream<Quad> fetchDirectMemberQuads() {
        final String query
            = "SELECT l.membership_resource, l.has_member_relation, r.id FROM ldp AS l "
            + "INNER JOIN resource AS r ON l.id = r.is_part_of "
            + "WHERE l.member = ? AND l.inserted_content_relation = ? AND l.has_member_relation IS NOT NULL";

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
            final String query = "SELECT id FROM resource where is_part_of = ?";
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
     * @return true if data was found; false otherwise
     */
    protected Boolean fetchData() {
        LOGGER.debug("Fetching data for: {}", identifier);
        final String extraQuery = "SELECT predicate, object FROM extra WHERE subject = ?";
        final Map<String, String> extras = new HashMap<>();
        jdbi.useHandle(handle ->
                handle.select(extraQuery, identifier.getIRIString())
                      .map((rs, ctx) -> new SimpleImmutableEntry<>(rs.getString(OBJECT), rs.getString(PREDICATE)))
                      .forEach(entry -> extras.put(entry.getKey(), entry.getValue())));

        final String query
            = "SELECT r.interaction_model, r.modified, r.is_part_of, r.deleted, r.acl, "
            + "l.membership_resource, l.has_member_relation, l.is_member_of_relation, l.inserted_content_relation, "
            + "nr.location, nr.modified AS binary_modified, nr.format, nr.size "
            + "FROM resource AS r "
            + "LEFT JOIN ldp AS l ON r.id = l.id "
            + "LEFT JOIN nonrdf AS nr ON r.id = nr.id "
            + "WHERE r.id = ?";
        final Optional<ResourceData> rd = jdbi.withHandle(handle -> handle.select(query, identifier.getIRIString())
                .map((rs, ctx) -> new ResourceData(rs, extras)).findFirst());
        if (rd.isPresent()) {
            this.data = rd.get();
            return true;
        }
        return false;
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
