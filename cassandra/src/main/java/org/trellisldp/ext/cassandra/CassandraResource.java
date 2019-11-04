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

import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.BinaryMetadata.builder;
import static org.trellisldp.vocabulary.LDP.Container;
import static org.trellisldp.vocabulary.LDP.NonRDFSource;
import static org.trellisldp.vocabulary.LDP.getSuperclassOf;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.Resource;

class CassandraResource implements Resource {

    private static final Logger log = getLogger(CassandraResource.class);

    private final IRI identifier;
    private final IRI container;
    private final IRI interactionModel;

    final boolean hasAcl;
    final boolean isContainer;

    private final Instant modified;

    private final BinaryMetadata binary;

    private final Dataset dataset;

    public CassandraResource(final IRI id, final IRI ixnModel, final boolean hasAcl, final IRI binaryIdentifier,
            final String mimeType, final IRI container, final Instant modified, final Dataset dataset) {
        this.identifier = id;
        this.interactionModel = ixnModel;
        this.isContainer = Container.equals(ixnModel) || Container.equals(getSuperclassOf(ixnModel));
        this.hasAcl = hasAcl;
        this.container = container;
        log.trace("Resource is {}a container.", !isContainer ? "not " : "");
        this.modified = modified;
        final boolean isBinary = NonRDFSource.equals(ixnModel);
        this.binary = isBinary ? builder(binaryIdentifier).mimeType(mimeType).build() : null;
        log.trace("Resource is {}a NonRDFSource.", !isBinary ? "not " : "");
        this.dataset = dataset;
    }

    @Override
    public IRI getIdentifier() {
        return identifier;
    }

    /**
     * @return a container for this resource
     */
    @Override
    public Optional<IRI> getContainer() {
        return Optional.ofNullable(container);
    }

    @Override
    public IRI getInteractionModel() {
        return interactionModel;
    }

    @Override
    public Instant getModified() {
        return modified;
    }

    @Override
    public boolean hasAcl() {
        return hasAcl;
    }

    @Override
    public Optional<BinaryMetadata> getBinaryMetadata() {
        return Optional.ofNullable(binary);
    }

    @Override
    public Dataset dataset() {
        return dataset;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<Quad> stream() {
        return (Stream<Quad>) dataset.stream();
    }
}
