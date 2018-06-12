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
import static org.trellisldp.api.RDFUtils.getInstance;

import com.google.common.io.Resources;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;

import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.jdbi.v3.core.Jdbi;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.api.NoopEventService;
import org.trellisldp.api.NoopMementoService;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.Session;
import org.trellisldp.id.UUIDGenerator;
import org.trellisldp.test.AbstractResourceServiceTests;

/**
 * ResourceService tests.
 */
public class ResourceServiceTest extends AbstractResourceServiceTests {

    private static final RDF rdf = getInstance();
    private static final IdentifierService idService = new UUIDGenerator();

    private static EmbeddedPostgres pg = null;

    static {
        try {
            pg = EmbeddedPostgres.builder()
                .setDataDirectory("./build/pgdata-" + new RandomStringGenerator.Builder()
                .withinRange('a', 'z').build().generate(10)).start();
            // SET UP DATABASE
            Jdbi.create(pg.getPostgresDatabase()).useHandle(handle ->
                handle.execute(Resources.toString(getResource("create.pgsql"), UTF_8)));
        } catch (final IOException ex) {

        }
    }

    private final ResourceService svc = new DBResourceService(pg.getPostgresDatabase(), idService,
                new NoopMementoService(), new NoopEventService());

    @Override
    public ResourceService getResourceService() {
        return svc;
    }

    @Override
    public Session getSession() {
        return new SimpleSession(rdf.createIRI("user:test"));
    }
}
