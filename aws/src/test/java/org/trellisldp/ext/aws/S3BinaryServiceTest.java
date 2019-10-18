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
package org.trellisldp.ext.aws;

import static com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.trellisldp.api.TrellisUtils.getInstance;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletionException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.BinaryService;

public class S3BinaryServiceTest {

    private static final RDF rdf = getInstance();
    private static final int length = 10;
    private static final String base = new RandomStringGenerator.Builder().withinRange('a', 'z')
        .build().generate(length);

    @AfterAll
    public static void tearDown() throws Exception {
        final AmazonS3 client = defaultClient();
        final String bucket = ConfigProvider.getConfig().getValue(S3BinaryService.CONFIG_BINARY_BUCKET, String.class);
        client.listObjects(bucket, "binaries/" + base).getObjectSummaries().stream()
            .map(S3ObjectSummary::getKey).forEach(key -> client.deleteObject(bucket, key));
    }

    @Test
    public void testBinary() {
        final IRI identifier = rdf.createIRI("s3://binaries/" + base + "/resource");
        final BinaryService svc = new S3BinaryService();
        final InputStream input = getClass().getResourceAsStream("/file.txt");
        assertDoesNotThrow(svc.setContent(BinaryMetadata.builder(identifier).mimeType("text/plain").build(),
                    input).toCompletableFuture()::join);
        svc.get(identifier).thenAccept(binary -> {
            try {
                assertEquals("A sample binary file.",
                        IOUtils.toString(binary.getContent(), UTF_8).trim());
            } catch (final IOException ex) {
                fail("Error reading IO stream", ex);
            }
        }).toCompletableFuture().join();

        svc.get(identifier).thenAccept(binary -> {
            try {
                assertEquals("ple bi",
                        IOUtils.toString(binary.getContent(5, 10), UTF_8).trim());
            } catch (final IOException ex) {
                fail("Error reading IO stream", ex);
            }
        }).toCompletableFuture().join();

        assertDoesNotThrow(svc.purgeContent(identifier).toCompletableFuture()::join);
    }

    @Test
    public void testIdentifier() {
        final BinaryService svc = new S3BinaryService();
        assertTrue(svc.generateIdentifier().startsWith("s3://"));
    }

    @Test
    public void testInvalidIdentifier() {
        final IRI identifier = rdf.createIRI("file://binaries/" + base + "/resource");
        final BinaryService svc = new S3BinaryService();
        assertDoesNotThrow(svc.get(identifier).handle((v, err) -> {
            assertNotNull(err);
            return null;
        }).toCompletableFuture()::join);
    }

    @Test
    public void testErrors() {
        final InputStream throwingMockInputStream = mock(InputStream.class, inv -> {
                throw new IOException("Expected error");
        });
        final BinaryService svc = new S3BinaryService();
        final IRI identifier = rdf.createIRI(svc.generateIdentifier());
        assertThrows(CompletionException.class,
                svc.setContent(BinaryMetadata.builder(identifier).build(),
                    throwingMockInputStream).toCompletableFuture()::join);
    }
}
