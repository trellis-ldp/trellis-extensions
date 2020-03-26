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

import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toSet;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;

import com.datastax.oss.driver.api.core.cql.Row;

import java.time.Instant;
import java.util.Set;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;
import org.trellisldp.vocabulary.Trellis;

interface CassandraBuildingService {

    default Resource parse(final Row metadata, final Logger log, final IRI id) {
        if (metadata == null) {
            log.debug("{} was not found.", id);
            return MISSING_RESOURCE;
        }

        log.debug("{} was found, computing metadata.", id);
        final IRI ixnModel = metadata.get("interactionModel", IRI.class);
        log.debug("Found interactionModel = {} for resource {}", ixnModel, id);
        final IRI container = metadata.get("container", IRI.class);
        log.debug("Found container = {} for resource {}", container, id);

        final IRI binaryId = metadata.get("binaryIdentifier", IRI.class);
        log.debug("Found binaryIdentifier = {} for resource {}", binaryId, id);
        final String mimeType = metadata.getString("mimetype");
        log.debug("Found mimeType = {} for resource {}", mimeType, id);

        final Instant modified = metadata.get("modified", Instant.class);
        log.debug("Found modified = {} for resource {}", modified, id);
        final Dataset dataset = metadata.get("quads", Dataset.class);
        log.debug("Found dataset = {} for resource {}", dataset, id);

        final BinaryMetadata binary = binaryId != null ?
            BinaryMetadata.builder(binaryId).mimeType(mimeType).build() : null;

        final Set<IRI> graphs = dataset.getGraphNames().filter(IRI.class::isInstance).map(IRI.class::cast)
            .filter(isEqual(Trellis.PreferUserManaged).or(isEqual(Trellis.PreferServerManaged)).negate())
            .collect(toSet());

        final Metadata meta = Metadata.builder(id).container(container).interactionModel(ixnModel)
            .metadataGraphNames(graphs).binary(binary).build();

        return new CassandraResource(meta, modified, dataset);
    }
}
