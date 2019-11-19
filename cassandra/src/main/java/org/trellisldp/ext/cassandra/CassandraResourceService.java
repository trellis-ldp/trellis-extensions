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

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.Metadata.builder;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.vocabulary.LDP.BasicContainer;
import static org.trellisldp.vocabulary.LDP.Container;
import static org.trellisldp.vocabulary.LDP.NonRDFSource;
import static org.trellisldp.vocabulary.LDP.RDFSource;
import static org.trellisldp.vocabulary.LDP.getSuperclassOf;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.uuid.Uuids;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.slf4j.Logger;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.api.TrellisUtils;
import org.trellisldp.ext.cassandra.query.rdf.BasicContainment;
import org.trellisldp.ext.cassandra.query.rdf.Delete;
import org.trellisldp.ext.cassandra.query.rdf.Get;
import org.trellisldp.ext.cassandra.query.rdf.ImmutableInsert;
import org.trellisldp.ext.cassandra.query.rdf.ImmutableRetrieve;
import org.trellisldp.ext.cassandra.query.rdf.MutableInsert;
import org.trellisldp.ext.cassandra.query.rdf.Touch;
import org.trellisldp.vocabulary.LDP;

/**
 * Implements persistence into a simple Apache Cassandra schema.
 *
 * @author ajs6f
 */
@ApplicationScoped
class CassandraResourceService implements ResourceService, CassandraBuildingService {

    private static final Set<IRI> SUPPORTED_INTERACTION_MODELS;

    static {
        final Set<IRI> ixnModels = new HashSet<>(Arrays.asList(LDP.Resource, RDFSource, NonRDFSource, Container,
                    BasicContainer));
        SUPPORTED_INTERACTION_MODELS = Collections.unmodifiableSet(ixnModels);
    }

    static final Logger log = getLogger(CassandraResourceService.class);

    private final Delete delete;

    private final Get get;

    private final ImmutableInsert immutableInsert;

    private final MutableInsert mutableInsert;

    private final Touch touch;

    private final BasicContainment bcontainment;

    private final ImmutableRetrieve immutableRetrieve;

    CassandraResourceService() {
        this(null, null, null, null, null, null, null);
    }

    @Inject
    CassandraResourceService(final Delete delete, final Get get, final ImmutableInsert immutableInsert,
            final MutableInsert mutableInsert, final Touch touch, final ImmutableRetrieve immutableRetrieve,
            final BasicContainment bcontainment) {
        this.delete = delete;
        this.get = get;
        this.immutableInsert = immutableInsert;
        this.mutableInsert = mutableInsert;
        this.touch = touch;
        this.immutableRetrieve = immutableRetrieve;
        this.bcontainment = bcontainment;
    }

    /**
     * Build a root container.
     */
    @PostConstruct
    void initializeRoot() {
        final IRI rootIri = TrellisUtils.getInstance().createIRI(TRELLIS_DATA_PREFIX);
        try {
            if (get(rootIri).toCompletableFuture().get().equals(MISSING_RESOURCE)) {
                final Metadata rootResource = builder(rootIri).interactionModel(BasicContainer).build();
                create(rootResource, null).toCompletableFuture().get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedStartupException("Interrupted while building repository root!", e);
        } catch (ExecutionException e) {
            throw new RuntimeTrellisException(e);
        }
    }

    @Override
    public CompletionStage<? extends Resource> get(final IRI id) {
        log.debug("Retrieving {}", id);
        log.debug("Retrieving immutable data for {}", id);
        final CompletionStage<Stream<Quad>> immutableData = immutableRetrieve.execute(id);
        // get resource and add immutable tuples
        log.debug("Retrieving mutable data for {}", id);
        final CompletionStage<Resource> resource = get.execute(id)
                        .thenApply(AsyncResultSet::one)
                        .thenApply(row -> parse(row, log, id))
                        .thenCombine(immutableData, this::addTuples);
        // add containment tuples if needed
        return resource.thenCompose(res -> {
            if (!isContainer(res)) return resource;
            log.debug("Retrieving containment data for {}", id);
            return resource.thenCombine(bcontainment.execute(id), this::addTuples);
        });
    }

    private Resource addTuples(final Resource resource, final Stream<Quad> additionalTuples) {
        additionalTuples.forEach(resource.dataset()::add);
        return resource;
    }

    private static boolean isContainer(final Resource res) {
        final IRI interactionModel = res.getInteractionModel();
        final IRI superclass = getSuperclassOf(interactionModel);
        return Container.equals(interactionModel) || Container.equals(superclass);
    }

    @Override
    public String generateIdentifier() {
        return randomUUID().toString();
    }

    @Override
    public CompletionStage<Void> add(final IRI id, final Dataset dataset) {
        log.debug("Adding immutable data to {}", id);
        return immutableInsert.execute(id, dataset, now());
    }

    @Override
    public CompletionStage<Void> create(final Metadata meta, final Dataset data) {
        log.debug("Creating {} with interaction model {}", meta.getIdentifier(), meta.getInteractionModel());
        return write(meta, data);
    }

    @Override
    public CompletionStage<Void> replace(final Metadata meta, final Dataset data) {
        log.debug("Replacing {} with interaction model {}", meta.getIdentifier(), meta.getInteractionModel());
        return write(meta, data);
    }

    @Override
    public CompletionStage<Void> delete(final Metadata meta) {
        log.debug("Deleting {}", meta.getIdentifier());
        return delete.execute(meta.getIdentifier());
    }

    @Override
    public CompletionStage<Void> touch(final IRI id) {
        return touch.execute(now(), id);
    }

    @Override
    public Set<IRI> supportedInteractionModels() {
        return SUPPORTED_INTERACTION_MODELS;
    }

    private CompletionStage<Void> write(final Metadata meta, final Dataset data) {
        return mutableInsert.execute(meta, now(), data, Uuids.timeBased());
    }
}
