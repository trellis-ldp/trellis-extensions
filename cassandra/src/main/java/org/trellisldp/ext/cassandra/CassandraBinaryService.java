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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.api.*;
import org.trellisldp.ext.cassandra.query.binary.*;


/**
 * Implements {@link BinaryService} by chunking binary data across Cassandra.
 *
 */
public class CassandraBinaryService implements BinaryService {

    private static final Logger log = getLogger(CassandraBinaryService.class);

    @SuppressWarnings("boxing")
    private static final CompletableFuture<Long> DONE = completedFuture(-1L);

    // package-private for testing
    static final String CASSANDRA_CHUNK_HEADER_NAME = "Cassandra-Chunk-Size";

    private final IdentifierService idService;

    private final int defaultChunkLength;

    private final GetChunkSize get;

    private final Insert insert;

    private final Delete delete;

    private final Read read;

    private final ReadRange readRange;

    private Executor readBinaryWorkers = Executors.newCachedThreadPool();

    /**
     * @param idService {@link IdentifierService} to use for binaries
     * @param chunkLength the maximum size of any chunk in this service
     * @param get a {@link GetChunkSize} query to use
     * @param insert a {@link Insert} query to use
     * @param delete a {@link Delete} query to use
     * @param read a {@link Read} query to use
     * @param readRange a {@link ReadRange} query to use
     */
    @Inject
    public CassandraBinaryService(final IdentifierService idService, @DefaultChunkSize final int chunkLength,
            final GetChunkSize get, final Insert insert, final Delete delete, final Read read,
            final ReadRange readRange) {
        this.idService = idService;
        this.defaultChunkLength = chunkLength;
        log.info("Using configured default chunk length: {}", chunkLength);
        this.get = get;
        this.insert = insert;
        this.delete = delete;
        this.read = read;
        this.readRange = readRange;
    }

    @Override
    public CompletionStage<Binary> get(final IRI id) {
        log.debug("Retrieving binary content from: {}", id);
        return get.execute(id)
                        .thenApplyAsync(r -> new CassandraBinary(id, read, readRange, r.getInt("chunkSize")),
                        readBinaryWorkers);
    }

    @Override
    public CompletionStage<Void> setContent(final BinaryMetadata meta, final InputStream stream) {
        log.debug("Recording binary content under: {}", meta.getIdentifier());
        final int chunkSize;
        if (meta.getHints() == null) chunkSize = defaultChunkLength;
        else {
            final List<String> headers = meta.getHints().get(CASSANDRA_CHUNK_HEADER_NAME);
            if (headers == null) chunkSize = defaultChunkLength;
            else if (headers.size() > 1)
                throw new RuntimeTrellisException("Too many " + CASSANDRA_CHUNK_HEADER_NAME + " headers!");
            else chunkSize = Integer.parseInt(headers.get(0));
        }
        return setChunk(meta, stream, new AtomicInteger(), chunkSize)
                        .thenAccept(l -> log.debug("Recorded binary content under: {}", meta.getIdentifier()));
    }

    @SuppressWarnings("resource")
    private CompletionStage<Long> setChunk(final BinaryMetadata meta, final InputStream data,
            final AtomicInteger chunkIndex, final int chunkLength) {
        final IRI id = meta.getIdentifier();
        log.debug("Recording chunk {} of binary content under: {}", chunkIndex.get(), id);

        try (final NoopCloseCountingInputStream countingChunk = new NoopCloseCountingInputStream(
                        new BoundedInputStream(data, chunkLength))) {
            return insert.execute(id, chunkLength, chunkIndex.getAndIncrement(), countingChunk)
                            .thenComposeAsync(future -> countingChunk.getByteCount() == chunkLength
                                            ? setChunk(meta, data, chunkIndex, chunkLength)
                                            : DONE, insert);
        }
    }

    @Override
    public CompletionStage<Void> purgeContent(final IRI identifier) {
        return delete.execute(identifier);
    }

    @Override
    public String generateIdentifier() {
        return idService.getSupplier().get();
    }
}
