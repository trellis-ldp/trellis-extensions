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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Check the health of the cassandra connection.
 */
@Liveness
@ApplicationScoped
public class CassandraLivenessCheck implements HealthCheck {

    private final CqlSession session;

    /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    public CassandraLivenessCheck() {
        this(null);
    }

    /**
     * Create a cassandra connection health checker.
     * @param session the cassandra session
     */
    @Inject
    public CassandraLivenessCheck(final CqlSession session) {
        this.session = session;
    }

    @Override
    public HealthCheckResponse call() {
        if (session != null) {
            final ResultSet res = session.execute("SELECT identifier FROM mutabledata LIMIT 1");

            return HealthCheckResponse.named(CassandraLivenessCheck.class.getSimpleName())
                .state(res.getExecutionInfo().getErrors().isEmpty()).build();
        }
        return HealthCheckResponse.named(CassandraLivenessCheck.class.getSimpleName()).down().build();
    }
}
