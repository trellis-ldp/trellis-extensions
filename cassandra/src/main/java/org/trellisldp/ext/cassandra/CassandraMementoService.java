/*
 * Copyright (c) 2017 - 2020 Aaron Coburn and individual contributors
 *
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

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Collections.emptySortedSet;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toCollection;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.uuid.Uuids;

import java.time.Instant;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.api.MementoService;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;
import org.trellisldp.ext.cassandra.query.rdf.GetFirstMemento;
import org.trellisldp.ext.cassandra.query.rdf.GetMemento;
import org.trellisldp.ext.cassandra.query.rdf.Mementoize;
import org.trellisldp.ext.cassandra.query.rdf.Mementos;

/**
 * A {@link MementoService} that stores Mementos in a Cassandra table.
 */
@ApplicationScoped
public class CassandraMementoService implements MementoService, CassandraBuildingService {

    /** The configuration key for enabling/disabling memento handling. */
    public static final String CONFIG_CASSANDRA_VERSIONING = "trellis.cassandra.versioning";

    private static final Logger LOGGER = getLogger(CassandraMementoService.class);

    private final Mementos mementos;

    private final Mementoize mementoize;

    private final GetMemento getMemento;

    private final GetFirstMemento getFirstMemento;

    private final boolean enabled;

    CassandraMementoService() {
        this(null, null, null, null);
    }

    @Inject
    CassandraMementoService(final Mementos mementos, final Mementoize mementoize, final GetMemento getMemento,
                    final GetFirstMemento getFirstMemento) {
        this.mementos = mementos;
        this.mementoize = mementoize;
        this.getMemento = getMemento;
        this.getFirstMemento = getFirstMemento;
        this.enabled = getConfig().getOptionalValue(CONFIG_CASSANDRA_VERSIONING, Boolean.class)
            .orElse(Boolean.TRUE);
    }

    @Override
    public CompletionStage<Void> put(final Resource r) {

        if (enabled) {
            final Metadata metadata = Metadata.builder(r).build();
            final Dataset data = r.dataset();
            final Instant modified = r.getModified();
            final UUID creation = Uuids.timeBased();

            LOGGER.debug("Writing Memento for {} at time: {}", metadata.getIdentifier(), modified);
            return mementoize.execute(metadata, modified, data, creation);
        }
        return completedFuture(null);
    }

    //@formatter:off
    @Override
    public CompletionStage<SortedSet<Instant>> mementos(final IRI id) {
        if (enabled) {
            return mementos.execute(id)
                            .thenApply(AsyncResultSetUtils::stream)
                            .thenApply(results -> results
                                            .map(row -> row.get("modified", Instant.class))
                                            .map(time -> time.truncatedTo(SECONDS))
                                            .collect(toCollection(TreeSet::new)));
        }
        return completedFuture(emptySortedSet());
    }
    //@formatter:on

    @Override
    public CompletionStage<Resource> get(final IRI id, final Instant time) {
        if (enabled) {
            LOGGER.debug("Retrieving Memento for: {} at {}", id, time);
            return getMemento.execute(id, time)
                            .thenCompose(result -> result.remaining() > 0
                                            ? getFirstMemento.execute(id)
                                            : completedFuture(result))
                            .thenApply(AsyncResultSet::one)
                            .thenApply(row -> parse(row, LOGGER, id));
        }
        return completedFuture(MISSING_RESOURCE);
    }
}
