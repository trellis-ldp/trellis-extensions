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
package org.trellisldp.ext.cassandra.query.binary;

import static java.util.Objects.requireNonNull;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.BinaryReadConsistency;

/**
 * A query to retrieve the chunk size metadata for a binary.
 *
 */
public class GetChunkSize extends BinaryQuery {

    /**
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public GetChunkSize(final CqlSession session, @BinaryReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT chunkSize FROM " + BINARY_TABLENAME + " WHERE identifier = :identifier LIMIT 1;",
                        consistency);
    }

    /**
     * @param id the {@link IRI} of the binary to retrieve
     * @return a {@link Row} with the chunk size for this binary
     */
    public CompletionStage<Row> execute(final IRI id) {
        final BoundStatement statement = preparedStatement().bind().set("identifier", id, IRI.class);
        return executeRead(statement)
                        .thenApply(AsyncResultSet::one)
                        .thenApply(row -> requireNonNull(row,
                                        () -> "Binary not found under IRI: " + id.getIRIString() + " !"));
    }
}
