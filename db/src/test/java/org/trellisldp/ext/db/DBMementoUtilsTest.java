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
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.TrellisUtils.getInstance;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.SortedSet;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.slf4j.Logger;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

@DisabledOnOs(WINDOWS)
public class DBMementoUtilsTest {
    private static final Logger LOGGER = getLogger(DBMementoUtils.class);
    private static final RDF rdf = getInstance();

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
    public void testMementoUtils() {
        final DBMementoUtils util = new DBMementoUtils(pg.getPostgresDatabase());

        final Instant time = now();
        final IRI identifier = rdf.createIRI("trellis:data/resource");
        util.put(identifier, time);
        util.put(identifier, time.plusSeconds(2L));
        util.put(identifier, time.plusSeconds(4L));

        final SortedSet<Instant> mementos = util.mementos(identifier);
        assertTrue(mementos.contains(time.truncatedTo(SECONDS)));
        assertTrue(mementos.contains(time.plusSeconds(2L).truncatedTo(SECONDS)));
        assertTrue(mementos.contains(time.plusSeconds(4L).truncatedTo(SECONDS)));
    }

    @Test
    public void testMementoUtilsPut() {
        final DBMementoUtils util = new DBMementoUtils(pg.getPostgresDatabase());

        final Instant time = now();
        final IRI identifier = rdf.createIRI("trellis:data/resource2");
        util.put(identifier, time);
        util.put(identifier, time.plusSeconds(2L));
        util.put(identifier, time.plusSeconds(2L));
        util.put(identifier, time.plusSeconds(4L));
        util.put(identifier, time.plusSeconds(4L));

        final SortedSet<Instant> mementos = util.mementos(identifier);
        assertEquals(3L, mementos.size());
        assertTrue(mementos.contains(time.truncatedTo(SECONDS)));
        assertTrue(mementos.contains(time.plusSeconds(2L).truncatedTo(SECONDS)));
        assertTrue(mementos.contains(time.plusSeconds(4L).truncatedTo(SECONDS)));
    }

    @Test
    public void testMementoUtilsGet() {
        final DBMementoUtils util = new DBMementoUtils(pg.getPostgresDatabase());

        final Instant time = now();
        final IRI identifier = rdf.createIRI("trellis:data/other");
        util.put(identifier, time);
        util.put(identifier, time.plusSeconds(2L));
        util.put(identifier, time.plusSeconds(4L));

        final SortedSet<Instant> mementos = util.mementos(identifier);
        assertEquals(of(time.truncatedTo(SECONDS)), util.get(identifier, time));
        assertEquals(of(time.truncatedTo(SECONDS)), util.get(identifier, time.plusSeconds(1L)));
        assertEquals(of(time.plusSeconds(2L).truncatedTo(SECONDS)), util.get(identifier, time.plusSeconds(2L)));
        assertEquals(of(time.plusSeconds(2L).truncatedTo(SECONDS)), util.get(identifier, time.plusSeconds(3L)));
        assertEquals(of(time.plusSeconds(4L).truncatedTo(SECONDS)), util.get(identifier, time.plusSeconds(4L)));
    }
}

