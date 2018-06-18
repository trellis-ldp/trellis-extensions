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
import static java.util.Optional.ofNullable;
import static org.trellisldp.api.RDFUtils.getInstance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.trellisldp.api.Binary;

/**
 * A simple Data POJO.
 */
class ResourceData {

    private static final RDF rdf = getInstance();

    private String interactionModel;
    private Long modified;
    private String isPartOf;
    private Boolean resourceHasAcl = false;
    private Boolean resourceIsDeleted = false;

    private String membershipResource;
    private String hasMemberRelation;
    private String isMemberOfRelation;
    private String insertedContentRelation;

    private Binary binary;
    private Map<String, String> extra;

    public ResourceData(final ResultSet rs, final Map<String, String> extra) throws SQLException {
        this.interactionModel = rs.getString("interactionModel");
        this.modified = rs.getLong("modified");
        this.isPartOf = rs.getString("isPartOf");
        this.resourceHasAcl = rs.getBoolean("hasAcl");
        this.resourceIsDeleted = rs.getBoolean("isDeleted");

        this.membershipResource = rs.getString("membershipResource");
        this.hasMemberRelation = rs.getString("hasMemberRelation");
        this.isMemberOfRelation = rs.getString("isMemberOfRelation");
        this.insertedContentRelation = rs.getString("insertedContentRelation");

        this.extra = extra;

        DBUtils.getBinary(rdf.createIRI(this.interactionModel), rs.getString("location"), rs.getLong("binaryModified"),
                rs.getString("format"), rs.getLong("size")).ifPresent(binary -> this.binary = binary);
    }

    public IRI getInteractionModel() {
        return ofNullable(interactionModel).map(rdf::createIRI).orElse(null);
    }

    public Instant getModified() {
        return ofNullable(modified).map(Instant::ofEpochMilli).orElse(null);
    }

    public Optional<IRI> getIsPartOf() {
        return ofNullable(isPartOf).map(rdf::createIRI);
    }

    public Boolean hasAcl() {
        return resourceHasAcl;
    }

    public Boolean isDeleted() {
        return resourceIsDeleted;
    }

    public Optional<IRI> getMembershipResource() {
        return ofNullable(membershipResource).map(rdf::createIRI);
    }

    public Optional<IRI> getHasMemberRelation() {
        return ofNullable(hasMemberRelation).map(rdf::createIRI);
    }

    public Optional<IRI> getIsMemberOfRelation() {
        return ofNullable(isMemberOfRelation).map(rdf::createIRI);
    }

    public Optional<IRI> getInsertedContentRelation() {
        return ofNullable(insertedContentRelation).map(rdf::createIRI);
    }

    public Optional<Binary> getBinary() {
        return ofNullable(binary);
    }

    public Map<String, String> getExtra() {
        return extra;
    }
}
