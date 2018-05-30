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
package org.trellisldp.ext.jdbc;

import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.builder;
import static java.util.stream.Stream.empty;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.RDFUtils.TRELLIS_SESSION_BASE_URL;
import static org.trellisldp.api.RDFUtils.getInstance;
import static org.trellisldp.ext.jdbc.JDBCUtils.getBaseIRI;
import static org.trellisldp.vocabulary.RDF.type;
import static org.trellisldp.vocabulary.Trellis.DeletedResource;
import static org.trellisldp.vocabulary.Trellis.PreferAudit;
import static org.trellisldp.vocabulary.Trellis.PreferServerManaged;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.Range;
import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;
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
import org.trellisldp.vocabulary.AS;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.XSD;

/**
 * A triplestore-based implementation of the Trellis ResourceService API.
 */
public class JDBCResourceService extends DefaultAuditService implements ResourceService {

    private static final String PARENT = "parent";
    private static final String MODIFIED = "modified";
    private static final String MEMBER = "member";

    private static final Logger LOGGER = getLogger(JDBCResourceService.class);
    private static final RDF rdf = getInstance();

    private static final Predicate<BlankNodeOrIRI> isUserGraph = PreferUserManaged::equals;
    private static final Predicate<BlankNodeOrIRI> isServerGraph = PreferServerManaged::equals;

    private final Supplier<String> supplier;
    private final Connection connection;
    private final Optional<EventService> eventService;
    private final Optional<MementoService> mementoService;
    private final Set<IRI> supportedIxnModels;

    /**
     * Create a triplestore-backed resource service.
     * @param connection the connection to an RDF datastore
     * @param identifierService an ID supplier service
     * @param mementoService a service for memento resources
     * @param eventService an event service
     */
    @Inject
    public JDBCResourceService(final Connection connection, final IdentifierService identifierService,
            final MementoService mementoService, final EventService eventService) {
        requireNonNull(connection, "Connection may not be null!");
        requireNonNull(identifierService, "IdentifierService may not be null!");
        this.connection = connection;
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
            final Instant eventTime = now();
            dataset.add(PreferServerManaged, identifier, DC.type, DeletedResource);
            dataset.add(PreferServerManaged, identifier, type, LDP.Resource);
            return storeAndNotify(identifier, session, dataset, eventTime, OperationType.DELETE);
        });
    }

    @Override
    public Future<Boolean> replace(final IRI id, final Session session, final IRI ixnModel, final Dataset dataset,
                    final IRI container, final Binary binary) {
        LOGGER.debug("Updating: {}", id);
        return supplyAsync(() ->
                createOrReplace(id, session, ixnModel, dataset, OperationType.REPLACE, container, binary));
    }

    private Boolean createOrReplace(final IRI identifier, final Session session, final IRI ixnModel,
                    final Dataset dataset, final OperationType opType, final IRI container, final Binary binary) {
        final Instant eventTime = now();

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

        return storeAndNotify(identifier, session, dataset, eventTime, opType);
    }

    private Boolean storeAndNotify(final IRI identifier, final Session session, final Dataset dataset,
            final Instant eventTime, final OperationType opType) {
        final Literal time = rdf.createLiteral(eventTime.toString(), XSD.dateTime);
        try {
            //connection.update(buildUpdateRequest(identifier, time, dataset, type));
            if (opType != OperationType.DELETE) {
                mementoService.ifPresent(svc -> get(identifier).ifPresent(res ->
                            svc.put(identifier, eventTime, res.stream())));
            }
            emitEvents(identifier, session, opType, time, dataset);
        } catch (final Exception ex) {
            LOGGER.error("Could not update data: {}", ex.getMessage());
            throw new RuntimeTrellisException(ex);
        }
        return true;
    }

    @Override
    public List<Range<Instant>> getMementos(final IRI identifier) {
        return mementoService.map(svc -> svc.list(identifier)).orElse(emptyList());
    }

    private void emitEvents(final IRI identifier, final Session session, final OperationType opType,
            final Literal time, final Dataset dataset) {

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
                    emitEventsForAdjacentResources(svc, parent, session, opType, time));
        });
    }

    private void emitEventsForAdjacentResources(final EventService svc, final IRI parent, final Session session,
                        final OperationType opType, final Literal time) {
    }

    /**
     * This is equivalent to the SPARQL below.
     *
     * <p><pre><code>
     * WITH trellis:PreferServerManaged
     *   DELETE { ?member dc:modified ?modified }
     *   INSERT { ?member dc:modified TIME }
     *   WHERE {
     *     IDENTIFIER dc:isPartOf ?parent .
     *     ?parent ldp:membershipResource ?member .
     *     ?parent ldp:hasMemberRelation ?any .
     *     ?member dc:modified ?modified
     * }
     * </code></pre></p>
     */
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

    @Override
    public Stream<Triple> scan() {
        final Stream.Builder<Triple> builder = builder();
        return builder.build();
    }

    private void init() {
        final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);
    }

    @Override
    public Optional<Resource> get(final IRI identifier, final Instant time) {
        return mementoService.isPresent() ? mementoService.flatMap(svc -> svc.get(identifier, time)) : get(identifier);
    }

    @Override
    public Optional<Resource> get(final IRI identifier) {
        return JDBCResource.findResource(connection, identifier);
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
        return supplyAsync(() -> {
            final IRI graphName = rdf.createIRI(id.getIRIString() + "?ext=audit");
            try (final Dataset data = rdf.createDataset()) {
                dataset.getGraph(PreferAudit).ifPresent(g ->
                        g.stream().forEach(t -> data.add(graphName, t.getSubject(), t.getPredicate(), t.getObject())));
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
}
