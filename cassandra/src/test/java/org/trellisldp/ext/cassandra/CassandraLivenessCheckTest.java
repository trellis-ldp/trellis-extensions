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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.metadata.Node;

import java.util.AbstractMap.SimpleEntry;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

class CassandraLivenessCheckTest {

    @Test
    void testUnhealthyDefault() {
        final HealthCheck check = new CassandraLivenessCheck();
        assertEquals(HealthCheckResponse.State.DOWN, check.call().getState(), "Connection isn't healthy!");
    }

    @Test
    void testHealthy() {
        final CqlSession mockSession = mock(CqlSession.class);
        final ResultSet mockResultSet = mock(ResultSet.class);
        final ExecutionInfo mockExecutionInfo = mock(ExecutionInfo.class);
        when(mockSession.execute(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.getExecutionInfo()).thenReturn(mockExecutionInfo);
        when(mockExecutionInfo.getErrors()).thenReturn(emptyList());

        final HealthCheck check = new CassandraLivenessCheck(mockSession);
        assertEquals(HealthCheckResponse.State.UP, check.call().getState(), "Connection isn't healthy!");
    }

    @Test
    void testUnhealthy() {
        final CqlSession mockSession = mock(CqlSession.class);
        final ResultSet mockResultSet = mock(ResultSet.class);
        final ExecutionInfo mockExecutionInfo = mock(ExecutionInfo.class);
        final Node mockNode = mock(Node.class);
        when(mockSession.execute(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.getExecutionInfo()).thenReturn(mockExecutionInfo);
        when(mockExecutionInfo.getErrors()).thenReturn(singletonList(
                    new SimpleEntry<>(mockNode, new RuntimeException("Expected exception."))));

        final HealthCheck check = new CassandraLivenessCheck(mockSession);
        assertEquals(HealthCheckResponse.State.DOWN, check.call().getState(),
                "Connection doesn't report as unhealthy!");
    }
}
