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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.contentEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.BinaryMetadata.builder;
import static org.trellisldp.ext.cassandra.CassandraBinaryService.CASSANDRA_CHUNK_HEADER_NAME;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.IRI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.RuntimeTrellisException;

@EnabledIfSystemProperty(named = "trellis.test.cassandra", matches = "true")
class CassandraBinaryServiceIT extends CassandraServiceIT {

    private static final Logger log = getLogger(CassandraBinaryServiceIT.class);

    @Test
    void setAndGetSmallContent() throws IOException {
        final IRI id = createIRI();
        log.debug("Using identifier: {}", id);
        final String content = "This is only a short test, but it has meaning";
        try (InputStream testInput = IOUtils.toInputStream(content, UTF_8)) {
            connection.binaryService.setContent(builder(id).build(), testInput).toCompletableFuture().join();
        }

        final CompletableFuture<Binary> future = connection.binaryService.get(id).toCompletableFuture();
        final Binary binary = future.join();
        assertTrue(future.isDone());
        log.debug("Retrieved binary metadata for {}", id);
        try (InputStream got = binary.getContent(5, 11)) {
            log.debug("Retrieved range-limited content for {}", id);
            final String reply = IOUtils.toString(got, UTF_8);
            assertEquals(content.subSequence(5, 12), reply);
        }

        try (InputStream got = binary.getContent()) {
            log.debug("Retrieved all content for {}", id);
            final String reply = IOUtils.toString(got, UTF_8);
            assertEquals(content, reply);
        }
    }

    @Test
    void shouldNotFindDeletedContent() throws Exception {
        final IRI id = createIRI();
        log.debug("Using identifier: {}", id);
        final String testContent = "This is only a short test, but it has meaning";
        try (InputStream testInput = IOUtils.toInputStream(testContent, UTF_8)) {
            connection.binaryService.setContent(builder(id).build(), testInput).toCompletableFuture().join();
        }
        connection.binaryService.purgeContent(id).toCompletableFuture().join();

        final Throwable cause = assertThrows(NullPointerException.class, () ->
                unwrapAsyncException(connection.binaryService.get(id)));
        assertTrue(cause.getMessage().contains(id.getIRIString()));
    }

    @Test
    void setAndGetMultiChunkContent() throws IOException {
        final IRI id = createIRI();
        try (FileInputStream testData = new FileInputStream("src/test/resources/test.jpg")) {
            connection.binaryService.setContent(builder(id).build(), testData).toCompletableFuture().join();
        }
        final CompletionStage<Binary> got = connection.binaryService.get(id);
        final Binary binary = got.toCompletableFuture().join();
        log.debug("Retrieved binary for {}.", id);
        assertTrue(got.toCompletableFuture().isDone());

        try (FileInputStream testData = new FileInputStream("src/test/resources/test.jpg");
             InputStream content = binary.getContent()) {
            assertTrue(contentEquals(testData, content), "Didn't retrieve correct content!");
        }
        log.debug("Retrieved and checked content for {}.", id);
    }

    @Test
    void varyChunkSizeFromDefault() throws IOException, InterruptedException, ExecutionException {
        final IRI id = createIRI();
        final String chunkSize = "10000000";
        final String md5sum = "89c4b71c69f59cde963ce8aa9dbe1617";
        try (FileInputStream testData = new FileInputStream("src/test/resources/test.jpg")) {
            final Map<String, List<String>> hints = singletonMap(CASSANDRA_CHUNK_HEADER_NAME, singletonList(chunkSize));
            connection.binaryService.setContent(builder(id).hints(hints).build(), testData).toCompletableFuture().get();
        }

        final CompletionStage<Binary> got = connection.binaryService.get(id);
        final Binary binary = got.toCompletableFuture().get();
        assertTrue(got.toCompletableFuture().isDone());

        try (InputStream testData = new FileInputStream("src/test/resources/test.jpg");
             InputStream content = binary.getContent()) {
            assertTrue(contentEquals(testData, content), "Didn't retrieve correct content!");
        }

        try (InputStream content = binary.getContent()) {
            final String digest = DigestUtils.md5Hex(content);
            assertEquals(md5sum, digest);
        }

        try (FileInputStream testData = new FileInputStream("src/test/resources/test.jpg")) {
            final Map<String, List<String>> hints = singletonMap(CASSANDRA_CHUNK_HEADER_NAME,
                            Arrays.asList(chunkSize, chunkSize + 1000));
            final Throwable ex = assertThrows(RuntimeTrellisException.class, () ->
                unwrapAsyncException(connection.binaryService.setContent(builder(id).hints(hints).build(), testData)));
            assertTrue(ex.getMessage().contains(CASSANDRA_CHUNK_HEADER_NAME));
        }
    }

    @Test
    void testNoArgCtor() {
        assertDoesNotThrow(() -> new CassandraBinaryService());
    }

    private IRI createIRI() {
        return rdfFactory.createIRI("http://example.com/" + randomUUID());
    }

    private void unwrapAsyncException(final CompletionStage async) throws Throwable {
        try {
            async.toCompletableFuture().join();
        } catch (final CompletionException ex) {
            throw ex.getCause();
        }
    }
}
