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

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.MutableReadConsistency;

/**
 * Retrieve data for a Memento.
 */
@ApplicationScoped
public class GetMemento extends ResourceQuery {

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public GetMemento() {
        super();
    }

    /**
     * A class that retrieves data for a Memento.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public GetMemento(final CqlSession session, @MutableReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT * FROM " + MEMENTO_MUTABLE_TABLENAME
                        + " WHERE identifier = :identifier AND mementomodified <= :time " + " LIMIT 1 ALLOW FILTERING;",
                        consistency);
    }

    /**
     * @param id the {@link IRI} of the Memento to retrieve
     * @param time the time for which this Memento is valid
     * @return the data for the Memento
     */
    public CompletionStage<AsyncResultSet> execute(final IRI id, final Instant time) {
        return preparedStatementAsync().thenApply(stmt -> stmt.bind().set("time", time, Instant.class)
                .set("identifier", id, IRI.class))
            .thenCompose(session::executeAsync);
    }
}
