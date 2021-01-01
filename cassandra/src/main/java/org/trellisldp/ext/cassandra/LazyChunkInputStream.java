/*
 * Copyright (c) 2021 Aaron Coburn and individual contributors
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

import static java.util.Objects.requireNonNull;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.io.InputStream;

/**
 * An {@link InputStream} backed by a Cassandra query to retrieve one binary chunk.
 *
 * <p>Not thread-safe!
 *
 * @see InputStreamCodec
 */
public class LazyChunkInputStream extends LazyFilterInputStream {

    private final CqlSession session;

    private final BoundStatement query;

    /**
     * @param session The Cassandra session to use
     * @param query the CQL query to use
     */
    public LazyChunkInputStream(final CqlSession session, final BoundStatement query) {
        this.session = session;
        this.query = query;
    }

    @Override
    protected InputStream initialize() {
        final Row row = requireNonNull(session.execute(query).one(), "Missing binary chunk!");
        return row.get("chunk", InputStream.class);
    }
}
