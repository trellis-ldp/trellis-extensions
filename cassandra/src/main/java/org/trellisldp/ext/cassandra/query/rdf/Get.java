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

import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.MutableReadConsistency;

/**
 * Retrieve data for a resource.
 */
@ApplicationScoped
public class Get extends ResourceQuery {

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public Get() {
        super();
    }

    /**
     * Retrieve data for a resource.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public Get(final CqlSession session, @MutableReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT * FROM " + MUTABLE_TABLENAME + " WHERE identifier = :identifier;", consistency);
    }

    /**
     * @param id the {@link IRI} of the resource
     * @return a {@link AsyncResultSet} of the resource data
     */
    public CompletionStage<AsyncResultSet> execute(final IRI id) {
        return preparedStatementAsync().thenApply(stmt -> stmt.bind().set("identifier", id, IRI.class))
            .thenCompose(session::executeAsync);
    }
}
