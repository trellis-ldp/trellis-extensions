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
package org.trellisldp.ext.cassandra.query;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.slf4j.Logger;

/**
 * A context for queries run against Cassandra. All requests to Cassandra should go through a subclass.
 *
 */
public class CassandraQuery {

    private static final Logger log = getLogger(CassandraQuery.class);

    /**
     * A Cassandra session for use with this query.
     */
    protected final CqlSession session;

    /**
     * Worker threads that read and write from and to Cassandra. Reading and writing are thereby uncoupled from threads
     * calling into this class.
     */
    protected final Executor writeWorkers = newCachedThreadPool();
    protected final Executor readWorkers = newCachedThreadPool();
    protected final Executor readBinaryWorkers = newCachedThreadPool();

    private final PreparedStatement statement;

    private final ConsistencyLevel consistency;

    /**
     * @return the {@link PreparedStatement} that underlies this query
     */
    protected PreparedStatement preparedStatement() {
        return statement;
    }

    /**
     * @param session a {@link CqlSession} to the Cassandra cluster
     * @param queryString the CQL string for this query
     * @param consistency the {@link ConsistencyLevel} to use for executions of this query
     */
    public CassandraQuery(final CqlSession session, final String queryString, final ConsistencyLevel consistency) {
        this.session = session;
        this.statement = session.prepare(queryString);
        this.consistency = consistency;
    }

    /**
     * @param statement the CQL statement to execute
     * @return when and whether the statement completed
     */
    protected CompletionStage<Void> executeWrite(final BoundStatement statement) {
        final String queryString = statement.getPreparedStatement().getQuery();
        log.debug("Executing CQL write: {}", queryString);
        final BoundStatement consistentStatement = statement.setConsistencyLevel(consistency);
        return session.executeAsync(consistentStatement)
                        .thenAccept(r -> log.debug("Executed CQL write: {}", queryString));
    }

    /**
     * @param statement the CQL statement to execute
     * @return the results of that statement
     */
    protected CompletionStage<AsyncResultSet> executeRead(final BoundStatement statement) {
        return session.executeAsync(statement);
    }

    /**
     * @param statement the CQL statement to execute
     * @return the results of that statement
     */
    protected ResultSet executeSyncRead(final BoundStatement statement) {
        return session.execute(statement);
    }
}
