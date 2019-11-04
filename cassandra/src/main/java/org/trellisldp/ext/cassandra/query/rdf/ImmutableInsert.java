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
import com.datastax.oss.driver.api.core.cql.BoundStatement;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.MutableWriteConsistency;

/**
 * A query to insert immutable data about a resource into Cassandra.
 */
public class ImmutableInsert extends ResourceQuery {

    /**
     * Create a query to insert immutable data into Cassandra.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public ImmutableInsert(final CqlSession session, @MutableWriteConsistency final ConsistencyLevel consistency) {
        super(session, "INSERT INTO " + IMMUTABLE_TABLENAME + " (identifier, quads, created) VALUES (?,?,?)",
                        consistency);
    }

    /**
     * @param id the {@link IRI} of the resource, immutable data for which is to be inserted
     * @param data the RDF to be inserted
     * @param time the time at which this RDF is to be recorded as inserted
     * @return whether and when the insertion succeeds
     */
    public CompletionStage<Void> execute(final IRI id, final Dataset data, final Instant time) {
        final BoundStatement statement = preparedStatement().bind(id, data, time);
        return executeWrite(statement);
    }
}
