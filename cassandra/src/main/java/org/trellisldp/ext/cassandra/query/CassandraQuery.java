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
package org.trellisldp.ext.cassandra.query;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
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

    private static final Logger LOGGER = getLogger(CassandraQuery.class);

    /**
     * A Cassandra session for use with this query.
     */
    protected final CqlSession session;
    protected final ConsistencyLevel consistency;
    protected final String queryString;

    protected CompletionStage<PreparedStatement> preparedStmtAsync;

    /**
     * Worker threads that read and write from and to Cassandra. Reading and writing are thereby uncoupled from threads
     * calling into this class.
     */
    protected final Executor writeWorkers = newCachedThreadPool();
    protected final Executor readBinaryWorkers = newCachedThreadPool();


     /**
     * For use with RESTeasy and CDI proxies.
     *
     * @apiNote This construtor is used by CDI runtimes that require a public, no-argument constructor.
     *          It should not be invoked directly in user code.
     */
    protected CassandraQuery() {
        this(null, null, ConsistencyLevel.ONE);
    }

    /**
     * @param session a {@link CqlSession} to the Cassandra cluster
     * @param queryString the CQL string for this query
     * @param consistency the {@link ConsistencyLevel} to use for executions of this query
     */
    public CassandraQuery(final CqlSession session, final String queryString, final ConsistencyLevel consistency) {
        this.session = session;
        this.consistency = consistency;
        this.queryString = queryString;
        if (session != null) {
            LOGGER.debug("Preparing async statement {}", queryString);
            this.preparedStmtAsync = session.prepareAsync(queryString);
        }
    }

    /**
     * @return the {@link PreparedStatement} that underlies this query
     */
    protected CompletionStage<PreparedStatement> preparedStatementAsync() {
        return preparedStmtAsync;
    }

    /**
     * @param statement the CQL statement to execute
     * @return the results of that statement
     */
    protected ResultSet executeSyncRead(final BoundStatement statement) {
        return session.execute(statement);
    }
}
