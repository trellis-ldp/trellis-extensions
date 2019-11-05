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

import static org.trellisldp.vocabulary.LDP.PreferContainment;
import static org.trellisldp.vocabulary.LDP.contains;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.trellisldp.api.TrellisUtils;
import org.trellisldp.ext.cassandra.AsyncResultSetUtils;
import org.trellisldp.ext.cassandra.MutableReadConsistency;

/**
 * A query to retrieve basic containment information from a materialized view or index table.
 */
public class BasicContainment extends ResourceQuery {

    private static final RDF rdfFactory = TrellisUtils.getInstance();

    /**
     * A class to query basic containment data.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public BasicContainment(final CqlSession session, @MutableReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT identifier AS contained FROM " + BASIC_CONTAINMENT_TABLENAME
                        + " WHERE container = :container ;", consistency);
    }

    /**
     * @param id the {@link IRI} of the container
     * @return a {@link ResultSet} of the resources contained in {@code id}
     */
    public CompletionStage<Stream<Quad>> execute(final IRI id) {
        final BoundStatement query = preparedStatement().bind().set("container", id, IRI.class);
        return executeRead(query)
                        .thenApply(AsyncResultSetUtils::stream)
                        .thenApply(rows -> rows.map(this::getContained))
                        .thenApply(rows -> rows.map(con -> containmentQuad(id, con)));
    }

    private IRI getContained(final Row r) {
        return r.get("contained", IRI.class);
    }

    private static Quad containmentQuad(final IRI id, final IRI con) {
        return rdfFactory.createQuad(PreferContainment, id, contains, con);
    }
}
