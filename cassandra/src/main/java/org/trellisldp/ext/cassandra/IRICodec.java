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
import static java.nio.ByteBuffer.wrap;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

import java.nio.ByteBuffer;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.trellisldp.api.RDFFactory;

/**
 * (De)serializes Commons RDF {@link IRI}s (out of)into Cassandra fields.
 * 
 * @author ajs6f
 *
 */
class IRICodec extends CassandraCodec<IRI> {

    private static final GenericType<IRI> IRI_TYPE = GenericType.of(IRI.class);

    /**
     * Singleton instance.
     */
    static final IRICodec IRI_CODEC = new IRICodec();

    protected static final int CACHE_MAXIMUM_SIZE = 10 ^ 6;

    protected static final RDF rdf = RDFFactory.getInstance();

    @Override
    public DataType getCqlType() {
        return TEXT;
    }

    @Override
    public GenericType<IRI> getJavaType() {
        return IRI_TYPE;
    }

    @Override
    public String format(final IRI v) {
        return v != null ? v.getIRIString() : null;
    }

    @Override
    public ByteBuffer encode(final IRI iri, final ProtocolVersion protocolVersion) {
        return iri != null ? wrap(format(iri).getBytes(UTF_8)) : null;
    }

    @Override
    public IRI decode(final ByteBuffer bytes, final ProtocolVersion protocolVersion) {
        return bytes == null ? null : parse(new String(bytesFromBuffer(bytes), UTF_8));
    }

    @Override
    public IRI parse(final String v) {
        if (v == null || v.isEmpty()) return null;
        return rdf.createIRI(v);
    }
}
