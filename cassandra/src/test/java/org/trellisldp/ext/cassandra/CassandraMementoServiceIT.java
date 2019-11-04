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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.trellisldp.api.Metadata.builder;

import java.time.Instant;
import java.util.SortedSet;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.trellisldp.api.Metadata;

@Disabled("These tests require a running Cassandra node")
class CassandraMementoServiceIT extends CassandraServiceIT {

    @Test
    void mementos() {
        final IRI id = createIRI("http://example.com/id/foo2");
        final IRI ixnModel = createIRI("http://example.com/ixnModel2");
        @SuppressWarnings("resource")
        final Dataset quads = rdfFactory.createDataset();
        final Quad quad = rdfFactory.createQuad(id, ixnModel, id, ixnModel);
        quads.add(quad);

        // build resource
        final Metadata meta = builder(id).interactionModel(ixnModel).build();
        connection.resourceService.create(meta, quads).toCompletableFuture().join();
        connection.mementoService.put(connection.resourceService, id).toCompletableFuture().join();

        SortedSet<Instant> mementos = connection.mementoService.mementos(id).toCompletableFuture().join();
        assertEquals(1, mementos.size());
        waitTwoSeconds();

        // again
        connection.resourceService.replace(meta, quads).toCompletableFuture().join();
        connection.mementoService.put(connection.resourceService, id).toCompletableFuture().join();

        mementos = connection.mementoService.mementos(id).toCompletableFuture().join();
        assertEquals(2, mementos.size());
    }
}
