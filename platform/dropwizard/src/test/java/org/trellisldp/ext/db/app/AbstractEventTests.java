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
package org.trellisldp.ext.db.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.test.TestUtils.readEntityAsGraph;

import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.trellisldp.api.RDFFactory;
import org.trellisldp.test.AbstractApplicationEventTests;

abstract class AbstractEventTests extends AbstractApplicationEventTests implements MessageListener {

    private static final Logger LOGGER = getLogger(AbstractEventTests.class);
    private static final RDF rdf = RDFFactory.getInstance();

    private final Set<Graph> messages = new CopyOnWriteArraySet<>();

    private MessageConsumer consumer;
    private Connection connection;

    @Override
    public String getJwtSecret() {
        return "gCjvrNoj8us4SXZQUENBunut85+s/XPN5T9+dxol8L2YXgY6QISuVd02oRcuPb/3ewrICaEnAGvm4QYdszgBIA==";
    }

    /**
     * Aquire a JMS connection.
     *
     * @throws Exception if an error is encountered connecting to the JMS broker
     */
    @BeforeEach
    public void aquireConnection() throws Exception {
        final ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
        connection = connectionFactory.createConnection();
        connection.start();
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Destination destination = session.createQueue("trellis");
        consumer = session.createConsumer(destination);
        consumer.setMessageListener(this);
    }

    /**
     * Release a JMS connection.
     *
     * @throws Exception if an error is encountered disconnecting from the JMS broker
     */
    @AfterEach
    public void releaseConnection() throws Exception {
        consumer.setMessageListener(msg -> { });
        consumer.close();
        connection.close();
    }

    @Override
    public Set<Graph> getMessages() {
        return messages;
    }

    @Override
    public void onMessage(final Message message) {
        messages.add(convertToGraph(message));
    }

    private Graph convertToGraph(final Message msg) {
        try {
            final String body = ((TextMessage) msg).getText();
            return readEntityAsGraph(new ByteArrayInputStream(body.getBytes(UTF_8)), getBaseURL(), JSONLD);
        } catch (final Exception ex) {
            LOGGER.error("Error processing message: {}", ex.getMessage());
        }
        return rdf.createGraph();
    }
}
