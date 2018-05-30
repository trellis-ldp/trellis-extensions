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
package org.trellisldp.ext.jdbc;

import static java.time.Instant.ofEpochMilli;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.builder;
import static java.util.stream.Stream.concat;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.getInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * A jdbc-based implementation of the Trellis Resource API.
 */
public class JDBCResource implements Resource {

    private static final Logger LOGGER = getLogger(JDBCResource.class);
    private static final RDF rdf = getInstance();

    private final IRI identifier;
    private final Connection connection;
    private final Map<IRI, Supplier<Stream<Quad>>> graphMapper = new HashMap<>();

    // Resource data fields.
    private String interactionModel;
    private Long modified;

    private String isMemberOfRelation;
    private String hasMemberRelation;
    private String membershipResource;
    private String insertedContentRelation;
    private Binary binary;
    private Boolean resourceHasAcl = false;
    private Boolean resourceIsDeleted = false;

    /**
     * Create a JDBC-based Resource.
     * @param connection the jdbc connector
     * @param identifier the identifier
     */
    public JDBCResource(final Connection connection, final IRI identifier) {
        this.identifier = identifier;
        this.connection = connection;
        graphMapper.put(Trellis.PreferUserManaged, this::fetchUserQuads);
        graphMapper.put(Trellis.PreferServerManaged, this::fetchServerQuads);
        graphMapper.put(Trellis.PreferAudit, this::fetchAuditQuads);
        graphMapper.put(Trellis.PreferAccessControl, this::fetchAclQuads);
        graphMapper.put(LDP.PreferContainment, this::fetchContainmentQuads);
        graphMapper.put(LDP.PreferMembership, this::fetchMembershipQuads);
    }

    /**
     * Try to load a Trellis resource.
     * @param connection the triplestore connector
     * @param identifier the identifier
     * @return a Resource, if one exists
     */
    public static Optional<Resource> findResource(final Connection connection, final IRI identifier) {
        final JDBCResource res = new JDBCResource(connection, identifier);
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

    /**
     * Fetch data for this resource.
     */
    protected void fetchData() {
        LOGGER.debug("Fetching data for: {}", identifier);
        try (final Statement stmt = connection.createStatement()) {

            final String query = "SELECT "
                + "interactionModel, modified, hasAcl, isDeleted "
                + "membershipResource, hasMemberRelation, isMemberOfRelation, insertedContentRelation, "
                + "binary, binaryModified, binaryFormat, binarySize "
                + "FROM metadata WHERE id=";
            final ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                interactionModel = rs.getString("interactionModel");
                modified = rs.getLong("modified");
                resourceHasAcl = rs.getBoolean("hasAcl");
                resourceIsDeleted = rs.getBoolean("isDeleted");

                membershipResource = rs.getString("membershipResource");
                hasMemberRelation = rs.getString("hasMemberRelation");
                isMemberOfRelation = rs.getString("isMemberOfRelation");
                insertedContentRelation = rs.getString("insertedContentRelation");

                final String binaryIdentifier = rs.getString("binary");
                final Long binaryModified = rs.getLong("binaryModified");
                final String binaryFormat = rs.getString("binaryFormat");
                final Long binarySize = rs.getLong("binarySize");

                if (LDP.NonRDFSource.getIRIString().equals(interactionModel) &&
                        nonNull(binaryIdentifier) && nonNull(binaryModified)) {
                    binary = new Binary(rdf.createIRI(binaryIdentifier), ofEpochMilli(binaryModified), binaryFormat,
                            binarySize);
                }
            } else {

            }
        } catch (final SQLException ex) {
            throw new RuntimeTrellisException(ex);
        }
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
        return ofNullable(interactionModel).map(rdf::createIRI).orElse(null);
    }

    @Override
    public Optional<IRI> getMembershipResource() {
        return ofNullable(membershipResource).map(rdf::createIRI);
    }

    @Override
    public Optional<IRI> getMemberRelation() {
        return ofNullable(hasMemberRelation).map(rdf::createIRI);
    }

    @Override
    public Optional<IRI> getMemberOfRelation() {
        return ofNullable(isMemberOfRelation).map(rdf::createIRI);
    }

    @Override
    public Optional<IRI> getInsertedContentRelation() {
        return ofNullable(insertedContentRelation).map(rdf::createIRI);
    }

    @Override
    public Optional<Binary> getBinary() {
        return ofNullable(binary);
    }

    @Override
    public Instant getModified() {
        return ofEpochMilli(modified);
    }

    @Override
    public Boolean hasAcl() {
        return resourceHasAcl;
    }

    @Override
    public Boolean isDeleted() {
        return resourceIsDeleted;
    }

    private Stream<Quad> fetchServerQuads() {
        // todo
        return Stream.empty();
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?subject ?predicate ?object
     * WHERE { GRAPH fromGraphName { ?subject ?predicate ?object } }
     * </code></pre>
     */
    private Stream<Quad> fetchAllFromGraph(final String fromGraphName, final IRI toGraphName) {
        // todo
        final Stream.Builder<Quad> builder = builder();
        return builder.build();
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?subject ?predicate ?object
     * WHERE { GRAPH IDENTIFIER?ext=audit { ?subject ?predicate ?object } }
     * </code></pre>
    */
    private Stream<Quad> fetchAuditQuads() {
        // todo
        return fetchAllFromGraph(identifier.getIRIString() + "?ext=audit", Trellis.PreferAudit);
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?subject ?predicate ?object
     * WHERE { GRAPH IDENTIFIER?ext=acl { ?subject ?predicate ?object } }
     * </code></pre>
    */
    private Stream<Quad> fetchAclQuads() {
        return fetchAllFromGraph(identifier.getIRIString() + "?ext=acl", Trellis.PreferAccessControl);
    }

    private Stream<Quad> fetchMembershipQuads() {
        return concat(fetchIndirectMemberQuads(),
                concat(fetchDirectMemberQuads(), fetchDirectMemberQuadsInverse()));
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?subject ?predicate ?object
     * WHERE {
     *   GRAPH trellis:PreferServerManaged {
     *      ?s ldp:member IDENTIFIER
     *      ?s ldp:membershipResource ?subject
     *      AND ?s rdf:type ldp:IndirectContainer
     *      AND ?s ldp:membershipRelation ?predicate
     *      AND ?s ldp:insertedContentRelation ?o
     *      AND ?res dc:isPartOf ?s .
     *   }
     *   GRAPH ?res { ?res ?o ?object }
     * }
     * </code></pre>
     */
    private Stream<Quad> fetchIndirectMemberQuads() {
        final Stream.Builder<Quad> builder = builder();
        return builder.build();
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?subject ?predicate ?object
     * WHERE {
     *   GRAPH trellis:PreferServerManaged {
     *      ?s ldp:member IDENTIFIER
     *      ?s ldp:membershipResource ?subject
     *      AND ?s ldp:hasMemberRelation ?predicate
     *      AND ?s ldp:insertedContentRelation ldp:MemberSubject
     *      AND ?object dc:isPartOf ?s }
     * }
     * </code></pre>
     */
    private Stream<Quad> fetchDirectMemberQuads() {
        final Stream.Builder<Quad> builder = builder();
        return builder.build();
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?predicate ?object
     * WHERE {
     *   GRAPH trellis:PreferServerManaged {
     *      IDENTIFIER dc:isPartOf ?subject .
     *      ?subject ldp:isMemberOfRelation ?predicate .
     *      ?subject ldp:membershipResource ?object .
     *      ?subject ldp:insertedContentRelation ldp:MemberSubject .
     *   }
     * }
     * </code></pre>
     */
    private Stream<Quad> fetchDirectMemberQuadsInverse() {
        final Stream.Builder<Quad> builder = builder();
        return builder.build();
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?object
     * WHERE {
     *   GRAPH trellis:PreferServerManaged { ?object dc:isPartOf IDENTIFIER }
     * }
     * </code></pre>
     */
    private Stream<Quad> fetchContainmentQuads() {
        if (getInteractionModel().getIRIString().endsWith("Container")) {
            final Stream.Builder<Quad> builder = builder();
            return builder.build();
        }
        return Stream.empty();
    }

    /**
     * This code is equivalent to the SPARQL query below.
     *
     * <p><pre><code>
     * SELECT ?subject ?predicate ?object
     * WHERE { GRAPH IDENTIFIER { ?subject ?predicate ?object } }
     * </code></pre>
     */
    private Stream<Quad> fetchUserQuads() {
        return fetchAllFromGraph(identifier.getIRIString(), Trellis.PreferUserManaged);
    }
}
