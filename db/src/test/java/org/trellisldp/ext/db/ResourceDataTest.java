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
package org.trellisldp.ext.db;

import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.TrellisUtils.getInstance;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import java.time.Instant;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.trellisldp.api.Resource;

/**
 * ResourceService tests.
 */
@DisabledOnOs(WINDOWS)
public class ResourceDataTest {

    private static final RDF rdf = getInstance();
    private static final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);
    private static final EmbeddedPostgres pg = DBTestUtils.setupDatabase("build");

    @Test
    public void testTimestampOnRootIsRecent() {
        final Instant time = now().minusSeconds(1L);
        final Resource res = DBResource.findResource(pg.getPostgresDatabase(), root).toCompletableFuture().join();
        assertEquals(root, res.getIdentifier());
        assertTrue(res.getModified().isAfter(time));
    }
}
