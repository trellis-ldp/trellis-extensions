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

import static java.util.Spliterators.spliteratorUnknownSize;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Includes a simple {@link Spliterator} backed by an {@link AsyncResultSet}.
 *
 * <p>Not thread-safe!
 */
public final class AsyncResultSetUtils implements Spliterator<Row> {

    private AsyncResultSet results;

    private Iterator<Row> currentResults;

    private static final int CHARACTERISTICS = Spliterator.ORDERED | Spliterator.NONNULL;

    /**
     * @param results an {@link AsyncResultSet}
     * @return a {@link Stream} of {@link Row}s
     */
    public static Stream<Row> stream(final AsyncResultSet results) {
        return StreamSupport.stream(new AsyncResultSetUtils(results), false);
    }

    private AsyncResultSetUtils(final AsyncResultSet r) {
        this.results = r;
        this.currentResults = r.currentPage().iterator();
    }

    @Override
    public boolean tryAdvance(final Consumer<? super Row> action) {
        if (currentResults.hasNext()) {
            action.accept(currentResults.next());
            return true;
        }
        if (results.hasMorePages()) {
            nextPage();
            return tryAdvance(action);
        }
        return false;
    }

    @Override
    public Spliterator<Row> trySplit() {
        if (results.hasMorePages()) {
            final Iterator<Row> splitResults = currentResults;
            nextPage();
            return spliteratorUnknownSize(splitResults, 0);
        }
        return null;
    }

    /**
     * Blocks until next page of results is available.
     */
    private void nextPage() {
        if (currentResults.hasNext()) return;
        results = results.fetchNextPage().toCompletableFuture().join();
        currentResults = results.currentPage().iterator();
    }

    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return CHARACTERISTICS;
    }
}
