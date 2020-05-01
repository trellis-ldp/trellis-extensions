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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An {@link InputStream} that wraps a {@link ByteBuffer} to avoid copying byte arrays.
 *
 */
class ByteBufferInputStream extends InputStream {

    private static final int ENDOFSTREAM = -1;

    private final ByteBuffer buffer;
    private int readLimit;
    private int readSinceMark;

    ByteBufferInputStream(final ByteBuffer b) {
        // https://github.com/trellis-ldp/trellis-cassandra/issues/51#issuecomment-474970424
        this.buffer = (ByteBuffer) b.mark();
        this.readSinceMark = 0;
        this.readLimit = buffer.remaining();
    }

    @Override
    public long skip(final long n) throws IOException {
        final int toSkip = (int) Math.min(n, buffer.remaining());
        buffer.position(buffer.position() + toSkip);
        return toSkip;
    }

    @Override
    public synchronized void mark(final int limit) {
        this.readLimit = limit;
        buffer.mark();
    }

    @Override
    public synchronized void reset() throws IOException {
        if (readSinceMark > readLimit)
            throw new IOException("Cannot read past read limit set by previous call to mark(" + readLimit + ")!");
        buffer.reset();
        readSinceMark = 0;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int available() {
        return buffer.remaining();
    }

    @Override
    public int read() {
        if (!buffer.hasRemaining()) return ENDOFSTREAM;
        readSinceMark++;
        return Byte.toUnsignedInt(buffer.get());
    }

    @Override
    public int read(final byte[] bytes, final int offset, final int length) {
        if (!buffer.hasRemaining()) return ENDOFSTREAM;
        final int availableLength = Math.min(length, buffer.remaining());
        buffer.get(bytes, offset, availableLength);
        readSinceMark += availableLength;
        return availableLength;
    }
}
