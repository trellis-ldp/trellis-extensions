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

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.ext.cassandra.query.binary.Read;
import org.trellisldp.ext.cassandra.query.binary.ReadRange;

/**
 * Simple implementation of {@link Binary} that pulls content from Cassandra on demand.
 *
 */
public class CassandraBinary implements Binary {

    private static final Logger LOGGER = getLogger(CassandraBinary.class);

    private final IRI id;

    private final int chunkLength;

    private final Read read;

    private final ReadRange readRange;

    /**
     * @param id identifier for this {@link Binary}
     * @param read a {@link Read} query to use
     * @param readRange a {@link ReadRange} query to use
     * @param chunkLength the length of chunk to use reading bits from Cassandra
     */
    public CassandraBinary(final IRI id, final Read read, final ReadRange readRange, final int chunkLength) {
        this.id = id;
        this.read = read;
        this.readRange = readRange;
        if (chunkLength < 1) throw new IllegalArgumentException("Chunk length < 1!");
        this.chunkLength = chunkLength;
    }

    @Override
    public InputStream getContent() {
        return read.execute(id).toCompletableFuture().join();
    }

    @Override
    public InputStream getContent(final int from, final int to) {
        final int firstChunk = from / chunkLength;
        final int lastChunk = to / chunkLength;
        final int chunkStreamStart = from % chunkLength;
        final int rangeSize = to - from + 1; // +1 because range is inclusive
        final InputStream retrieve = readRange.execute(id, firstChunk, lastChunk).toCompletableFuture().join();
        // skip to fulfill lower end of range
        try {
            final long skipped = retrieve.skip(chunkStreamStart);
            LOGGER.debug("Skipped {} bytes", skipped);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } // we needn't check the result; see BinaryReadQuery#retrieve
        return new BoundedInputStream(retrieve, rangeSize); // apply limit for upper end of range
    }
}
