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

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.trellisldp.api.RDFUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.RDFUtils.getInstance;

import com.google.common.io.Resources;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.api.NoopEventService;
import org.trellisldp.api.NoopMementoService;
import org.trellisldp.api.ResourceService;
import org.trellisldp.id.UUIDGenerator;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * ResourceService tests.
 */
public class DBResourceTest {

    private static final RDF rdf = getInstance();

    private static final IdentifierService idService = new UUIDGenerator();

    private static EmbeddedPostgres pg = null;

    private static ResourceService svc = null;

    static {
        try {
            pg = EmbeddedPostgres.builder()
                .setDataDirectory("./build/pgdata-" + new RandomStringGenerator.Builder()
                .withinRange('a', 'z').build().generate(10)).start();
            // SET UP DATABASE
            Jdbi.create(pg.getPostgresDatabase()).useHandle(handle ->
                handle.execute(Resources.toString(getResource("create.pgsql"), UTF_8)));

            svc = new DBResourceService(pg.getPostgresDatabase(), idService,
                new NoopMementoService(), new NoopEventService());

        } catch (final IOException ex) {

        }
    }

    @Test
    public void getRoot() {
        assertTrue(DBResource.findResource(pg.getPostgresDatabase(), rdf.createIRI(TRELLIS_DATA_PREFIX)).isPresent());
    }

    @Test
    public void getNonExistent() {
        assertFalse(DBResource.findResource(pg.getPostgresDatabase(), rdf.createIRI(TRELLIS_DATA_PREFIX + "other"))
                .isPresent());
    }

    @Test
    public void getServerQuads() {
        final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);
        DBResource.findResource(pg.getPostgresDatabase(), root).ifPresent(res -> {
            assertTrue(res.stream(Trellis.PreferServerManaged).anyMatch(triple ->
                        triple.getSubject().equals(root) && triple.getObject().equals(LDP.BasicContainer)));
        });
    }
}
