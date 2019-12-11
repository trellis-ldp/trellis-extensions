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
package org.trellisldp.ext.cassandra;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.trellisldp.api.Metadata.builder;

import java.time.Instant;
import java.util.SortedSet;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.trellisldp.api.Metadata;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

@EnabledIfSystemProperty(named = "trellis.test.cassandra", matches = "true")
class CassandraMementoServiceIT extends CassandraServiceIT {

    @Test
    void mementos() {
        final IRI id = createIRI("http://example.com/testing/" + randomUUID());
        final IRI relation = createIRI("http://example.com/testing/" + randomUUID());
        @SuppressWarnings("resource")
        final Dataset quads = rdfFactory.createDataset();
        final Quad quad = rdfFactory.createQuad(Trellis.PreferUserManaged, id, DC.relation, relation);
        quads.add(quad);

        // build resource
        final Metadata meta = builder(id).interactionModel(LDP.RDFSource).build();
        connection.resourceService.create(meta, quads).toCompletableFuture().join();
        connection.mementoService.put(connection.resourceService, id).toCompletableFuture().join();

        SortedSet<Instant> mementos = connection.mementoService.mementos(id).toCompletableFuture().join();
        assertEquals(1, mementos.size());

        assertEquals(id, connection.mementoService.get(id, mementos.first()).toCompletableFuture().join()
                .getIdentifier());
        waitTwoSeconds();

        // again
        connection.resourceService.replace(meta, quads).toCompletableFuture().join();
        connection.mementoService.put(connection.resourceService, id).toCompletableFuture().join();

        mementos = connection.mementoService.mementos(id).toCompletableFuture().join();
        assertEquals(2, mementos.size());

        mementos.forEach(time ->
            assertEquals(id, connection.mementoService.get(id, time).toCompletableFuture().join()
                .getIdentifier()));

    }

    @Test
    void testNoArgCtor() {
        assertDoesNotThrow(() -> new CassandraMementoService());
    }
}
