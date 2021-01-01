/*
 * Copyright (c) 2021 Aaron Coburn and individual contributors
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

import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.ext.cassandra.MutableWriteConsistency;

/**
 * A query that adjusts the modified time of a resource.
 */
@ApplicationScoped
public class Touch extends ResourceQuery {

    private static final Logger LOGGER = getLogger(Touch.class);

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public Touch() {
        super();
    }

    /**
     * Create a query that adjusts the modified time of a resource.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public Touch(final CqlSession session, @MutableWriteConsistency final ConsistencyLevel consistency) {
        super(session, "UPDATE " + MUTABLE_TABLENAME + " SET modified = :modified WHERE identifier = :identifier",
                        consistency);
    }

    /**
     * @param modified the new modification time to record
     * @param id the {@link IRI} of the resource to modify
     * @return whether and when the modification succeeds
     */
    public CompletionStage<Void> execute(final Instant modified, final IRI id) {
        return preparedStatementAsync().thenApply(stmt ->
                stmt.bind().set("modified", modified, Instant.class)
                           .set("identifier", id, IRI.class)
                           .setConsistencyLevel(consistency))
            .thenCompose(session::executeAsync)
            .thenAccept(r -> LOGGER.debug("Executed query: {}", queryString));
    }
}
