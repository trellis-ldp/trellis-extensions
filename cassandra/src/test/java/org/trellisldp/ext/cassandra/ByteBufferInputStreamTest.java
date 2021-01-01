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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class ByteBufferInputStreamTest {

    private final byte[] testByteArray = new byte[] { 1, 2, 3, 4, 3, 2, 1 };

    private final ByteBuffer testData = ByteBuffer.wrap(testByteArray);

    private ByteBuffer testData() {
        return testData.asReadOnlyBuffer();
    }

    @Test
    void cantResetBeyondLimit() throws IOException {
        final ByteBufferInputStream stream = new ByteBufferInputStream(testData());
        stream.mark(3);
        stream.read(new byte[8]);
        assertThrows(IOException.class, stream::reset);
    }

    @Test
    void availableWorks() {
        final ByteBufferInputStream stream = new ByteBufferInputStream(testData());
        assertEquals(7, stream.available());
    }

    @Test
    void supportsMark() {
        final ByteBufferInputStream stream = new ByteBufferInputStream(testData());
        assertTrue(stream.markSupported());
    }

    @Test
    void noMarkMeansResetTo0() throws IOException {
        final ByteBufferInputStream stream = new ByteBufferInputStream(testData());
        final byte[] answer = new byte[testByteArray.length];
        stream.read(new byte[3]);
        stream.reset();
        stream.read(answer);
        assertArrayEquals(testByteArray, answer);
    }

    @Test
    void noBytesMeansNoBytes() throws IOException {
        final ByteBufferInputStream stream = new ByteBufferInputStream(ByteBuffer.allocate(0));
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
    }

    @Test
    void skipWorks() throws IOException {
        final ByteBufferInputStream stream = new ByteBufferInputStream(testData());
        final byte[] answer = new byte[testByteArray.length];
        stream.skip(3);
        assertEquals(4, stream.read(answer));
    }

    @Test
    void readWorks() {
        final ByteBufferInputStream stream = new ByteBufferInputStream(testData());
        for (int i : testByteArray)
            assertEquals(i, stream.read());
    }
}
