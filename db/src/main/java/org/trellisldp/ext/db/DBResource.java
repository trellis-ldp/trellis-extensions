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
import static java.util.Optional.of;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Stream.builder;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.getInstance;
import static org.trellisldp.api.Resource.SpecialResources.DELETED_RESOURCE;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.vocabulary.RDF.type;

import java.time.Instant;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    private static final String MEMBERSHIP_RESOURCE = "ldp_membership_resource";
    private static final String HAS_MEMBER_RELATION = "ldp_has_member_relation";
    private static final String IS_MEMBER_OF_RELATION = "ldp_is_member_of_relation";

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
    public static CompletableFuture<Resource> findResource(final DataSource ds, final IRI identifier) {
        return findResource(Jdbi.create(ds), identifier);
    }

    /**
     * Try to load a Trellis resource.
     * @param jdbi the Jdbi object
     * @param identifier the identifier
     * @return a Resource, if one exists
     */
    public static CompletableFuture<Resource> findResource(final Jdbi jdbi, final IRI identifier) {
        return supplyAsync(() -> {
            final DBResource res = new DBResource(jdbi, identifier);
            if (!res.fetchData()) {
                return MISSING_RESOURCE;
            }
            if (res.isDeleted()) {
                return DELETED_RESOURCE;
            }
            return res;
        });
    }

    /**
     * Identify whether this resource had previously been deleted.
     * @return true if the resource previously existed
     */
    public Boolean isDeleted() {
        return data.isDeleted();
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
    public Optional<IRI> getContainer() {
        return data.getIsPartOf();
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
    public Stream<Map.Entry<String,String>> getExtraLinkRelations() {
        return data.getExtra().entrySet().stream();
    }

    /**
     * Combine the various membership-related quad streams.
     */
    private Stream<Quad> fetchMembershipQuads() {
        return concat(fetchIndirectMemberQuads(),
                concat(fetchDirectMemberQuads(), fetchDirectMemberQuadsInverse()));
    }

    /**
     * Fetch a stream of server quads.
     */
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

    /**
     * Fetch a stream of user-managed quads.
     */
    private Stream<Quad> fetchUserQuads() {
        return fetchQuadsFromTable("description", Trellis.PreferUserManaged);
    }

    /**
     * Fetch a stream of webac quads.
     */
    private Stream<Quad> fetchAclQuads() {
        return fetchQuadsFromTable("acl", Trellis.PreferAccessControl);
    }

    /**
     * Fetch a stream of the audit-related quads.
     */
    private Stream<Quad> fetchAuditQuads() {
        final String query = "SELECT subject, predicate, object, lang, datatype FROM log WHERE id = ?";
        return jdbi.withHandle(handle -> handle.select(query, getIdentifier().getIRIString())
                .map((rs, ctx) -> rdf.createQuad(Trellis.PreferAudit, rdf.createIRI(rs.getString(SUBJECT)),
                        rdf.createIRI(rs.getString(PREDICATE)),
                        getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))))
                .stream());
    }

    /**
     * Fetch a stream of membership quads based on indirect containment with a custom
     * ldp:insertedContentRelation value.
     */
    private Stream<Quad> fetchIndirectMemberQuads() {
        final String query
            = "SELECT r2.ldp_membership_resource, r2.ldp_has_member_relation, d.object, d.lang, d.datatype "
            + "FROM resource AS r INNER JOIN resource AS r2 ON r.is_part_of = r2.subject "
            + "INNER JOIN description AS d ON r.id = d.resource_id AND d.predicate = r2.ldp_inserted_content_relation "
            + "WHERE r2.ldp_member = ? AND r2.interaction_model = ? AND r2.ldp_has_member_relation IS NOT NULL";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.IndirectContainer.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership,
                            rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE)),
                            rdf.createIRI(rs.getString(HAS_MEMBER_RELATION)),
                            getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))))
                .stream());
    }

    /**
     * Fetch a stream of membership quads that are built with ldp:isMemberOfRelation
     * and either direct or indirect containment where the ldp:insertedContentRelation
     * is equal to ldp:MemberSubject.
     */
    private Stream<Quad> fetchDirectMemberQuadsInverse() {
        final String query
            = "SELECT r2.ldp_is_member_of_relation, r2.ldp_membership_resource "
            + "FROM resource AS r INNER JOIN resource AS r2 ON r.is_part_of = r2.subject "
            + "WHERE r.subject = ? AND r2.ldp_inserted_content_relation = ? "
            + "AND r2.ldp_is_member_of_relation IS NOT NULL";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.MemberSubject.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership, getIdentifier(),
                        rdf.createIRI(rs.getString(IS_MEMBER_OF_RELATION)),
                        rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE))))
                .stream());
    }

    /**
     * Fetch a stream of membership quads that are built with ldp:hasMemberRelation
     * and direct containment.
     */
    private Stream<Quad> fetchDirectMemberQuads() {
        final String query
            = "SELECT r.ldp_membership_resource, r.ldp_has_member_relation, r2.subject "
            + "FROM resource AS r INNER JOIN resource AS r2 ON r.subject = r2.is_part_of "
            + "WHERE r.ldp_member = ? AND r.ldp_inserted_content_relation = ? "
            + "AND r.ldp_has_member_relation IS NOT NULL";

        return jdbi.withHandle(handle -> handle.select(query,
                    getIdentifier().getIRIString(), LDP.MemberSubject.getIRIString())
                .map((rs, ctx) -> rdf.createQuad(LDP.PreferMembership,
                        rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE)),
                        rdf.createIRI(rs.getString(HAS_MEMBER_RELATION)),
                        rdf.createIRI(rs.getString(SUBJECT))))
                .stream());
    }

    /**
     * Fetch a stream of containment quads for a resource.
     */
    private Stream<Quad> fetchContainmentQuads() {
        if (getInteractionModel().getIRIString().endsWith("Container")) {
            final String query = "SELECT subject FROM resource WHERE is_part_of = ?";
            return jdbi.withHandle(handle -> handle.select(query,
                        getIdentifier().getIRIString())
                    .map((rs, ctx) -> rdf.createQuad(LDP.PreferContainment, getIdentifier(),
                            LDP.contains, rdf.createIRI(rs.getString(SUBJECT))))
                    .stream());
        }
        return empty();
    }

    private Stream<Quad> fetchQuadsFromTable(final String tableName, final IRI graphName) {
        final String query = "SELECT subject, predicate, object, lang, datatype "
                           + "FROM " + tableName + " WHERE resource_id = ?";
        return jdbi.withHandle(handle -> handle.select(query, data.getId())
                .map((rs, ctx) -> rdf.createQuad(graphName, rdf.createIRI(rs.getString(SUBJECT)),
                        rdf.createIRI(rs.getString(PREDICATE)),
                        getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))))
                .stream());
    }

    /**
     * Fetch data for this resource.
     * @return true if data was found; false otherwise
     */
    private Boolean fetchData() {
        LOGGER.debug("Fetching data for: {}", identifier);
        final String extraQuery = "SELECT predicate, object FROM extra WHERE resource_id = ?";
        final String query
            = "SELECT id, interaction_model, modified, is_part_of, deleted, acl, "
            + "ldp_membership_resource, ldp_has_member_relation, ldp_is_member_of_relation, "
            + "ldp_inserted_content_relation, binary_location, binary_modified, binary_format, binary_size "
            + "FROM resource WHERE subject = ?";
        final Optional<ResourceData> rd = jdbi.withHandle(handle -> handle.select(query, identifier.getIRIString())
                .map((rs, ctx) -> new ResourceData(rs)).findFirst());
        if (rd.isPresent()) {
            this.data = rd.get();
            final Map<String, String> extras = new HashMap<>();
            jdbi.useHandle(handle ->
                    handle.select(extraQuery, this.data.getId())
                          .map((rs, ctx) -> new SimpleImmutableEntry<>(rs.getString(OBJECT), rs.getString(PREDICATE)))
                          .forEach(entry -> extras.put(entry.getKey(), entry.getValue())));

            this.data.setExtra(extras);
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
