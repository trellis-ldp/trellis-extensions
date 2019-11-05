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

import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.BinaryWriteConsistency;

/**
 * A query that deletes a binary.
 *
 */
public class Delete extends BinaryQuery {

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
        final BoundStatement statement = preparedStatement().bind().set("identifier", id, IRI.class);
        return executeWrite(statement);
    }
}
