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

import static java.util.Optional.ofNullable;
import static org.trellisldp.api.TrellisUtils.getInstance;

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

    private Integer id;
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

    public ResourceData(final ResultSet rs) throws SQLException {
        this.interactionModel = rs.getString("interaction_model");
        this.id = rs.getInt("id");
        this.modified = rs.getLong("modified");
        this.isPartOf = rs.getString("is_part_of");
        this.resourceHasAcl = rs.getBoolean("acl");
        this.resourceIsDeleted = rs.getBoolean("deleted");

        this.membershipResource = rs.getString("ldp_membership_resource");
        this.hasMemberRelation = rs.getString("ldp_has_member_relation");
        this.isMemberOfRelation = rs.getString("ldp_is_member_of_relation");
        this.insertedContentRelation = rs.getString("ldp_inserted_content_relation");

        this.binary = DBUtils.getBinary(rdf.createIRI(this.interactionModel), rs.getString("binary_location"),
                rs.getLong("binary_modified"), rs.getString("binary_format"), rs.getLong("binary_size")).orElse(null);
    }

    public Integer getId() {
        return id;
    }

    public IRI getInteractionModel() {
        return ofNullable(interactionModel).map(rdf::createIRI).orElse(null);
    }

    public Instant getModified() {
        return ofNullable(modified).filter(x -> x > 0).map(Instant::ofEpochMilli).orElseGet(Instant::now);
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

    public void setExtra(final Map<String, String> extra) {
        this.extra = extra;
    }
}
