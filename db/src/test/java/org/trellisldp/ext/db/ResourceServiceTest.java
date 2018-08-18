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

import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.trellisldp.api.RDFUtils.getInstance;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.trellisldp.api.NoopEventService;
import org.trellisldp.api.NoopMementoService;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.Session;
import org.trellisldp.id.UUIDGenerator;
import org.trellisldp.test.AbstractResourceServiceTests;

/**
 * ResourceService tests.
 */
@DisabledOnOs(WINDOWS)
public class ResourceServiceTest extends AbstractResourceServiceTests {

    private static final RDF rdf = getInstance();
    private static final EmbeddedPostgres pg = DBTestUtils.setupDatabase("build");

    private final ResourceService svc = new DBResourceService(pg.getPostgresDatabase(), new UUIDGenerator(),
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
