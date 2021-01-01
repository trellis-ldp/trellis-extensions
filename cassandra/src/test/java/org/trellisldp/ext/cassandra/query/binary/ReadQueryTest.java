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
package org.trellisldp.ext.cassandra.query.binary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.trellisldp.ext.cassandra.query.binary.BinaryReadQuery.SequenceInputStream;

class ReadQueryTest {

    @Test
    void shouldConcatStreams() throws IOException {
        try (InputStream one = new ByteArrayInputStream("one".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("two".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            final String answer = IOUtils.toString(stream, UTF_8);
            assertEquals("onetwo", answer, "Did not correctly concat streams!");
        }
    }

    @Test
    void shouldSkipAcrossStreams() throws IOException {
        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            assertEquals(12, stream.skip(12), "Could not skip through first stream!");
            final String answer = IOUtils.toString(stream, UTF_8);
            assertEquals("ourfivesix", answer, "Did not correctly stream rest of streams!");
        }
    }

    @Test
    void shouldReadAcrossStreams() throws IOException {
        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two);
             InputStream answer = new ByteArrayInputStream(("onetwothree" + "fourfivesix").getBytes(UTF_8))) {
            int read = 0;
            int count = 0;
            while ((read = stream.read()) != -1) {
                count++;
                assertEquals(read, answer.read(), "Got wrong byte from read()!");
            }
            assertEquals(-1, answer.read(), "Answer stream was not exhausted after comparison!");
            assertEquals(22, count, "Not enough bytes were read!");
        }
    }

    @Test
    void shouldFulfillSkipContractEdges() throws IOException {
        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            assertEquals(0, stream.skip(0));
            assertEquals(0, stream.skip(-1));
        }
    }

    @Test
    void shouldFulfillReadContractEdges1() throws IOException {
        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[0], 0, -1));
        }
    }

    @Test
    void shouldFulfillReadContractEdges2() throws IOException {
        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[0], -1, 10));
        }
    }

    @Test
    void shouldFulfillReadContractEdges3() throws IOException {

        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[5], 2, 10));
        }
    }

    @Test
    void shouldFulfillReadContractEdges4() throws IOException {

        try (InputStream one = new ByteArrayInputStream("onetwothree".getBytes(UTF_8));
             InputStream two = new ByteArrayInputStream("fourfivesix".getBytes(UTF_8));
             SequenceInputStream stream = new SequenceInputStream(one, two)) {
            assertEquals(0, stream.read(new byte[5], 2, 0));
        }
    }
}
