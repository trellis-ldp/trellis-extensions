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

import com.datastax.oss.driver.api.core.type.codec.TypeCodec;

import java.nio.ByteBuffer;
import java.util.Arrays;

abstract class CassandraCodec<T>  implements TypeCodec<T> {

    protected byte[] bytesFromBuffer(final ByteBuffer buffer) {
        final int length = buffer.remaining();

        // can we go get the buffer's backing array?
        if (buffer.hasArray()) {
            final int offset = buffer.arrayOffset() + buffer.position();
            final byte[] bufferArray = buffer.array();
            // try and take the array wholesale
            if (offset == 0 && length == bufferArray.length) return bufferArray;
            // but if we can't, copy out the relevant range
            return Arrays.copyOfRange(bufferArray, offset, offset + length);
        }
        // no backing array, so we have to copy the buffer and copy the bytes out
        final byte[] bytes = new byte[length];
        buffer.duplicate().get(bytes);
        return bytes;
    }

}
