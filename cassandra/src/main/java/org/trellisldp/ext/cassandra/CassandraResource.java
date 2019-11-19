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

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;

class CassandraResource implements Resource {

    private static final Logger log = getLogger(CassandraResource.class);

    private final Metadata metadata;
    private final Dataset dataset;
    private final Instant modified;

    public CassandraResource(final Metadata metadata, final Instant modified, final Dataset dataset) {
        this.metadata = metadata;
        this.dataset = dataset;
        this.modified = modified;
    }

    @Override
    public IRI getIdentifier() {
        return metadata.getIdentifier();
    }

    @Override
    public Optional<IRI> getContainer() {
        return metadata.getContainer();
    }

    @Override
    public IRI getInteractionModel() {
        return metadata.getInteractionModel();
    }

    @Override
    public Instant getModified() {
        return modified;
    }

    @Override
    public boolean hasAcl() {
        return metadata.getHasAcl();
    }

    @Override
    public Optional<BinaryMetadata> getBinaryMetadata() {
        return metadata.getBinary();
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
