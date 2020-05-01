/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
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
package org.trellisldp.ext.aws;

import static com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient;
import static java.io.File.createTempFile;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Collections.unmodifiableSet;
import static java.util.Collections.unmodifiableSortedSet;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.of;
import static org.apache.jena.riot.Lang.NQUADS;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.enterprise.inject.Alternative;
import javax.inject.Inject;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.jena.JenaDataset;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.riot.RDFDataMgr;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.trellisldp.api.MementoService;
import org.trellisldp.api.Resource;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * An S3-based Memento service.
 */
@Alternative
public class S3MementoService implements MementoService {

    public static final Logger LOGGER = getLogger(S3MementoService.class);
    public static final String CONFIG_AWS_MEMENTO_BUCKET = "trellis.aws.memento-bucket";
    public static final String CONFIG_AWS_MEMENTO_PATH_PREFIX = "trellis.aws.memento-path-prefix";

    private static final Set<IRI> IGNORE = buildIgnoreGraphs();
    private static final JenaRDF rdf = new JenaRDF();

    private final AmazonS3 client;
    private final String bucketName;
    private final String pathPrefix;

    /**
     * Create an S3-based memento service.
     */
    @Inject
    public S3MementoService() {
        this(defaultClient(), getConfig());
    }

    private S3MementoService(final AmazonS3 client, final Config config) {
        this(client, config.getValue(CONFIG_AWS_MEMENTO_BUCKET, String.class),
                config.getOptionalValue(CONFIG_AWS_MEMENTO_PATH_PREFIX, String.class).orElse(""));
    }

    /**
     * Create an S3-based memento service.
     * @param client the S3 client
     * @param bucketName the bucket name
     * @param pathPrefix the path prefix for mementos, may be {@code null}
     */
    public S3MementoService(final AmazonS3 client, final String bucketName, final String pathPrefix) {
        this.client = requireNonNull(client, "S3 client may not be null!");
        this.bucketName = requireNonNull(bucketName, "AWS Bucket may not be null!");
        this.pathPrefix = pathPrefix != null ? pathPrefix : "";
        LOGGER.info("Using AWS for memento persistence. S3 bucket: '{}'", bucketName);
    }

    @Override
    public CompletionStage<Void> put(final Resource resource) {
        return runAsync(() -> {
            try {
                final File file = createTempFile("trellis-memento-", ".nq");
                file.deleteOnExit();
                final Map<String, String> metadata = new HashMap<>();
                metadata.put(S3Resource.INTERACTION_MODEL, resource.getInteractionModel().getIRIString());
                metadata.put(S3Resource.MODIFIED, resource.getModified().toString());
                resource.getContainer().map(IRI::getIRIString).ifPresent(c -> metadata.put(S3Resource.CONTAINER, c));
                resource.getBinaryMetadata().ifPresent(b -> {
                    metadata.put(S3Resource.BINARY_LOCATION, b.getIdentifier().getIRIString());
                    b.getMimeType().ifPresent(m -> metadata.put(S3Resource.BINARY_TYPE, m));
                });
                resource.getMembershipResource().map(IRI::getIRIString)
                    .ifPresent(m -> metadata.put(S3Resource.MEMBERSHIP_RESOURCE, m));
                resource.getMemberRelation().map(IRI::getIRIString)
                    .ifPresent(m -> metadata.put(S3Resource.MEMBER_RELATION, m));
                resource.getMemberOfRelation().map(IRI::getIRIString)
                    .ifPresent(m -> metadata.put(S3Resource.MEMBER_OF_RELATION, m));
                resource.getInsertedContentRelation().map(IRI::getIRIString)
                    .ifPresent(m -> metadata.put(S3Resource.INSERTED_CONTENT_RELATION, m));

                try (final JenaDataset dataset = rdf.createDataset();
                        final OutputStream output = Files.newOutputStream(file.toPath());
                        final Stream<? extends Quad> quads = resource.stream()) {
                    quads.forEachOrdered(dataset::add);

                    metadata.put(S3Resource.METADATA_GRAPHS, dataset.getGraphNames().filter(IRI.class::isInstance)
                            .map(IRI.class::cast).filter(graph -> !IGNORE.contains(graph)).map(IRI::getIRIString)
                            .collect(joining(",")));
                    RDFDataMgr.write(output, dataset.asJenaDatasetGraph(), NQUADS);
                }
                final ObjectMetadata md = new ObjectMetadata();
                md.setContentType("application/n-quads");
                md.setUserMetadata(metadata);
                final PutObjectRequest req = new PutObjectRequest(bucketName, getKey(resource.getIdentifier(),
                            resource.getModified().truncatedTo(SECONDS)), file);
                client.putObject(req.withMetadata(md));
                Files.delete(file.toPath());
            } catch (final Exception ex) {
                throw new RuntimeTrellisException("Error deleting locally buffered file", ex);
            }
        });
    }

    @Override
    public CompletionStage<Resource> get(final IRI identifier, final Instant time) {
        return supplyAsync(() ->  {
            final String key = getKey(identifier, time.truncatedTo(SECONDS));
            if (client.doesObjectExist(bucketName, key)) {
                return new S3Resource(client.getObjectMetadata(bucketName, key), client,
                        new GetObjectRequest(bucketName, key), pathPrefix);
            }
            LOGGER.debug("Fetching mementos for {}", identifier);
            final SortedSet<Instant> allMementos = listMementos(identifier);
            if (allMementos.isEmpty()) {
                return MISSING_RESOURCE;
            }
            final SortedSet<Instant> possible = allMementos.headSet(time.truncatedTo(SECONDS));
            final String best = getKey(identifier, possible.isEmpty() ? allMementos.first() : possible.last());
            return new S3Resource(client.getObjectMetadata(bucketName, best), client,
                    new GetObjectRequest(bucketName, best), pathPrefix);
        });
    }

    @Override
    public CompletionStage<SortedSet<Instant>> mementos(final IRI identifier) {
        return supplyAsync(() -> listMementos(identifier));
    }

    private SortedSet<Instant> listMementos(final IRI identifier) {
        final SortedSet<Instant> versions = new TreeSet<>();
        final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName)
            .withPrefix(getKey(identifier)).withDelimiter("/");
        ListObjectsV2Result result;
        do {
            result = client.listObjectsV2(req);
            result.getObjectSummaries().stream().map(S3ObjectSummary::getKey).flatMap(this::getInstant)
                .map(i -> i.truncatedTo(SECONDS)).forEachOrdered(versions::add);
            req.setContinuationToken(result.getContinuationToken());
        } while (result.isTruncated());
        return unmodifiableSortedSet(versions);
    }

    private Stream<Instant> getInstant(final String key) {
        return of(key).map(k -> k.split("\\?version=", 2)).filter(p -> p.length == 2).map(p -> p[1])
            .map(Long::parseLong).map(Instant::ofEpochSecond).map(i -> i.truncatedTo(SECONDS));
    }

    private String getKey(final IRI identifier) {
        return pathPrefix + identifier.getIRIString().substring(TRELLIS_DATA_PREFIX.length()) + "?version=";
    }

    private String getKey(final IRI identifier, final Instant time) {
        return getKey(identifier) + Long.toString(time.truncatedTo(SECONDS).getEpochSecond());
    }

    static Set<IRI> buildIgnoreGraphs() {
        final Set<IRI> graphs = new HashSet<>();
        graphs.add(Trellis.PreferUserManaged);
        graphs.add(Trellis.PreferServerManaged);
        graphs.add(LDP.PreferContainment);
        graphs.add(LDP.PreferMembership);
        return unmodifiableSet(graphs);
    }
}
