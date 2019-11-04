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

import static com.datastax.oss.driver.api.core.CqlSession.builder;
import static com.datastax.oss.driver.api.core.DefaultConsistencyLevel.ONE;
import static java.net.InetSocketAddress.createUnresolved;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.ext.cassandra.DatasetCodec.DATASET_CODEC;
import static org.trellisldp.ext.cassandra.IRICodec.IRI_CODEC;
import static org.trellisldp.ext.cassandra.InputStreamCodec.INPUTSTREAM_CODEC;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.trellisldp.api.IdentifierService;
import org.trellisldp.ext.cassandra.query.binary.GetChunkSize;
import org.trellisldp.ext.cassandra.query.binary.Insert;
import org.trellisldp.ext.cassandra.query.binary.Read;
import org.trellisldp.ext.cassandra.query.binary.ReadRange;
import org.trellisldp.ext.cassandra.query.rdf.BasicContainment;
import org.trellisldp.ext.cassandra.query.rdf.Delete;
import org.trellisldp.ext.cassandra.query.rdf.Get;
import org.trellisldp.ext.cassandra.query.rdf.GetFirstMemento;
import org.trellisldp.ext.cassandra.query.rdf.GetMemento;
import org.trellisldp.ext.cassandra.query.rdf.ImmutableInsert;
import org.trellisldp.ext.cassandra.query.rdf.ImmutableRetrieve;
import org.trellisldp.ext.cassandra.query.rdf.Mementoize;
import org.trellisldp.ext.cassandra.query.rdf.Mementos;
import org.trellisldp.ext.cassandra.query.rdf.MutableInsert;
import org.trellisldp.ext.cassandra.query.rdf.Touch;

class CassandraConnection implements AfterAllCallback, BeforeAllCallback {

    private static final String[] CLEANOUT_QUERIES = new String[] { "TRUNCATE metadata ; ", "TRUNCATE mutabledata ; ",
            "TRUNCATE immutabledata ;", "TRUNCATE binarydata ;", "TRUNCATE mementodata ;" };

    private static final DefaultConsistencyLevel testConsistency = ONE;

    private static final Logger log = getLogger(CassandraConnection.class);

    private static final String keyspace = "trellis";

    private CqlSession session;

    CassandraResourceService resourceService;

    CassandraBinaryService binaryService;

    CassandraMementoService mementoService;

    private static final String contactAddress = System.getProperty("cassandra.contactAddress", "localhost");

    private static final Integer contactPort = Integer.getInteger("cassandra.nativeTransportPort", 9042);

    private static final boolean cleanBefore = Boolean.getBoolean("cleanBeforeTests");

    private static final boolean cleanAfter = Boolean.getBoolean("cleanAfterTests");

    @Override
    public void beforeAll(final ExtensionContext context) {
        log.debug("Trying Cassandra connection at: {}:{}", contactAddress, contactPort);
        final InetSocketAddress socketAddress = createUnresolved(contactAddress, contactPort);
        this.session = builder()
                        .withLocalDatacenter("datacenter1")
                        .addTypeCodecs(INPUTSTREAM_CODEC, IRI_CODEC, DATASET_CODEC)
                        .withKeyspace("trellis")
                        .addContactPoint(socketAddress).build();
        this.resourceService = new CassandraResourceService(new Delete(session, ONE),
                        new Get(session, ONE),
                        new ImmutableInsert(session, testConsistency),
                        new MutableInsert(session, testConsistency),
                        new Touch(session, testConsistency),
                        new ImmutableRetrieve(session, testConsistency),
                        new BasicContainment(session, testConsistency));
        resourceService.initializeRoot();
        this.mementoService = new CassandraMementoService(new Mementos(session, testConsistency),
                        new Mementoize(session, testConsistency), new GetMemento(session, testConsistency),
                        new GetFirstMemento(session, testConsistency));
        this.binaryService = new CassandraBinaryService((IdentifierService) null, 1024 * 1024,
                        new GetChunkSize(session, testConsistency),
                        new Insert(session, testConsistency),
                        new org.trellisldp.ext.cassandra.query.binary.Delete(session, testConsistency),
                        new Read(session, testConsistency),
                        new ReadRange(session, testConsistency));
        if (cleanBefore) cleanOut();
    }

    private void cleanOut() {
        log.info("Cleaning out test keyspace {}", keyspace);
        for (String q : CLEANOUT_QUERIES)
            session.execute(q);
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        if (cleanAfter) cleanOut();
        session.close();
    }
}
