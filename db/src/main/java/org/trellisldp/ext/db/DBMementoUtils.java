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

import static java.time.Instant.ofEpochSecond;
import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.apache.commons.rdf.api.IRI;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.slf4j.Logger;

public class DBMementoUtils {

    private static final Logger LOGGER = getLogger(DBMementoUtils.class);
    private final Jdbi jdbi;

    /**
     * Create a new DBMementoUtils object.
     * @param ds the DataSource object
     */
    public DBMementoUtils(final DataSource ds) {
        this(Jdbi.create(ds));
    }

    /**
     * Create a new DBMementoUtils object.
     * @param jdbi the JDBI object
     */
    public DBMementoUtils(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /**
     * Get a list of all mementos for the resource.
     * @param identifier the resource identifier
     * @return a sorted set of mementos
     */
    public SortedSet<Instant> mementos(final IRI identifier) {
        final SortedSet<Instant> instants = new TreeSet<>();
        jdbi.useHandle(handle -> handle
                .select("SELECT moment FROM memento WHERE subject = ?")
                .bind(0, identifier.getIRIString())
                .mapTo(Long.class)
                .forEach(moment -> instants.add(ofEpochSecond(moment))));
        return instants;
    }

    /**
     * Get the location of a memento for the resource at the given moment.
     * @param identifier the resource identifier
     * @param instant the moment in time
     * @return a location for the appropriate memento at that moment in time, if one exists
     */
    public Optional<Instant> get(final IRI identifier, final Instant instant) {
        return jdbi.withHandle(handle -> handle
                .select("SELECT moment FROM memento WHERE subject = ? AND moment <= ? ORDER BY moment DESC")
                .bind(0, identifier.getIRIString())
                .bind(1, instant.getEpochSecond())
                .mapTo(Long.class)
                .findFirst()).map(Instant::ofEpochSecond);
    }

    /**
     * Set the location of a memento for the resource at the given moment.
     * @param identifier the resource identifier
     * @param instant the moment in time
     */
    public void put(final IRI identifier, final Instant instant) {
        try {
            jdbi.useHandle(handle ->
                    handle.execute("INSERT INTO memento (subject, moment) VALUES (?, ?)",
                        identifier.getIRIString(), instant.getEpochSecond()));
        } catch (final UnableToExecuteStatementException ex) {
            LOGGER.debug("Unable to insert memento value: {}", ex.getMessage());
        }
    }
}
