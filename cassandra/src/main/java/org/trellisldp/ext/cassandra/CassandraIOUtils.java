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
package org.trellisldp.ext.cassandra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.jena.riot.Lang.NQUADS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.RDF;
import org.apache.jena.commonsrdf.JenaCommonsRDF;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.trellisldp.api.RDFFactory;
import org.trellisldp.vocabulary.Trellis;


public final class CassandraIOUtils {

    private static final RDF rdf = RDFFactory.getInstance();

    /**
     * Serialize a dataset into an nquads string.
     * @param dataset the dataset
     * @return an nquads string
     */
    public static String serialize(final Dataset dataset) {
        if (dataset == null || dataset.size() == 0) {
            return null;
        }
        return toNQuads(dataset);
    }

    /**
     * Parse an nquads string into a dataset.
     * @param data the nquads
     * @return the dataset
     */
    public static Dataset parse(final String data) {
        if (data == null) {
            return rdf.createDataset();
        }
        return fromNQuads(data);
    }

    static String toNQuads(final Dataset dataset) {
        try (final ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            RDFDataMgr.writeQuads(bytes, dataset.stream().filter(quad ->
                        !quad.getGraphName().filter(Trellis.PreferServerManaged::equals).isPresent())
                    .map(JenaCommonsRDF::toJena).iterator());
            return bytes.toString(UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("Dataset could not be serialized!", e);
        }
    }

    static Dataset fromNQuads(final String data) {
        final org.apache.jena.query.Dataset dataset = DatasetFactory.create();
        RDFParser.fromString(data).lang(NQUADS).parse(dataset);
        return JenaCommonsRDF.fromJena(dataset.asDatasetGraph());
    }

    private CassandraIOUtils() {
        // Prevent instantiation.
    }
}
