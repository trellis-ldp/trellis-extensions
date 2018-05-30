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
package org.trellisldp.ext.app.jdbc;

import static com.codahale.metrics.health.HealthCheck.Result.healthy;
import static com.codahale.metrics.health.HealthCheck.Result.unhealthy;

import com.codahale.metrics.health.HealthCheck;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Check the health of the Database connection.
 */
public class DatabaseConnectionHealthCheck extends HealthCheck {

    private final Connection connection;

    /**
     * Create an object that checks the health of a Database Connection.
     * @param connection the database Connection
     */
    public DatabaseConnectionHealthCheck(final Connection connection) {
        this.connection = connection;
    }

    @Override
    protected HealthCheck.Result check() throws InterruptedException {
        try {
            if (!connection.isClosed()) {
                return healthy("Database Connection is open.");
            }
        } catch (final SQLException ex) {
            // TODO log this error
        }
        return unhealthy("Database Connection is closed.");
    }
}
