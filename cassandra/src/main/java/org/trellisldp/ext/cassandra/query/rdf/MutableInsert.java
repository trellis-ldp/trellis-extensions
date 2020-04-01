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
package org.trellisldp.ext.cassandra.query.rdf;

import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.Dataset;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.Metadata;
import org.trellisldp.ext.cassandra.CassandraIOUtils;
import org.trellisldp.ext.cassandra.MutableWriteConsistency;

/**
 * A query to insert mutable data about a resource into Cassandra.
 */
@ApplicationScoped
public class MutableInsert extends ResourceQuery {

    private static final Logger LOGGER = getLogger(MutableInsert.class);

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public MutableInsert() {
        super();
    }

    /**
     * A query that inserts mutable data into Cassandra.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public MutableInsert(final CqlSession session, @MutableWriteConsistency final ConsistencyLevel consistency) {
        super(session, "INSERT INTO " + MUTABLE_TABLENAME
                    + " (interactionModel, mimeType, container, quads, modified, binaryIdentifier, created, identifier)"
                    + " VALUES (?,?,?,?,?,?,?,?);", consistency);
    }

    /**
     * @param metadata the metadata for this resource
     * @param modified the time at which this resource was last modified
     * @param data RDF for this resource
     * @param creation a time-based (version 1) UUID for the moment this resource is created
     * @return whether and when it has been inserted
     */
    public CompletionStage<Void> execute(final Metadata metadata, final Instant modified, final Dataset data,
            final UUID creation) {
        return preparedStatementAsync().thenApply(stmt ->
                stmt.bind(metadata.getInteractionModel(),
                    metadata.getBinary().flatMap(BinaryMetadata::getMimeType).orElse(null),
                    metadata.getContainer().orElse(null), CassandraIOUtils.serialize(data), modified,
                    metadata.getBinary().map(BinaryMetadata::getIdentifier).orElse(null),
                    creation, metadata.getIdentifier()).setConsistencyLevel(consistency))
            .thenCompose(session::executeAsync)
            .thenAccept(r -> LOGGER.debug("Executed query: {}", queryString));
    }
}
