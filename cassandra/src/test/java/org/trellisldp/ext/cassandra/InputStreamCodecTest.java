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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.trellisldp.ext.cassandra.InputStreamCodec.INPUTSTREAM_CODEC;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class InputStreamCodecTest {

    @Test
    void emptyStringShouldParseWithNoBytes() throws IOException {
        try (InputStream testResult = INPUTSTREAM_CODEC.parse("")) {
            assertEquals(-1, testResult.read(), "Parsed InputStream should have no bytes!");
        }
    }

    @Test
    void nullStringShouldParseAsNull() throws IOException {
        try (InputStream testResult = INPUTSTREAM_CODEC.parse(null)) {
            assertNull(testResult, "Parsed null InputStream should be null!");
        }
    }

    @Test
    void nullByteBufferShouldDeserializeAsNull() throws IOException {
        try (InputStream testResult = INPUTSTREAM_CODEC.decode(null, null)) {
            assertNull(testResult, "Parsed null InputStream should be null!");
        }
    }

    @Test
    void nullInputStreamShouldFormatAsNull() {
        final String testResult = INPUTSTREAM_CODEC.format(null);
        assertNull(testResult, "Parsed null InputStream should be null!");
    }

    @Test
    void nullInputStreamShouldSerializeAsNull() {
        final ByteBuffer testResult = INPUTSTREAM_CODEC.encode(null, null);
        assertNull(testResult, "Parsed null InputStream should be null!");
    }

    @Test
    void emptyInputStreamShouldSerializeAsEmpty() {
        final ByteBuffer testResult = INPUTSTREAM_CODEC.encode(new ByteArrayInputStream(new byte[] {}), null);
        assertFalse(testResult.hasRemaining(), "Parsed null InputStream should be null!");
    }

    @Test
    void emptyByteBufferShouldParseAsEmpty() throws IOException {
        final ByteBuffer testBuffer = ByteBuffer.wrap(new byte[] {});
        try (InputStream testResult = INPUTSTREAM_CODEC.decode(testBuffer, null)) {
            assertEquals(-1, testResult.read(), "Parsed null InputStream should be null!");
        }
    }

    @Test
    void emptyStringShouldParseAsEmpty() throws IOException {
        try (InputStream testResult = INPUTSTREAM_CODEC.parse("")) {
            assertEquals(-1, testResult.read(), "Parsed null InputStream should be null!");
        }
    }
}
