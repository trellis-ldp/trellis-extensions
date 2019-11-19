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

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;

import io.smallrye.config.inject.ConfigProducer;

import javax.inject.Inject;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.trellisldp.api.DefaultIdentifierService;

@EnabledIfSystemProperty(named = "trellis.test.cassandra.enable", matches = "true")
@ExtendWith(WeldJunit5Extension.class)
class CassandraContextIT {

    @WeldSetup
    private WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                       .beanClasses(
                                           CassandraContext.class,
                                           DefaultIdentifierService.class,
                                           ConfigProducer.class));

    @Inject
    private CassandraContext ctx;

    @Test
    void testCassandraContext() {
        assertNotNull(ctx);
    }

    @Test
    void testContactPort() {
        assertNotNull(ctx.session());
        assertFalse(ctx.session().isClosed());
    }

    @Test
    void testBinaryReadConsistency() {
        assertEquals(DefaultConsistencyLevel.ALL, ctx.getBinaryReadConsistency());
    }

    @Test
    void testBinaryWriteConsistency() {
        assertEquals(DefaultConsistencyLevel.ALL, ctx.getBinaryWriteConsistency());
    }

    @Test
    void testRdfReadConsistency() {
        assertEquals(DefaultConsistencyLevel.ONE, ctx.getRdfReadConsistency());
    }

    @Test
    void testRdfWriteConsistency() {
        assertEquals(DefaultConsistencyLevel.ONE, ctx.getRdfWriteConsistency());
    }
}
