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

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.jena.riot.Lang.NQUADS;
import static org.apache.jena.riot.RDFDataMgr.read;
import static org.apache.jena.riot.RDFDataMgr.writeQuads;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.query.DatasetFactory;

class DatasetCodec extends CassandraCodec<Dataset> {

    private static final GenericType<Dataset> DATASET_TYPE = GenericType.of(Dataset.class);

    static final DatasetCodec DATASET_CODEC = new DatasetCodec();

    private static final JenaRDF rdf = new JenaRDF();

    @Override
    public DataType getCqlType() {
        return TEXT;
    }

    @Override
    public GenericType<Dataset> getJavaType() {
        return DATASET_TYPE;
    }

    @Override
    public ByteBuffer encode(final Dataset dataset, final ProtocolVersion protocolVersion) {
        if (dataset == null || dataset.size() == 0) return null;
        return ByteBuffer.wrap(toNQuads(dataset));
    }

    private byte[] toNQuads(final Dataset dataset) {
        try (final ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            writeQuads(bytes, dataset.stream().map(rdf::asJenaQuad).iterator());
            return bytes.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException("Dataset could not be serialized!", e);
        }
    }

    @Override
    public Dataset decode(final ByteBuffer buffer, final ProtocolVersion protocolVersion) {
        if (buffer == null) return rdf.createDataset();
        return fromNQuads(bytesFromBuffer(buffer));
    }

    private Dataset fromNQuads(final byte[] bytes) {
        final org.apache.jena.query.Dataset dataset = DatasetFactory.create();
        read(dataset, new ByteArrayInputStream(bytes), null, NQUADS);
        return rdf.asDataset(dataset);
    }

    @Override
    public Dataset parse(final String quads) {
        if (quads == null || quads.isEmpty()) return rdf.createDataset();
        return fromNQuads(quads.getBytes(UTF_8));
    }

    @Override
    public String format(final Dataset dataset) {
        if (dataset == null || dataset.size() == 0) return null;
        return new String(toNQuads(dataset), UTF_8);
    }
}
