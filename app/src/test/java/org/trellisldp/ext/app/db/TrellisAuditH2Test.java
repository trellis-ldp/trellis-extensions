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
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;
import static org.junit.jupiter.api.Assertions.fail;
import static org.slf4j.LoggerFactory.getLogger;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.DropwizardTestSupport;

import java.io.IOException;

import javax.ws.rs.client.Client;

import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.trellisldp.test.AbstractApplicationAuditTests;

/**
 * Audit tests.
 */
public class TrellisAuditH2Test extends AbstractApplicationAuditTests {

    private static final Logger LOGGER = getLogger(TrellisAuditH2Test.class);

    private static DropwizardTestSupport<AppConfiguration> APP;

    private static Client CLIENT;

    static {
        try {
            APP = new DropwizardTestSupport<AppConfiguration>(TrellisApplication.class,
                        resourceFilePath("trellis-config.yml"),
                        config("database.url", "jdbc:h2:" + resourceFilePath("data") + "h2-"
                             + new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(10)),
                        config("database.driverClass", "org.h2.Driver"),
                        config("binaries", resourceFilePath("data") + "/binaries"),
                        config("mementos", resourceFilePath("data") + "/mementos"),
                        config("namespaces", resourceFilePath("data/namespaces.json")));

            APP.before();
            APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));

            CLIENT = new JerseyClientBuilder(APP.getEnvironment()).build("test client");
            CLIENT.property(CONNECT_TIMEOUT, 5000);
            CLIENT.property(READ_TIMEOUT, 5000);

        } catch (final Exception ex) {
            LOGGER.error("Error initializing Trellis", ex);
            fail(ex.getMessage());
        }
    }

    @Override
    public String getJwtSecret() {
        return "secret";
    }

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + APP.getLocalPort() + "/";
    }

    @AfterAll
    public static void cleanup() throws IOException {
        APP.after();
    }
}
