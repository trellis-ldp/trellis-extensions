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

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toCollection;
import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.uuid.Uuids;

import java.time.Instant;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.MementoService;
import org.trellisldp.api.Resource;
import org.trellisldp.ext.cassandra.query.rdf.GetFirstMemento;
import org.trellisldp.ext.cassandra.query.rdf.GetMemento;
import org.trellisldp.ext.cassandra.query.rdf.Mementoize;
import org.trellisldp.ext.cassandra.query.rdf.Mementos;


/**
 * A {@link MementoService} that stores Mementos in a Cassandra table.
 *
 */
public class CassandraMementoService extends CassandraBuildingService implements MementoService {

    private static final Logger LOGGER = getLogger(CassandraMementoService.class);

    private final Mementos mementos;

    private final Mementoize mementoize;

    private final GetMemento getMemento;

    private final GetFirstMemento getFirstMemento;

    @Inject
    CassandraMementoService(final Mementos mementos, final Mementoize mementoize, final GetMemento getMemento,
                    final GetFirstMemento getFirstMemento) {
        this.mementos = mementos;
        this.mementoize = mementoize;
        this.getMemento = getMemento;
        this.getFirstMemento = getFirstMemento;
    }

    @Override
    public CompletionStage<Void> put(final Resource r) {

        final IRI id = r.getIdentifier();
        final IRI ixnModel = r.getInteractionModel();
        final IRI container = r.getContainer().orElse(null);
        final Optional<BinaryMetadata> binary = r.getBinaryMetadata();
        final IRI binaryIdentifier = binary.map(BinaryMetadata::getIdentifier).orElse(null);
        final String mimeType = binary.flatMap(BinaryMetadata::getMimeType).orElse(null);
        final Dataset data = r.dataset();
        final Instant modified = r.getModified();
        final UUID creation = Uuids.timeBased();

        LOGGER.debug("Writing Memento for {} at time: {}", id, modified);
        return mementoize.execute(ixnModel, mimeType, container, data, modified, binaryIdentifier, creation, id);
    }

    //@formatter:off
    @Override
    public CompletionStage<SortedSet<Instant>> mementos(final IRI id) {
        return mementos.execute(id)
                        .thenApply(AsyncResultSetUtils::stream)
                        .thenApply(results -> results
                                        .map(row -> row.get("modified", Instant.class))
                                        .map(time -> time.truncatedTo(SECONDS))
                                        .collect(toCollection(TreeSet::new)));
    }
    //@formatter:on

    @Override
    public CompletionStage<Resource> get(final IRI id, final Instant time) {
        LOGGER.debug("Retrieving Memento for: {} at {}", id, time);
        return getMemento.execute(id, time)
                        .thenCompose(result -> result.remaining() > 0
                                        ? getFirstMemento.execute(id)
                                        : completedFuture(result))
                        .thenApply(AsyncResultSet::one)
                        .thenApply(row -> parse(row, LOGGER, id));
    }
}
