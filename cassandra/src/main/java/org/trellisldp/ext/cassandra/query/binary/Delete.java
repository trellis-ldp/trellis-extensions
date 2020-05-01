/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
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
package org.trellisldp.ext.cassandra.query.binary;

import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;

import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.ext.cassandra.BinaryWriteConsistency;

/**
 * A query that deletes a binary.
 *
 */
@ApplicationScoped
public class Delete extends BinaryQuery {

    private static final Logger LOGGER = getLogger(Delete.class);

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public Delete() {
        super();
    }

    /**
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public Delete(final CqlSession session, @BinaryWriteConsistency final ConsistencyLevel consistency) {
        super(session, "DELETE FROM " + BINARY_TABLENAME + " WHERE identifier = :identifier;", consistency);
    }

    /**
     * @param id an {@link IRI} for a binary to delete
     * @return whether and when it has been deleted
     */
    public CompletionStage<Void> execute(final IRI id) {
        return preparedStatementAsync().thenApply(stmt -> stmt.bind().set("identifier", id, IRI.class))
            .thenCompose(session::executeAsync)
            .thenAccept(r -> LOGGER.debug("Executed query: {}", queryString));
    }
}
