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
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.empty;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.RDFUtils.TRELLIS_SESSION_BASE_URL;
import static org.trellisldp.api.RDFUtils.getInstance;
import static org.trellisldp.ext.db.DBUtils.getBaseIRI;
import static org.trellisldp.ext.db.DBUtils.getObjectDatatype;
import static org.trellisldp.ext.db.DBUtils.getObjectLang;
import static org.trellisldp.ext.db.DBUtils.getObjectValue;
import static org.trellisldp.vocabulary.RDF.type;
import static org.trellisldp.vocabulary.Trellis.DeletedResource;
import static org.trellisldp.vocabulary.Trellis.PreferAccessControl;
import static org.trellisldp.vocabulary.Trellis.PreferAudit;
import static org.trellisldp.vocabulary.Trellis.PreferServerManaged;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.commons.lang3.Range;
import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;
import org.apache.tamaya.Configuration;
import org.apache.tamaya.ConfigurationProvider;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.EventService;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.api.MementoService;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.api.Session;
import org.trellisldp.audit.DefaultAuditService;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.AS;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.FOAF;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.XSD;

/**
 * A Database-backed implementation of the Trellis ResourceService API.
 *
 * <p>Note: one can manipulate the size of a batched query by setting
 * a property for {@code trellis.ext.db.batchSize}. By default, this
 * value is 1,000.
 */
public class DBResourceService extends DefaultAuditService implements ResourceService {

    public static final String BATCH_KEY = "trellis.ext.db.batchSize";
    public static final int DEFAULT_BATCH_SIZE = 1000;

    private static final String PARENT = "parent";
    private static final String MODIFIED = "modified";
    private static final String MEMBER = "member";

    private static final Logger LOGGER = getLogger(DBResourceService.class);
    private static final RDF rdf = getInstance();

    private static final Predicate<BlankNodeOrIRI> isUserGraph = PreferUserManaged::equals;
    private static final Predicate<BlankNodeOrIRI> isServerGraph = PreferServerManaged::equals;
    private static final Configuration config = ConfigurationProvider.getConfiguration();

    private final Supplier<String> supplier;
    private final Jdbi jdbi;
    private final Optional<EventService> eventService;
    private final Optional<MementoService> mementoService;
    private final Set<IRI> supportedIxnModels;

    /**
     * Create a Database-backed resource service.
     * @param ds the data source
     * @param identifierService an ID supplier service
     * @param mementoService a service for memento resources
     * @param eventService an event service
     */
    @Inject
    public DBResourceService(final DataSource ds, final IdentifierService identifierService,
            final MementoService mementoService, final EventService eventService) {
        this(Jdbi.create(ds), identifierService, mementoService, eventService);
    }

    /**
     * Create a Database-backed resource service.
     * @param jdbi the jdbi object
     * @param identifierService an ID supplier service
     * @param mementoService a service for memento resources
     * @param eventService an event service
     */
    public DBResourceService(final Jdbi jdbi, final IdentifierService identifierService,
            final MementoService mementoService, final EventService eventService) {
        requireNonNull(jdbi, "Jdbi may not be null!");
        requireNonNull(identifierService, "IdentifierService may not be null!");
        this.jdbi = jdbi;
        this.supplier = identifierService.getSupplier();
        this.eventService = ofNullable(eventService);
        this.mementoService = ofNullable(mementoService);
        this.supportedIxnModels = unmodifiableSet(asList(LDP.Resource, LDP.RDFSource, LDP.NonRDFSource, LDP.Container,
                LDP.BasicContainer, LDP.DirectContainer, LDP.IndirectContainer).stream().collect(toSet()));
        init();
    }

    @Override
    public Future<Boolean> create(final IRI id, final Session session, final IRI ixnModel, final Dataset dataset,
                    final IRI container, final Binary binary) {
        LOGGER.debug("Creating: {}", id);
        return supplyAsync(() ->
                createOrReplace(id, session, ixnModel, dataset, OperationType.CREATE, container, binary));
    }

    @Override
    public Future<Boolean> delete(final IRI identifier, final Session session, final IRI ixnModel,
            final Dataset dataset) {
        LOGGER.debug("Deleting: {}", identifier);
        return supplyAsync(() -> {
            dataset.add(PreferServerManaged, identifier, DC.type, DeletedResource);
            dataset.add(PreferServerManaged, identifier, type, LDP.Resource);
            return storeAndNotify(identifier, session, ixnModel, dataset, OperationType.DELETE, null);
        });
    }

    @Override
    public Future<Boolean> replace(final IRI id, final Session session, final IRI ixnModel, final Dataset dataset,
                    final IRI container, final Binary binary) {
        LOGGER.debug("Updating: {}", id);
        return supplyAsync(() ->
                createOrReplace(id, session, ixnModel, dataset, OperationType.REPLACE, container, binary));
    }

    @Override
    public List<Range<Instant>> getMementos(final IRI identifier) {
        return mementoService.map(svc -> svc.list(identifier)).orElse(emptyList());
    }

    @Override
    public Stream<Triple> scan() {
        final String query = "SELECT id, interactionModel FROM metadata";
        return jdbi.withHandle(handle -> handle.createQuery(query).map((rs, ctx) ->
                    rdf.createTriple(rdf.createIRI(rs.getString("id")), type,
                        rdf.createIRI(rs.getString("interactionModel")))).stream());
    }

    @Override
    public Optional<Resource> get(final IRI identifier, final Instant time) {
        return mementoService.isPresent() ? mementoService.flatMap(svc -> svc.get(identifier, time)) : get(identifier);
    }

    @Override
    public Optional<Resource> get(final IRI identifier) {
        return DBResource.findResource(jdbi, identifier);
    }

    @Override
    public String generateIdentifier() {
        return supplier.get();
    }

    @Override
    public Stream<IRI> compact(final IRI identifier, final Instant from, final Instant until) {
        throw new UnsupportedOperationException("compact is not supported");
    }

    @Override
    public Stream<IRI> purge(final IRI identifier) {
        throw new UnsupportedOperationException("purge is not supported");
    }

    @Override
    public Future<Boolean> add(final IRI id, final Session session, final Dataset dataset) {
        final String q = "INSERT INTO audit (id, subject, predicate, object, lang, datatype) VALUES (?, ?, ?, ?, ?, ?)";
        return supplyAsync(() -> {
            try {
                jdbi.useHandle(handle -> dataset.getGraph(PreferAudit).ifPresent(graph -> {
                    final PreparedBatch batch = handle.prepareBatch(q);
                    graph.stream().forEach(triple -> batch
                            .bind(0, id.getIRIString())
                            .bind(1, ((IRI) triple.getSubject()).getIRIString())
                            .bind(2, triple.getPredicate().getIRIString())
                            .bind(3, getObjectValue(triple.getObject()))
                            .bind(4, getObjectLang(triple.getObject()))
                            .bind(5, getObjectDatatype(triple.getObject())).add());
                    batch.execute();
                }));

                return true;
            } catch (final Exception ex) {
                LOGGER.error("Error storing audit dataset: {}", ex.getMessage());
                throw new RuntimeTrellisException(ex);
            }
        });
    }

    @Override
    public Set<IRI> supportedInteractionModels() {
        return supportedIxnModels;
    }

    private void init() {
        jdbi.useTransaction(handle -> {
            final String auth = TRELLIS_DATA_PREFIX + "#auth";
            handle.execute(
                    "INSERT INTO metadata (id, interactionModel, modified, isPartOf, isDeleted, hasAcl)" +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    TRELLIS_DATA_PREFIX, LDP.BasicContainer.getIRIString(), now().toEpochMilli(), null, false, true);

            final PreparedBatch batch = handle.prepareBatch(
                "INSERT INTO acl (id, subject, predicate, object, lang, datatype) VALUES (?, ?, ?, ?, NULL, NULL)");
            batch.bind(0, TRELLIS_DATA_PREFIX).bind(1, auth).bind(2, ACL.mode.getIRIString())
                 .bind(3, ACL.Read.getIRIString()).add();
            batch.bind(0, TRELLIS_DATA_PREFIX).bind(1, auth).bind(2, ACL.mode.getIRIString())
                 .bind(3, ACL.Write.getIRIString()).add();
            batch.bind(0, TRELLIS_DATA_PREFIX).bind(1, auth).bind(2, ACL.mode.getIRIString())
                 .bind(3, ACL.Control.getIRIString()).add();
            batch.bind(0, TRELLIS_DATA_PREFIX).bind(1, auth).bind(2, ACL.agentClass.getIRIString())
                 .bind(3, FOAF.Agent.getIRIString()).add();
            batch.bind(0, TRELLIS_DATA_PREFIX).bind(1, auth).bind(2, ACL.accessTo.getIRIString())
                 .bind(3, TRELLIS_DATA_PREFIX).add();
            batch.execute();
        });
    }

    private Boolean createOrReplace(final IRI identifier, final Session session, final IRI ixnModel,
                    final Dataset dataset, final OperationType opType, final IRI container, final Binary binary) {

        // Set the LDP type
        dataset.add(PreferServerManaged, identifier, type, ixnModel);

        // Relocate some user-managed triples into the server-managed graph
        if (LDP.DirectContainer.equals(ixnModel) || LDP.IndirectContainer.equals(ixnModel)) {
            dataset.getGraph(PreferUserManaged).ifPresent(g -> {
                g.stream(identifier, LDP.membershipResource, null).findFirst().ifPresent(t -> {
                    // This allows for HTTP resource URL-based queries
                    dataset.add(PreferServerManaged, identifier, LDP.member, getBaseIRI(t.getObject()));
                    dataset.add(PreferServerManaged, identifier, LDP.membershipResource, t.getObject());
                });
                g.stream(identifier, LDP.hasMemberRelation, null).findFirst().ifPresent(t -> dataset
                                .add(PreferServerManaged, identifier, LDP.hasMemberRelation, t.getObject()));
                g.stream(identifier, LDP.isMemberOfRelation, null).findFirst().ifPresent(t -> dataset
                                .add(PreferServerManaged, identifier, LDP.isMemberOfRelation, t.getObject()));
                dataset.add(PreferServerManaged, identifier, LDP.insertedContentRelation,
                                g.stream(identifier, LDP.insertedContentRelation, null).map(Triple::getObject)
                                                .findFirst().orElse(LDP.MemberSubject));
            });
        }

        // Set the parent relationship
        if (nonNull(container)) {
            dataset.add(PreferServerManaged, identifier, DC.isPartOf, container);
        }

        if (nonNull(binary)) {
            dataset.add(PreferServerManaged, identifier, DC.hasPart, binary.getIdentifier());
            dataset.add(PreferServerManaged, binary.getIdentifier(), DC.modified,
                    rdf.createLiteral(binary.getModified().toString(), XSD.dateTime));
            binary.getMimeType().map(rdf::createLiteral).ifPresent(mimeType ->
                    dataset.add(PreferServerManaged, binary.getIdentifier(), DC.format, mimeType));
            binary.getSize().map(size -> rdf.createLiteral(size.toString(), XSD.long_)).ifPresent(size ->
                    dataset.add(PreferServerManaged, binary.getIdentifier(), DC.extent, size));
        }

        return storeAndNotify(identifier, session, ixnModel, dataset, opType, binary);
    }

    private Boolean storeAndNotify(final IRI identifier, final Session session, final IRI ixnModel,
            final Dataset dataset, final OperationType opType, final Binary binary) {
        try {
            jdbi.useTransaction(handle -> {
                handle.execute("DELETE FROM metadata WHERE id = ?", identifier.getIRIString());
                handle.execute(
                        "INSERT INTO metadata (id, interactionModel, modified, isPartOf, isDeleted, hasAcl)" +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                        identifier.getIRIString(), ixnModel.getIRIString(),
                        session.getCreated().toEpochMilli(),
                        dataset.stream(of(PreferServerManaged), identifier, DC.isPartOf, null)
                            .map(Quad::getObject).map(DBUtils::getObjectValue).findFirst().orElse(null),
                        opType == OperationType.DELETE,
                        dataset.stream(of(PreferAccessControl), null, null, null).findFirst().isPresent());

                handle.execute("DELETE FROM ldp WHERE id = ?", identifier.getIRIString());
                if (LDP.DirectContainer.equals(ixnModel) || LDP.IndirectContainer.equals(ixnModel)) {
                    handle.execute("INSERT INTO ldp (id, member, membershipResource, hasMemberRelation, " +
                            "isMemberOfRelation, insertedContentRelation) VALUES (?, ?, ?, ?, ?, ?)",
                            identifier.getIRIString(),
                            dataset.stream(of(PreferServerManaged), identifier, LDP.member, null)
                                .map(Quad::getObject).map(DBUtils::getObjectValue).findFirst().orElse(null),
                            dataset.stream(of(PreferServerManaged), identifier, LDP.membershipResource, null)
                                .map(Quad::getObject).map(DBUtils::getObjectValue).findFirst().orElse(null),
                            dataset.stream(of(PreferServerManaged), identifier, LDP.hasMemberRelation, null)
                                .map(Quad::getObject).map(DBUtils::getObjectValue).findFirst().orElse(null),
                            dataset.stream(of(PreferServerManaged), identifier, LDP.isMemberOfRelation, null)
                                .map(Quad::getObject).map(DBUtils::getObjectValue).findFirst().orElse(null),
                            dataset.stream(of(PreferServerManaged), identifier, LDP.insertedContentRelation, null)
                                .map(Quad::getObject).map(DBUtils::getObjectValue).findFirst().orElse(null));
                }

                handle.execute("DELETE FROM binary WHERE id = ?", identifier.getIRIString());
                if (nonNull(binary)) {
                    handle.execute("INSERT INTO binary (id, location, modified, format, size) VALUES (?, ?, ?, ?, ?)",
                            identifier.getIRIString(),
                            binary.getIdentifier().getIRIString(),
                            binary.getModified().toEpochMilli(),
                            binary.getMimeType().orElse(null),
                            binary.getSize().orElse(null));
                }

                handle.execute("DELETE FROM resource WHERE id = ?", identifier.getIRIString());
                dataset.getGraph(PreferUserManaged).ifPresent(graph -> {
                    final PreparedBatch batch = handle.prepareBatch(
                            "INSERT INTO resource (id, subject, predicate, object, lang, datatype) " +
                            "VALUES (?, ?, ?, ?, ?, ?)");
                    graph.stream().sequential().forEach(triple -> {
                        batch.bind(0, identifier.getIRIString())
                             .bind(1, ((IRI) triple.getSubject()).getIRIString())
                             .bind(2, triple.getPredicate().getIRIString())
                             .bind(3, getObjectValue(triple.getObject()))
                             .bind(4, getObjectLang(triple.getObject()))
                             .bind(5, getObjectDatatype(triple.getObject())).add();
                        if (batch.size() >= config.getOrDefault(BATCH_KEY, Integer.class, DEFAULT_BATCH_SIZE)) {
                            batch.execute();
                        }
                    });
                    batch.execute();
                });

                handle.execute("DELETE FROM extras WHERE subject = ?", identifier.getIRIString());
                dataset.getGraph(PreferUserManaged).ifPresent(graph -> {
                    final PreparedBatch batch = handle.prepareBatch(
                            "INSERT INTO extras (subject, predicate, object) VALUES (?, ?, ?)");
                    graph.stream(identifier, LDP.inbox, null).map(Triple::getObject).filter(t -> t instanceof IRI)
                        .map(t -> ((IRI) t).getIRIString()).findFirst().ifPresent(iri ->
                                batch.bind(0, identifier.getIRIString()).bind(1, LDP.inbox.getIRIString()).bind(2, iri)
                                     .add());

                    graph.stream(identifier, OA.annotationService, null).map(Triple::getObject)
                         .filter(t -> t instanceof IRI).map(t -> ((IRI) t).getIRIString()).findFirst().ifPresent(iri ->
                                batch.bind(0, identifier.getIRIString()).bind(1, OA.annotationService.getIRIString())
                                     .bind(2, iri).add());

                    batch.execute();
                });

                handle.execute("DELETE FROM acl WHERE id = ?", identifier.getIRIString());
                dataset.getGraph(PreferAccessControl).ifPresent(graph -> {
                    final PreparedBatch batch = handle.prepareBatch(
                        "INSERT INTO acl (id, subject, predicate, object, lang, datatype) VALUES (?, ?, ?, ?, ?, ?)");
                    graph.stream().sequential().forEach(triple -> {
                        batch.bind(0, identifier.getIRIString())
                             .bind(1, ((IRI) triple.getSubject()).getIRIString())
                             .bind(2, triple.getPredicate().getIRIString())
                             .bind(3, getObjectValue(triple.getObject()))
                             .bind(4, getObjectLang(triple.getObject()))
                             .bind(5, getObjectDatatype(triple.getObject())).add();
                        if (batch.size() >= config.getOrDefault(BATCH_KEY, Integer.class, DEFAULT_BATCH_SIZE)) {
                            batch.execute();
                        }
                    });
                    batch.execute();
                });
            });

            if (opType != OperationType.DELETE) {
                mementoService.ifPresent(svc -> get(identifier).ifPresent(res ->
                            svc.put(identifier, session.getCreated(), res.stream())));
            }
            emitEvents(identifier, session, opType, dataset);
        } catch (final Exception ex) {
            LOGGER.error("Could not update data: {}", ex.getMessage());
            throw new RuntimeTrellisException(ex);
        }
        return true;
    }

    private void emitEventsForAdjacentResources(final EventService svc, final IRI parent, final Session session,
                        final OperationType opType) {
        final Literal time = rdf.createLiteral(session.getCreated().toString(), XSD.dateTime);
        // TODO - determine which resources changed, send events for each
    }

    private void emitEvents(final IRI identifier, final Session session, final OperationType opType,
            final Dataset dataset) {

        // Get the base URL
        final Optional<String> baseUrl = session.getProperty(TRELLIS_SESSION_BASE_URL);
        final IRI inbox = dataset.getGraph(PreferUserManaged)
            .flatMap(graph -> graph.stream(null, LDP.inbox, null).map(Triple::getObject)
                    .filter(term -> term instanceof IRI).map(term -> (IRI) term).findFirst())
            .orElse(null);
        final List<IRI> targetTypes = dataset.stream()
            .filter(quad -> quad.getGraphName().filter(isUserGraph.or(isServerGraph)).isPresent())
            .filter(quad -> quad.getPredicate().equals(type))
            .flatMap(quad -> quad.getObject() instanceof IRI ? Stream.of((IRI) quad.getObject()) : empty())
            .distinct().collect(toList());

        eventService.ifPresent(svc -> {
            svc.emit(new SimpleEvent(getUrl(identifier, baseUrl),
                        asList(session.getAgent()), asList(PROV.Activity, OperationType.asIRI(opType)),
                        targetTypes, inbox));
            getContainer(identifier).ifPresent(parent ->
                    emitEventsForAdjacentResources(svc, parent, session, opType));
        });
    }

    private enum OperationType {
        DELETE, CREATE, REPLACE;

        static IRI asIRI(final OperationType opType) {
            switch (opType) {
                case DELETE:
                  return AS.Delete;
                case CREATE:
                  return AS.Create;
                case REPLACE:
                default:
                  return AS.Update;
            }
        }
    }

    private String getUrl(final IRI identifier, final Optional<String> baseUrl) {
        if (baseUrl.isPresent()) {
            return toExternal(identifier, baseUrl.get()).getIRIString();
        }
        LOGGER.warn("No baseURL defined. Emitting message with resource's internal IRI: {}", identifier);
        return identifier.getIRIString();
    }

}
