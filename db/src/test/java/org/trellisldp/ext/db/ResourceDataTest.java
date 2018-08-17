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

import static java.io.File.separator;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.RDFUtils.getInstance;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.slf4j.Logger;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.api.Resource;
import org.trellisldp.id.UUIDGenerator;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * ResourceService tests.
 */
@DisabledOnOs(WINDOWS)
public class ResourceDataTest {

    private static final Logger LOGGER = getLogger(DBResourceTest.class);
    private static final RDF rdf = getInstance();

    private static final IdentifierService idService = new UUIDGenerator();

    private static final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);

    private static EmbeddedPostgres pg = null;

    static {
        try {
            pg = EmbeddedPostgres.builder()
                .setDataDirectory("build" + separator + "pgdata-" + new RandomStringGenerator
                            .Builder().withinRange('a', 'z').build().generate(10)).start();

            // Set up database migrations
            try (final Connection c = pg.getPostgresDatabase().getConnection()) {
                final Liquibase liquibase = new Liquibase("migrations.yml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(c));
                final Contexts ctx = null;
                liquibase.update(ctx);
            }
        } catch (final IOException | SQLException | LiquibaseException ex) {
            LOGGER.error("Error setting up tests", ex);
        }
    }

    @Test
    public void testTimestampOnRootIsRecent() {
        final Instant time = now().minusSeconds(1L);
        final Optional<Resource> res = DBResource.findResource(pg.getPostgresDatabase(), root);
        assertTrue(res.isPresent());
        assertTrue(res.get().getModified().isAfter(time));
    }
}
