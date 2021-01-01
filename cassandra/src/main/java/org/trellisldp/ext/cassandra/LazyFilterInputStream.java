/*
 * Copyright (c) 2021 Aaron Coburn and individual contributors
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Like {@link FilterInputStream} but lazier; does not fill the slot for wrapped {@link InputStream} until
 * {@link #initialize()} is called. Not thread-safe!
 */
public abstract class LazyFilterInputStream extends InputStream {

    private InputStream wrapped;

    private InputStream wrapped() {
        if (wrapped == null) wrapped = initialize();
        return wrapped;
    }

    /**
     * Implementations of this method should use {@link #initialize} to fill {@link #wrapped}.
     * @return a wrapped {@link InputStream}
     */
    protected abstract InputStream initialize();

    @Override
    public int read() throws IOException {
        return wrapped().read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return wrapped().read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return wrapped().read(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return wrapped().skip(n);
    }

    @Override
    public int available() throws IOException {
        return wrapped().available();
    }

    @Override
    public void close() throws IOException {
        if (wrapped != null) wrapped.close();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        wrapped().mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        wrapped().reset();
    }

    @Override
    public boolean markSupported() {
        return wrapped().markSupported();
    }
}
