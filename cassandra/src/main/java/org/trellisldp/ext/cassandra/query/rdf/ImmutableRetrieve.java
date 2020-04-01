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
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.trellisldp.ext.cassandra.AsyncResultSetUtils;
import org.trellisldp.ext.cassandra.CassandraIOUtils;
import org.trellisldp.ext.cassandra.MutableReadConsistency;

/**
 * A query to retrieve immutable data about a resource from Cassandra.
 */
@ApplicationScoped
public class ImmutableRetrieve extends ResourceQuery {

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public ImmutableRetrieve() {
        super();
    }

    /**
     * Retrieve immutable data about a resource.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public ImmutableRetrieve(final CqlSession session, @MutableReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT quads FROM " + IMMUTABLE_TABLENAME + "  WHERE identifier = :identifier ;", consistency);
    }

    /**
     * @param id the {@link IRI} of the resource, the immutable data of which is to be retrieved
     * @return the RDF retrieved
     */
    public CompletionStage<Stream<Quad>> execute(final IRI id) {
        return preparedStatementAsync().thenApply(stmt -> stmt.bind().set("identifier", id, IRI.class))
            .thenCompose(session::executeAsync)
            .thenApply(AsyncResultSetUtils::stream)
            .thenApply(row -> row.map(this::getDataset))
            .thenApply(r -> r.map(CassandraIOUtils::parse).flatMap(Dataset::stream));
    }

    private String getDataset(final Row r) {
        return r.getString("quads");
    }
}
