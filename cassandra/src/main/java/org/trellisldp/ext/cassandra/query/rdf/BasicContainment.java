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

import static java.util.Collections.unmodifiableSet;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.trellisldp.api.RDFFactory;
import org.trellisldp.ext.cassandra.AsyncResultSetUtils;
import org.trellisldp.ext.cassandra.MutableReadConsistency;
import org.trellisldp.vocabulary.LDP;

/**
 * A query to retrieve basic containment information from a materialized view or index table.
 */
@ApplicationScoped
public class BasicContainment extends ResourceQuery {

    private static final RDF rdfFactory = RDFFactory.getInstance();
    private static final Set<IRI> containerTypes;

    static {
        final Set<IRI> types = new HashSet<>();
        types.add(LDP.Container);
        types.add(LDP.BasicContainer);
        types.add(LDP.DirectContainer);
        types.add(LDP.IndirectContainer);
        containerTypes = unmodifiableSet(types);
    }

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public BasicContainment() {
        super();
    }

    /**
     * A class to query basic containment data.
     * @param session the cassandra session
     * @param consistency the consistency level
     */
    @Inject
    public BasicContainment(final CqlSession session, @MutableReadConsistency final ConsistencyLevel consistency) {
        super(session, "SELECT identifier AS contained, interactionModel AS type FROM " + BASIC_CONTAINMENT_TABLENAME
                        + " WHERE container = :container ;", consistency);
    }

    /**
     * @param id the {@link IRI} of the container
     * @return a {@link ResultSet} of the resources contained in {@code id}
     */
    public CompletionStage<Stream<Quad>> execute(final IRI id) {
        return preparedStatementAsync().thenApply(stmt -> stmt.bind().set("container", id, IRI.class))
            .thenCompose(session::executeAsync)
            .thenApply(AsyncResultSetUtils::stream)
            .thenApply(rows -> rows.map(this::getContained))
            .thenApply(rows -> rows.map(con -> containmentQuad(id, con)));
    }

    private IRI getContained(final Row r) {
        final IRI contained = r.get("contained", IRI.class);
        final IRI type = r.get("type", IRI.class);
        return adjustIdentifier(contained, type);
    }

    private static Quad containmentQuad(final IRI id, final IRI con) {
        return rdfFactory.createQuad(LDP.PreferContainment, adjustIdentifier(id, LDP.BasicContainer),
                LDP.contains, con);
    }

    private static IRI adjustIdentifier(final IRI identifier, final IRI type) {
        if (containerTypes.contains(type) && !identifier.getIRIString().endsWith("/")) {
            return rdfFactory.createIRI(identifier.getIRIString() + "/");
        }
        return identifier;
    }
}
