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
package org.trellisldp.ext.db.webapp;

import static com.google.common.cache.CacheBuilder.newBuilder;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.trellisldp.api.RuntimeTrellisException;

class TrellisCacheTest {

    @Test
    void testCache() {
        final TrellisCache<String, String> cache = new TrellisCache<>(newBuilder().build());
        assertEquals("OK", cache.get("any", val -> "OK"));
        assertEquals("OK", cache.get("value", val -> "OK"));
    }

    @Test
    void testCacheException() throws Exception {
        final TrellisCache<String, String> cache = new TrellisCache<>(newBuilder().build());

        assertThrows(RuntimeTrellisException.class, () -> cache.get("any", val -> {
            throwsSneakyIOException();
            return "value";
        }));

    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(final Throwable e) throws T {
        throw (T) e;
    }

    private static void throwsSneakyIOException() {
        sneakyThrow(new IOException("sneaky"));
    }
}
