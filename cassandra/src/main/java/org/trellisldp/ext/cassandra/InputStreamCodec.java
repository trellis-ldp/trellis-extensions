/*
 * Copyright (c) 2017 - 2020 Aaron Coburn and individual contributors
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

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import org.apache.commons.io.IOUtils;

/**
 * Serializes {@link InputStream}s in Cassandra text fields.
 *
 */
class InputStreamCodec extends CassandraCodec<InputStream> {

    public static final InputStreamCodec INPUTSTREAM_CODEC = new InputStreamCodec();

    private static final GenericType<InputStream> INPUTSTREAM_TYPE = GenericType.of(InputStream.class);

    @Override
    public DataType getCqlType() {
        return DataTypes.BLOB;
    }

    @Override
    public GenericType<InputStream> getJavaType() {
        return INPUTSTREAM_TYPE;
    }

    @Override
    public ByteBuffer encode(final InputStream value, final ProtocolVersion protocolVersion) {
        return value == null ? null : ByteBuffer.wrap(toBytes(value));
    }

    @Override
    public InputStream decode(final ByteBuffer bytes, final ProtocolVersion protocolVersion) {
        return bytes == null ? null : new ByteBufferInputStream(bytes);
    }

    @Override
    public InputStream parse(final String value) {
        if (value == null) return null;
        final byte[] bytes = value.getBytes(UTF_8);
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new ByteBufferInputStream(buffer);
    }

    private static byte[] toBytes(final InputStream in) {
        try {
            return IOUtils.toByteArray(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String format(final InputStream in) {
        return in == null ? null : new String(toBytes(in), UTF_8);
    }
}
