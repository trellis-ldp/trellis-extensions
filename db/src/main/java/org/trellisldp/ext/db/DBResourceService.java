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
package org.trellisldp.ext.db;

import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.ServiceLoader.load;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.toSet;
import static org.apache.tamaya.ConfigurationProvider.getConfiguration;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.TrellisUtils.getInstance;
import static org.trellisldp.ext.db.DBUtils.getObjectDatatype;
import static org.trellisldp.ext.db.DBUtils.getObjectLang;
import static org.trellisldp.ext.db.DBUtils.getObjectValue;
import static org.trellisldp.vocabulary.Trellis.PreferAccessControl;
import static org.trellisldp.vocabulary.Trellis.PreferAudit;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.time.Instant;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Update;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.DefaultIdentifierService;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.audit.DefaultAuditService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;

/**
 * A Database-backed implementation of the Trellis ResourceService API.
 *
 * <p>Note: one can manipulate the size of a batched query by setting
 * a property for {@code trellis.ext.db.batchSize}. By default, this
 * value is 1,000.
 */
public class DBResourceService extends DefaultAuditService implements ResourceService {

    /** Configuration key used to define the size of database write batches. **/
    public static final String BATCH_KEY = "trellis.ext.db.batchSize";
    /** The default size of a database batch write operation. **/
    public static final int DEFAULT_BATCH_SIZE = 1000;

    private static final Logger LOGGER = getLogger(DBResourceService.class);
    private static final RDF rdf = getInstance();

    private final Supplier<String> supplier;
    private final Jdbi jdbi;
    private final Set<IRI> supportedIxnModels;
    private final int batchSize;

    /**
     * Create a Database-backed resource service.
     * @param ds the data source
     */
    @Inject
    public DBResourceService(final DataSource ds) {
        this(Jdbi.create(ds));
    }

    /**
     * Create a Database-backed resource service.
     * @param jdbi the jdbi object
     */
    public DBResourceService(final Jdbi jdbi) {
        this(jdbi, getConfiguration().getOrDefault(BATCH_KEY, Integer.class, DEFAULT_BATCH_SIZE),
                of(load(IdentifierService.class)).map(ServiceLoader::iterator).filter(Iterator::hasNext)
                    .map(Iterator::next).orElseGet(DefaultIdentifierService::new));
    }

    /**
     * Create a Database-backed resource service.
     * @param jdbi the jdbi object
     * @param batchSize the batch size
     * @param identifierService an ID supplier service
     */
    public DBResourceService(final Jdbi jdbi, final int batchSize, final IdentifierService identifierService) {
        this.jdbi = requireNonNull(jdbi, "Jdbi may not be null!");
        this.supplier = requireNonNull(identifierService, "IdentifierService may not be null!").getSupplier();
        this.batchSize = batchSize;
        this.supportedIxnModels = unmodifiableSet(asList(LDP.Resource, LDP.RDFSource, LDP.NonRDFSource, LDP.Container,
                LDP.BasicContainer, LDP.DirectContainer, LDP.IndirectContainer).stream().collect(toSet()));
        LOGGER.info("Using database persistence with TrellisLDP");
    }

    @Override
    public CompletionStage<Void> create(final Metadata metadata, final Dataset dataset) {
        LOGGER.debug("Creating: {}", metadata.getIdentifier());
        return runAsync(() -> storeResource(metadata, dataset, now(), OperationType.CREATE));
    }

    @Override
    public CompletionStage<Void> delete(final Metadata metadata) {
        LOGGER.debug("Deleting: {}", metadata.getIdentifier());
        return runAsync(() -> {
            try (final Dataset dataset = rdf.createDataset()) {
                final Instant time = now();
                final Metadata md = Metadata.builder(metadata.getIdentifier()).interactionModel(LDP.Resource).build();
                storeResource(md, dataset, time, OperationType.DELETE);
            } catch (final Exception ex) {
                throw new RuntimeTrellisException("Error deleting resoruce: " + metadata.getIdentifier(), ex);
            }
        });
    }

    @Override
    public CompletionStage<Void> replace(final Metadata metadata, final Dataset dataset) {
        LOGGER.debug("Updating: {}", metadata.getIdentifier());
        return runAsync(() -> storeResource(metadata, dataset, now(), OperationType.REPLACE));
    }

    @Override
    public CompletionStage<Void> touch(final IRI id) {
        LOGGER.debug("Updating modification date for {}", id);
        final Instant time = now();
        return runAsync(() -> updateResourceModification(id, time));
    }

    @Override
    public CompletionStage<Resource> get(final IRI identifier) {
        return DBResource.findResource(jdbi, identifier);
    }

    @Override
    public String generateIdentifier() {
        return supplier.get();
    }

    @Override
    public CompletionStage<Void> add(final IRI id, final Dataset dataset) {
        final String query
            = "INSERT INTO log (id, subject, predicate, object, lang, datatype) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        return runAsync(() -> {
            try {
                jdbi.useHandle(handle -> dataset.getGraph(PreferAudit).ifPresent(graph -> {
                        try (final PreparedBatch batch = handle.prepareBatch(query)) {
                            graph.stream().forEach(triple -> batch
                                    .bind(0, id.getIRIString())
                                    .bind(1, ((IRI) triple.getSubject()).getIRIString())
                                    .bind(2, triple.getPredicate().getIRIString())
                                    .bind(3, getObjectValue(triple.getObject()))
                                    .bind(4, getObjectLang(triple.getObject()))
                                    .bind(5, getObjectDatatype(triple.getObject())).add());
                            if (batch.size() > 0) {
                                batch.execute();
                            }
                        }
                    }));
            } catch (final Exception ex) {
                throw new RuntimeTrellisException("Error storing audit dataset for " + id, ex);
            }
        });
    }

    @Override
    public Set<IRI> supportedInteractionModels() {
        return supportedIxnModels;
    }

    private void updateResourceModification(final IRI identifier, final Instant time) {
        final String query = "UPDATE resource SET modified=? WHERE subject=?";
        try {
            jdbi.useHandle(handle -> {
                try (final Update update = handle.createUpdate(query)
                        .bind(0, time.toEpochMilli())
                        .bind(1, identifier.getIRIString())) {
                    update.execute();
                }
            });
        } catch (final Exception ex) {
            throw new RuntimeTrellisException("Error updating modification date for " + identifier, ex);
        }
    }

    private static int updateResource(final Handle handle, final Metadata metadata, final Dataset dataset,
            final Instant time, final boolean isDelete) {

        handle.execute("DELETE FROM resource WHERE subject = ?", metadata.getIdentifier().getIRIString());
        final String query
            = "INSERT INTO resource (subject, interaction_model, modified, deleted, is_part_of, acl, "
            + "ldp_member, ldp_membership_resource, ldp_has_member_relation, ldp_is_member_of_relation, "
            + "ldp_inserted_content_relation, binary_location, binary_format) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // Set ldp:insertedContentRelation only for LDP-IC and LDP-DC resources
        final String icr = asList(LDP.DirectContainer, LDP.IndirectContainer).contains(metadata.getInteractionModel())
            ? metadata.getInsertedContentRelation().orElse(LDP.MemberSubject).getIRIString() : null;

        try (final Update update = handle.createUpdate(query)
                .bind(0, metadata.getIdentifier().getIRIString())
                .bind(1, metadata.getInteractionModel().getIRIString())
                .bind(2, time.toEpochMilli())
                .bind(3, isDelete)
                .bind(4, metadata.getContainer().map(IRI::getIRIString).orElse(null))
                .bind(5, dataset.contains(of(PreferAccessControl), null, null, null))
                .bind(6, metadata.getMembershipResource().map(IRI::getIRIString)
                    .map(str -> str.split("#")[0]).orElse(null))
                .bind(7, metadata.getMembershipResource().map(IRI::getIRIString).orElse(null))
                .bind(8, metadata.getMemberRelation().map(IRI::getIRIString).orElse(null))
                .bind(9, metadata.getMemberOfRelation().map(IRI::getIRIString).orElse(null))
                .bind(10, icr)
                .bind(11, metadata.getBinary().map(BinaryMetadata::getIdentifier).map(IRI::getIRIString).orElse(null))
                .bind(12, metadata.getBinary().flatMap(BinaryMetadata::getMimeType).orElse(null))) {
            return update.executeAndReturnGeneratedKeys("id").mapTo(Integer.class).findOnly();
        }
    }

    private static void updateDescription(final Handle handle, final int resourceId, final Dataset dataset,
            final int batchSize) {
        dataset.getGraph(PreferUserManaged).ifPresent(graph ->
                batchUpdateTriples(handle, resourceId, "description", graph, batchSize));
    }

    private static void updateAcl(final Handle handle, final int resourceId, final Dataset dataset,
            final int batchSize) {
        dataset.getGraph(PreferAccessControl).ifPresent(graph ->
                batchUpdateTriples(handle, resourceId, "acl", graph, batchSize));
    }

    private static void batchUpdateTriples(final Handle handle, final int resourceId, final String table,
            final Graph graph, final int batchSize) {
        final String query
            = "INSERT INTO " + table + " (resource_id, subject, predicate, object, lang, datatype) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (final PreparedBatch batch = handle.prepareBatch(query)) {
            graph.stream().sequential().forEach(triple -> {
                batch.bind(0, resourceId)
                     .bind(1, ((IRI) triple.getSubject()).getIRIString())
                     .bind(2, triple.getPredicate().getIRIString())
                     .bind(3, getObjectValue(triple.getObject()))
                     .bind(4, getObjectLang(triple.getObject()))
                     .bind(5, getObjectDatatype(triple.getObject())).add();
                if (batch.size() >= batchSize) {
                    batch.execute();
                }
            });
            if (batch.size() > 0) {
                batch.execute();
            }
        }
    }

    private static void updateExtra(final Handle handle, final int resourceId, final IRI identifier,
            final Dataset dataset) {
        dataset.getGraph(PreferUserManaged).ifPresent(graph -> {
            final String query = "INSERT INTO extra (resource_id, predicate, object) VALUES (?, ?, ?)";
            try (final PreparedBatch batch = handle.prepareBatch(query)) {
                graph.stream(identifier, LDP.inbox, null).map(Triple::getObject).filter(t -> t instanceof IRI)
                    .map(t -> ((IRI) t).getIRIString()).findFirst().ifPresent(iri ->
                            batch.bind(0, resourceId)
                                 .bind(1, LDP.inbox.getIRIString())
                                 .bind(2, iri)
                                 .add());

                graph.stream(identifier, OA.annotationService, null).map(Triple::getObject)
                     .filter(t -> t instanceof IRI).map(t -> ((IRI) t).getIRIString()).findFirst().ifPresent(iri ->
                            batch.bind(0, resourceId)
                                 .bind(1, OA.annotationService.getIRIString())
                                 .bind(2, iri).add());

                if (batch.size() > 0) {
                    batch.execute();
                }
            }
        });
    }

    private void storeResource(final Metadata metadata, final Dataset dataset, final Instant time,
            final OperationType opType) {
        try {
            jdbi.useTransaction(handle -> {
                final int resourceId = updateResource(handle, metadata, dataset, time,
                        opType == OperationType.DELETE);
                updateDescription(handle, resourceId, dataset, batchSize);
                updateAcl(handle, resourceId, dataset, batchSize);
                updateExtra(handle, resourceId, metadata.getIdentifier(), dataset);
            });
        } catch (final Exception ex) {
            throw new RuntimeTrellisException("Could not update data for " + metadata.getIdentifier(), ex);
        }
    }

    private enum OperationType {
        DELETE, CREATE, REPLACE;
    }
}
