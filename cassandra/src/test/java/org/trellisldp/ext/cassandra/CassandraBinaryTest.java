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

import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.ext.cassandra.query.binary.Read;
import org.trellisldp.ext.cassandra.query.binary.ReadRange;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("resource")
class CassandraBinaryTest {

    private final RDF factory = new SimpleRDF();

    private int testChunkSize = 10;

    private final IRI testId = factory.createIRI("urn:test");

    @Mock
    private Read mockRead;

    @Mock
    private ReadRange mockReadRange;

    @Mock
    private InputStream mockInputStream1;

    @Mock
    private InputStream mockInputStream2;

    @Test
    @SuppressWarnings("unused")
    void badChunkLength() {
        try {
            new CassandraBinary(testId, mockRead, mockReadRange, -1);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException, "Wrong exception type!");
        }
        try {
            new CassandraBinary(testId, mockRead, mockReadRange, 0);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException, "Wrong exception type!");
        }
    }

    @Test
    void noContent() {
        final CassandraBinary testCassandraBinary = new CassandraBinary(testId, mockRead, mockReadRange, testChunkSize);

        try {
            testCassandraBinary.getContent();
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeTrellisException, "Wrong exception type!");
        }
    }

    @Test
    void someContent() {
        when(mockRead.execute(any())).thenReturn(mockInputStream1);
        final CassandraBinary testCassandraBinary = new CassandraBinary(testId, mockRead, mockReadRange, testChunkSize);
        final InputStream result = testCassandraBinary.getContent();
        assertSame(mockInputStream1, result, "Got wrong InputStream!");
    }

    @Test
    void aBitOfContent() throws IOException {
        final byte[] bytes = new byte[] { 1, 2, 3, 4, 5, 6, -1 };
        final InputStream testInputStream = new ByteArrayInputStream(bytes);
        when(mockReadRange.execute(any(), anyInt(), anyInt())).thenReturn(testInputStream);
        final CassandraBinary testCassandraBinary = new CassandraBinary(testId, mockRead, mockReadRange, testChunkSize);
        final InputStream content = testCassandraBinary.getContent(0, 10);
        final byte[] result = new byte[3];

        content.read(result);
        assertArrayEquals(copyOf(bytes, 3), result, "Wrong bytes!");
        content.read(result);
        assertArrayEquals(copyOfRange(bytes, 3, 6), result, "Wrong bytes!");
    }
}
