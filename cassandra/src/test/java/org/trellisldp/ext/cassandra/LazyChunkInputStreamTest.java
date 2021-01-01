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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LazyChunkInputStreamTest {

    @Mock
    private CqlSession mockSession;

    @Mock
    private BoundStatement mockQuery;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private Row mockRow;

    @Mock
    private InputStream mockInputStream;

    private byte[] b = null;
    private int off = 0;
    private int len = 0;
    private int n = 0;
    private int readlimit = 0;

    @Test
    void badQuery() {
        final RuntimeException e = new RuntimeException("Expected");
        when(mockSession.execute(mockQuery)).thenThrow(e);
        try (LazyChunkInputStream testLazyChunkInputStream = new LazyChunkInputStream(mockSession, mockQuery)) {
            testLazyChunkInputStream.read();
        } catch (Exception e1) {
            assertSame(e, e1, "Didn't get the exception we expected!");
        }
    }

    @Test
    void noData() {
        when(mockSession.execute(mockQuery)).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(null);

        try (LazyChunkInputStream testLazyChunkInputStream = new LazyChunkInputStream(mockSession, mockQuery)) {
            testLazyChunkInputStream.read();
        } catch (Exception e) {
            assertThat("Wrong exception type!", e, instanceOf(NullPointerException.class));
            assertEquals("Missing binary chunk!", e.getMessage(), "Wrong exception message!");
        }
    }

    @Test
    void normalOperation() throws IOException {
        when(mockSession.execute(mockQuery)).thenReturn(mockResultSet);
        when(mockResultSet.one()).thenReturn(mockRow);
        when(mockRow.get("chunk", InputStream.class)).thenReturn(mockInputStream);

        try (LazyChunkInputStream testLazyChunkInputStream = new LazyChunkInputStream(mockSession, mockQuery)) {

            testLazyChunkInputStream.read();
            verify(mockInputStream).read();

            testLazyChunkInputStream.read(b);
            verify(mockInputStream).read(b);

            testLazyChunkInputStream.read(b, off, len);
            verify(mockInputStream).read(b, off, len);

            testLazyChunkInputStream.skip(n);
            verify(mockInputStream).skip(n);

            testLazyChunkInputStream.available();
            verify(mockInputStream).available();

            testLazyChunkInputStream.mark(readlimit);
            verify(mockInputStream).mark(readlimit);

            testLazyChunkInputStream.reset();
            verify(mockInputStream).reset();

            testLazyChunkInputStream.markSupported();
            verify(mockInputStream).markSupported();

        }
        verify(mockInputStream).close();
    }
}
