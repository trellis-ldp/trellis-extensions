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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.trellisldp.ext.cassandra.BinaryReadConsistency;

/**
 * Reads a range of bytes from a binary to an {@link InputStream}.
 */
@ApplicationScoped
public class ReadRange extends BinaryReadQuery {

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public ReadRange() {
        super();
    }

    /**
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public ReadRange(final CqlSession session, @BinaryReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT chunkIndex FROM " + BINARY_TABLENAME
                        + " WHERE identifier = :identifier and chunkIndex >= :start and chunkIndex <= :end;",
                        consistency);
    }

    /**
     * @param id the {@link IRI} of a binary to read
     * @param first which byte to begin reading on
     * @param last which byte to end reading on
     * @return An {@link InputStream} of bytes as requested. The {@code skip} method of this {@code InputStream} is
     *         guaranteed to skip as many bytes as asked.
     *
     * @see BinaryReadQuery#retrieve(IRI, BoundStatement)
     */
    public InputStream execute(final IRI id, final int first, final int last) {
        final BoundStatement bound = preparedStatement().bind()
                        .set("identifier", id, IRI.class)
                        .setInt("start", first)
                        .setInt("end", last);
        return retrieve(id, bound);
    }
}
