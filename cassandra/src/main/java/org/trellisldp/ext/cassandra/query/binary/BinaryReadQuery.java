/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
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
package org.trellisldp.ext.cassandra.query.binary;

import static java.util.stream.StreamSupport.stream;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.IRI;
import org.trellisldp.api.TrellisRuntimeException;
import org.trellisldp.ext.cassandra.LazyChunkInputStream;

/**
 * A query that reads binary data from Cassandra.
 */
public abstract class BinaryReadQuery extends BinaryQuery {

    private static final String READ_CHUNK_QUERY = "SELECT chunk FROM " + BINARY_TABLENAME
                    + " WHERE identifier = :identifier and chunkIndex = :chunkIndex;";

    private final PreparedStatement readChunkStatement;

    BinaryReadQuery() {
        super();
        this.readChunkStatement = null;
    }

    BinaryReadQuery(final CqlSession session, final String queryString, final ConsistencyLevel consistency) {
        super(session, queryString, consistency);
        this.readChunkStatement = session.prepare(READ_CHUNK_QUERY);
    }

    //@formatter:off
    /**
     * @param id an {@link IRI} for a binary
     * @param statement a CQL query that retrieves the chunk indexes of chunks for {@code id}
     * @return An {@link InputStream} of bytes as requested. The {@code skip} method of this {@code InputStream} is
     *         guaranteed to skip as many bytes as asked.
     */
    protected InputStream retrieve(final IRI id, final BoundStatement statement) {
        return stream(executeSyncRead(statement).spliterator(), false)
                    .mapToInt(r -> r.getInt("chunkIndex"))
                    .mapToObj(chunkIndex -> readChunkStatement.bind()
                                        .setInt("chunkIndex", chunkIndex)
                                        .set("identifier", id, IRI.class))
                    .<InputStream>map(s -> new LazyChunkInputStream(session, s))
                    .reduce(SequenceInputStream::new) // chunks now in one large stream
                    .orElseThrow(() -> new TrellisRuntimeException("Binary not found under IRI: " + id.getIRIString()));
    }
    //@formatter:on

    /**
     * An {@link InputStream} that sequentially streams two underlying streams. {@link #skip(long)} calls {@code skip}
     * on the underlying streams before defaulting to using {@link IOUtils#skip(InputStream, long)}, and
     * {@link #read(byte[], int, int)} also calls {@code read(byte[], int, int)} on the underlying streams. This is
     * useful in particular with {@link ByteArrayInputStream}s, which have very fast
     * {@link ByteArrayInputStream#skip(long)} and {@link ByteArrayInputStream#read(byte[], int, int)} implementations.
     *
     */
    static class SequenceInputStream extends InputStream {

        private final InputStream s1;
        private final InputStream s2;

        /**
         * Changes from {@link #s1} to {@link #s2} to {@code null} via {@link #next()}.
         */
        private InputStream current;

        public SequenceInputStream(final InputStream s1, final InputStream s2) {
            this.current = (this.s1 = s1);
            this.s2 = s2;
        }

        @Override
        public long skip(final long n) throws IOException {
            if (current == null || n <= 0) return 0;
            long toSkip = n;
            toSkip -= current.skip(toSkip);
            if (toSkip > 0) { // we ran out of bytes to skip from current
                toSkip -= IOUtils.skip(current, toSkip); // read them instead
                if (toSkip > 0) {
                    next();
                    toSkip -= skip(toSkip);
                }
            }
            return n - toSkip;
        }

        @Override
        public int read() throws IOException {
            if (current == null) return -1;
            final int take = current.read();
            if (take == -1) {
                next();
                return read();
            }
            return take;
        }

        @Override
        public int read(final byte[] b, final int offset, final int length) throws IOException {
            if (offset < 0 || length < 0 || length > b.length - offset) throw new IndexOutOfBoundsException();
            if (length == 0) return 0;
            if (current == null) return -1;
            final int read = current.read(b, offset, length);
            if (read <= 0) { // we couldn't get any bytes from current
                next();
                return read(b, offset, length);
            }
            return read;
        }

        private void next() throws IOException {
            if (current != null) current.close();
            current = current == s1 ? s2 : null;
        }
    }
}
