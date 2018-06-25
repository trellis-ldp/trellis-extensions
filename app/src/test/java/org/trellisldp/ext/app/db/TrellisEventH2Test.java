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
package org.trellisldp.ext.app.db;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.awaitility.Awaitility.setDefaultPollInterval;
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;
import static org.junit.jupiter.api.Assertions.fail;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.getInstance;
import static org.trellisldp.test.TestUtils.readEntityAsGraph;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.DropwizardTestSupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import javax.ws.rs.client.Client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.trellisldp.test.AbstractApplicationEventTests;

/**
 * Event tests.
 */
public class TrellisEventH2Test extends AbstractApplicationEventTests implements MessageListener {

    private static final Logger LOGGER = getLogger(TrellisEventH2Test.class);

    private static DropwizardTestSupport<AppConfiguration> APP;

    private static Client CLIENT;

    private static final RDF rdf = getInstance();

    private static final BrokerService BROKER = new BrokerService();

    static {

        try {
            BROKER.setPersistent(false);
            BROKER.start();

            APP = new DropwizardTestSupport<AppConfiguration>(TrellisApplication.class,
                        resourceFilePath("trellis-config.yml"),
                        config("database.url", "jdbc:h2:" + resourceFilePath("data") + "h2-"
                             + new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(10)),
                        config("database.driverClass", "org.h2.Driver"),
                        config("notifications.type", "JMS"),
                        config("notifications.connectionString", "vm://localhost"),
                        config("binaries", resourceFilePath("data") + "/binaries"),
                        config("mementos", resourceFilePath("data") + "/mementos"),
                        config("namespaces", resourceFilePath("data/namespaces.json")));

            APP.before();
            APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));

            CLIENT = new JerseyClientBuilder(APP.getEnvironment()).build("test client");
            CLIENT.property(CONNECT_TIMEOUT, 5000);
            CLIENT.property(READ_TIMEOUT, 5000);
            setDefaultPollInterval(100L, MILLISECONDS);

        } catch (final IOException ex) {
            LOGGER.error("Error initializing Trellis", ex);
            fail(ex.getMessage());
        } catch (final Exception ex) {
            LOGGER.error("Error starting broker", ex);
            fail(ex.getMessage());
        }
    }

    private final Set<Graph> messages = new CopyOnWriteArraySet<>();

    private MessageConsumer consumer;
    private Connection connection;

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + APP.getLocalPort() + "/";
    }

    @Override
    public Set<Graph> getMessages() {
        return messages;
    }

    @Override
    public String getJwtSecret() {
        return "secret";
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

    @AfterAll
    public static void cleanup() throws Exception {
        APP.after();
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
