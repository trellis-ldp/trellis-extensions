/*
 * Copyright (c) 2017 - 2020 Aaron Coburn and individual contributors
 *
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

import static java.time.temporal.ChronoUnit.SECONDS;
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
 * A query that records a version of a resource as a Memento.
 */
@ApplicationScoped
public class Mementoize extends ResourceQuery {

    private static final Logger LOGGER = getLogger(Mementoize.class);

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public Mementoize() {
        super();
    }

    /**
     * Create a query that generates a memento.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public Mementoize(final CqlSession session, @MutableWriteConsistency final ConsistencyLevel consistency) {
        super(session, "INSERT INTO " + MEMENTO_MUTABLE_TABLENAME
                        + " (interactionModel, mimeType, container, quads, modified, binaryIdentifier, "
                        + "created, identifier, mementomodified)" + " VALUES (?,?,?,?,?,?,?,?,?);", consistency);
    }

    /**
     * Store a Memento. Note that the value for {@code modified} is truncated to seconds because Memento requires HTTP
     * time management.
     *
     * @param metadata metadata for this resource
     * @param modified the time at which this resource was last modified
     * @param data RDF for this resource
     * @param creation a time-based (version 1) UUID for the moment this resource is created
     * @return whether and when it has been inserted
     */
    public CompletionStage<Void> execute(final Metadata metadata, final Instant modified,
            final Dataset data, final UUID creation) {
        return preparedStatementAsync().thenApply(stmt -> stmt.bind(metadata.getInteractionModel(),
                    metadata.getBinary().flatMap(BinaryMetadata::getMimeType).orElse(null),
                    metadata.getContainer().orElse(null), CassandraIOUtils.serialize(data), modified,
                    metadata.getBinary().map(BinaryMetadata::getIdentifier).orElse(null),
                    creation, metadata.getIdentifier(), modified.truncatedTo(SECONDS))
                .setConsistencyLevel(consistency))
            .thenCompose(session::executeAsync)
            .thenAccept(r -> LOGGER.debug("Executed query: {}", queryString));
    }
}
