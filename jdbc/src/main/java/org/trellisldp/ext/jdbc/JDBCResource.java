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
import static org.trellisldp.vocabulary.RDF.type;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.vocabulary.XSD;

/**
 * A jdbc-based implementation of the Trellis Resource API.
 */
public class JDBCResource implements Resource {

    private static final Logger LOGGER = getLogger(JDBCResource.class);
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
    private final Connection connection;
    private final Map<IRI, Supplier<Stream<Quad>>> graphMapper = new HashMap<>();

    // Resource data fields.
    private String interactionModel;
    private Long modified;
    private String isPartOf;

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
        return ofNullable(modified).map(Instant::ofEpochMilli).orElse(null);
    }

    @Override
    public Boolean hasAcl() {
        return resourceHasAcl;
    }

    @Override
    public Boolean isDeleted() {
        return resourceIsDeleted;
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
        ofNullable(isPartOf).map(rdf::createIRI).ifPresent(parent ->
                builder.add(rdf.createQuad(Trellis.PreferServerManaged, getIdentifier(), DC.isPartOf, parent)));
        return builder.build();
    }

    private Stream<Quad> fetchUserQuads() {
        return fetchQuadsFromTable("resource", Trellis.PreferUserManaged);
    }

    private Stream<Quad> fetchAuditQuads() {
        return fetchQuadsFromTable("audit", Trellis.PreferAudit);
    }

    private Stream<Quad> fetchAclQuads() {
        return fetchQuadsFromTable("acl", Trellis.PreferAccessControl);
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
        final String query
            = "SELECT l.membershipResource, l.hasMemberRelation, r.object, r.lang, r.datatype "
            + "FROM ldp AS l INNER JOIN metadata AS m ON l.id = m.isPartOf "
            + "INNER JOIN resource AS r ON m.id = r.id AND r.predicate = l.insertedContentRelation "
            + "WHERE l.member = ? AND m.interactionModel = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, identifier.getIRIString());
            stmt.setString(2, LDP.IndirectContainer.getIRIString());
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                final String object = rs.getString(OBJECT);
                final String lang = rs.getString(LANG);
                final String datatype = rs.getString(DATATYPE);
                builder.add(rdf.createQuad(LDP.PreferMembership, rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE)),
                            rdf.createIRI(rs.getString(HAS_MEMBER_RELATION)),
                            getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))));
            }
        } catch (final SQLException ex) {
            throw new RuntimeTrellisException(ex);
        }
        return builder.build();
    }

    private Stream<Quad> fetchDirectMemberQuadsInverse() {
        final Stream.Builder<Quad> builder = builder();
        final String query
            = "SELECT l.isMemberOfRelation, l.membershipResource "
            + "FROM ldp AS l INNER JOIN metadata AS m ON l.id = m.isPartOf "
            + "WHERE m.id = ? AND l.insertedContentRelation = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, getIdentifier().getIRIString());
            stmt.setString(2, LDP.MemberSubject.getIRIString());
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                builder.add(rdf.createQuad(LDP.PreferMembership, getIdentifier(),
                            rdf.createIRI(rs.getString(IS_MEMBER_OF_RELATION)),
                            rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE))));

            }
        } catch (final SQLException ex) {
            throw new RuntimeTrellisException(ex);
        }
        return builder.build();
    }

    private Stream<Quad> fetchDirectMemberQuads() {
        final Stream.Builder<Quad> builder = builder();
        final String query
            = "SELECT l.membershipResource, l.hasMemberRelation, m.id "
            + "FROM ldp AS l INNER JOIN metadata AS m ON l.id = m.isPartOf "
            + "WHERE l.member = ? AND l.insertedContentRelation = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, identifier.getIRIString());
            stmt.setString(2, LDP.MemberSubject.getIRIString());
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                builder.add(rdf.createQuad(LDP.PreferMembership, rdf.createIRI(rs.getString(MEMBERSHIP_RESOURCE)),
                            rdf.createIRI(rs.getString(HAS_MEMBER_RELATION)), rdf.createIRI(rs.getString("id"))));

            }
        } catch (final SQLException ex) {
            throw new RuntimeTrellisException(ex);
        }
        return builder.build();
    }

    private Stream<Quad> fetchContainmentQuads() {
        if (getInteractionModel().getIRIString().endsWith("Container")) {
            final Stream.Builder<Quad> builder = builder();
            final String query = "SELECT id FROM metadata where isPartOf = ?";
            try (final PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, identifier.getIRIString());
                final ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    builder.add(rdf.createQuad(LDP.PreferContainment, getIdentifier(), LDP.contains,
                                rdf.createIRI(rs.getString("id"))));
                }
            } catch (final SQLException ex) {
                throw new RuntimeTrellisException(ex);
            }
            return builder.build();
        }
        return Stream.empty();
    }

    /**
     * Fetch data for this resource.
     */
    protected void fetchData() {
        LOGGER.debug("Fetching data for: {}", identifier);
        final String query
            = "SELECT m.interactionModel, m.modified, m.isPartOf, m.isDeleted, m.hasAcl, "
            + "l.membershipResource, l.hasMemberRelation, l.isMemberOfRelation, l.insertedContentRelation, "
            + "b.location, b.modified AS binaryModified, b.format, b.size "
            + "FROM medatdata AS m "
            + "LEFT JOIN ldp AS l ON m.id = l.id "
            + "LEFT JOIN binary AS b ON m.id = b.id "
            + "WHERE m.id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, identifier.getIRIString());
            final ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                interactionModel = rs.getString("interactionModel");
                modified = rs.getLong("modified");
                isPartOf = rs.getString("isPartOf");
                resourceHasAcl = rs.getBoolean("hasAcl");
                resourceIsDeleted = rs.getBoolean("isDeleted");

                membershipResource = rs.getString(MEMBERSHIP_RESOURCE);
                hasMemberRelation = rs.getString(HAS_MEMBER_RELATION);
                isMemberOfRelation = rs.getString(IS_MEMBER_OF_RELATION);
                insertedContentRelation = rs.getString(INSERTED_CONTENT_RELATION);

                final String binaryLocation = rs.getString("location");
                final Long binaryModified = rs.getLong("binaryModified");
                final String binaryFormat = rs.getString("format");
                final Long binarySize = rs.getLong("size");

                if (LDP.NonRDFSource.getIRIString().equals(interactionModel) && nonNull(binaryLocation)
                        && nonNull(binaryModified)) {
                    binary = new Binary(rdf.createIRI(binaryLocation), ofEpochMilli(binaryModified), binaryFormat,
                            binarySize);
                }
            }
        } catch (final SQLException ex) {
            throw new RuntimeTrellisException(ex);
        }
    }

    private Stream<Quad> fetchQuadsFromTable(final String tableName, final IRI graphName) {
        final Stream.Builder<Quad> builder = builder();
        final String query = "SELECT subject, predicate, object, lang, datatype "
                           + "FROM " + tableName + " WHERE id = ?";
        try (final PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, identifier.getIRIString());
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                final String object = rs.getString(OBJECT);
                final String lang = rs.getString(LANG);
                final String datatype = rs.getString(DATATYPE);
                builder.add(rdf.createQuad(graphName, rdf.createIRI(rs.getString(SUBJECT)),
                            rdf.createIRI(rs.getString(PREDICATE)),
                            getObject(rs.getString(OBJECT), rs.getString(LANG), rs.getString(DATATYPE))));
            }
        } catch (final SQLException ex) {
            throw new RuntimeTrellisException(ex);
        }
        return builder.build();
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
