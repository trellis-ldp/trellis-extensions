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

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;

import java.io.InputStream;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.BinaryWriteConsistency;

/**
 * Insert binary data into a table.
 */
public class Insert extends BinaryQuery implements Executor {

    /**
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public Insert(final CqlSession session, @BinaryWriteConsistency final ConsistencyLevel consistency) {
        super(session, "INSERT INTO " + BINARY_TABLENAME + " (identifier, chunkSize, chunkIndex, chunk) VALUES "
                        + "(:identifier, :chunkSize, :chunkIndex, :chunk)", consistency);
    }

    /**
     * @param id the {@link IRI} of this binary
     * @param chunkSize size of chunk to use for this binary
     * @param chunkIndex which chunk this is
     * @param chunk the bytes of this chunk
     * @return whether and when it has been inserted
     */
    public CompletionStage<Void> execute(final IRI id, final int chunkSize, final int chunkIndex,
            final InputStream chunk) {
        final BoundStatement boundStatement = preparedStatement().bind().set("identifier", id, IRI.class)
                        .setInt("chunkSize", chunkSize).setInt("chunkIndex", chunkIndex)
                        .set("chunk", chunk, InputStream.class);
        return executeWrite(boundStatement);
    }

    @Override
    public void execute(final Runnable command) {
        writeWorkers.execute(command);
    }
}
