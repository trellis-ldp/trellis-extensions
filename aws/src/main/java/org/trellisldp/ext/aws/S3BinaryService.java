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
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.IRI;
import org.eclipse.microprofile.config.Config;
import org.trellisldp.api.Binary;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.BinaryService;
import org.trellisldp.api.DefaultIdentifierService;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.api.RuntimeTrellisException;

/**
 * An S3-based binary service.
 */
@ApplicationScoped
public class S3BinaryService implements BinaryService {

    public static final String CONFIG_BINARY_BUCKET = "trellis.s3.binary.bucket";
    public static final String CONFIG_BINARY_PATH_PREFIX = "trellis.s3.binary.path.prefix";

    private static final String PREFIX = "s3://";

    private final IdentifierService idService = new DefaultIdentifierService();
    private final AmazonS3 client;
    private final String bucketName;
    private final String pathPrefix;

    /**
     * Create an S3-based binary service.
     */
    @Inject
    public S3BinaryService() {
        this(defaultClient(), getConfig());
    }

    private S3BinaryService(final AmazonS3 client, final Config config) {
        this(client, config.getValue(CONFIG_BINARY_BUCKET, String.class),
                config.getOptionalValue(CONFIG_BINARY_PATH_PREFIX, String.class).orElse(""));
    }

    /**
     * Create an S3-based binary service.
     * @param client the client
     * @param bucketName the bucket name
     * @param pathPrefix the path prefix, may be {@code null}
     */
    public S3BinaryService(final AmazonS3 client, final String bucketName, final String pathPrefix) {
        this.client = requireNonNull(client, "client may not be null!");
        this.bucketName = requireNonNull(bucketName, "bucket name may not be null!");
        this.pathPrefix = pathPrefix != null ? pathPrefix : "";
    }

    @Override
    public CompletionStage<Binary> get(final IRI identifier) {
        return supplyAsync(() -> new S3Binary(client, bucketName, getKey(identifier)));
    }

    @Override
    public CompletionStage<Void> purgeContent(final IRI identifier) {
        return runAsync(() -> client.deleteObject(bucketName, getKey(identifier)));
    }

    @Override
    public CompletionStage<Void> setContent(final BinaryMetadata metadata, final InputStream stream) {
        return runAsync(() -> {
            try {
                bufferUpload(metadata, stream, Files.createTempFile("trellis-binary", ".tmp"));
            } catch (final IOException ex) {
                throw new UncheckedIOException("Error buffering binary to local file", ex);
            }
        });
    }

    @Override
    public String generateIdentifier() {
        return idService.getSupplier(PREFIX).get();
    }

    private void bufferUpload(final BinaryMetadata metadata, final InputStream stream, final Path path)
            throws IOException {
        // Buffer the file locally so that the PUT request can be parallelized for large objects
        try {
            try (final OutputStream output = Files.newOutputStream(path, WRITE)) {
                IOUtils.copy(stream, output);
            }
            final ObjectMetadata md = new ObjectMetadata();
            metadata.getMimeType().ifPresent(md::setContentType);
            final PutObjectRequest req = new PutObjectRequest(bucketName, getKey(metadata.getIdentifier()),
                    path.toFile()).withMetadata(md);
            client.putObject(req);
        } finally {
            Files.delete(path);
        }
    }

    private String getKey(final IRI identifier) {
        final String id = identifier.getIRIString();
        if (id.startsWith(PREFIX)) {
            return pathPrefix + id.substring(PREFIX.length());
        }
        throw new RuntimeTrellisException("Invalid identifier: " + identifier);
    }
}
