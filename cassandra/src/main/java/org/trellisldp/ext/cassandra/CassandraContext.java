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

import static java.lang.Integer.parseInt;
import static java.net.InetSocketAddress.createUnresolved;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.ext.cassandra.IRICodec.IRI_CODEC;
import static org.trellisldp.ext.cassandra.InputStreamCodec.INPUTSTREAM_CODEC;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;

import java.net.InetSocketAddress;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

@ApplicationScoped
public class CassandraContext {

    private static final Logger LOGGER = getLogger(CassandraContext.class);
    private static final String ONE = "ONE";
    private static final TypeCodec<?>[] STANDARD_CODECS = new TypeCodec<?>[] { INPUTSTREAM_CODEC, IRI_CODEC };

    private CqlSession session;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.keyspace", defaultValue = "trellis")
    String keyspace;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.datacenter", defaultValue = "datacenter1")
    String datacenter;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.contact-port", defaultValue = "9042")
    String contactPort;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.contact-address", defaultValue = "localhost")
    String contactAddress;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.binary-read-consistency", defaultValue = ONE)
    String binaryReadConsistency;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.binary-write-consistency", defaultValue = ONE)
    String binaryWriteConsistency;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.rdf-read-consistency", defaultValue = ONE)
    String rdfReadConsistency;

    @Inject
    @ConfigProperty(name = "trellis.cassandra.rdf-write-consistency", defaultValue = ONE)
    String rdfWriteConsistency;

    /**
     * @return the read-consistency to use querying Cassandra binary data
     */
    @Produces
    @BinaryReadConsistency
    public DefaultConsistencyLevel getBinaryReadConsistency() {
        return DefaultConsistencyLevel.valueOf(binaryReadConsistency);
    }

    /**
     * @return the write-consistency to use querying Cassandra binary data
     */
    @Produces
    @BinaryWriteConsistency
    public DefaultConsistencyLevel getBinaryWriteConsistency() {
        return DefaultConsistencyLevel.valueOf(binaryWriteConsistency);
    }

    /**
     * @return the read-consistency to use querying Cassandra RDF data
     */
    @Produces
    @MutableReadConsistency
    public DefaultConsistencyLevel getRdfReadConsistency() {
        return DefaultConsistencyLevel.valueOf(rdfReadConsistency);
    }

    /**
     * @return the write-consistency to use querying Cassandra RDF data
     */
    @Produces
    @MutableWriteConsistency
    public DefaultConsistencyLevel getRdfWriteConsistency() {
        return DefaultConsistencyLevel.valueOf(rdfWriteConsistency);
    }

    /**
     * Connect to Cassandra, lazily.
     */
    @PostConstruct
    public void connect() {
        LOGGER.info("Using Cassandra node address: {} and port: {}", contactAddress, contactPort);
        LOGGER.debug("Looking for connection...");
        final InetSocketAddress socketAddress = createUnresolved(contactAddress, parseInt(contactPort));

        this.session = CqlSession.builder()
                        .addTypeCodecs(STANDARD_CODECS)
                        .withKeyspace(keyspace)
                        .withLocalDatacenter(datacenter)
                        .addContactPoint(socketAddress)
                        .build();
    }

    /**
     * @return a {@link CqlSession} for use with {@link CassandraResourceService} (and {@link CassandraBinaryService})
     */
    @Produces
    @ApplicationScoped
    public CqlSession session() {
        return session;
    }

    /**
     * Release resources.
     */
    @PreDestroy
    public void close() {
        session.close();
    }
}
