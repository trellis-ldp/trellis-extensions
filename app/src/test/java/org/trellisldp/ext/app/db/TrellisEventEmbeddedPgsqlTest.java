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
import static java.io.File.separator;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.setDefaultPollInterval;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import io.dropwizard.testing.DropwizardTestSupport;

import java.io.IOException;

import javax.ws.rs.client.Client;

import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledOnOs;

/**
 * Event tests.
 */
@DisabledOnOs(WINDOWS)
@DisabledIfEnvironmentVariable(named = "TRAVIS", matches = "true")
public class TrellisEventEmbeddedPgsqlTest extends AbstractEventTests {

    private static final BrokerService BROKER = new BrokerService();

    private static EmbeddedPostgres pg;
    private static DropwizardTestSupport<AppConfiguration> APP;
    private static Client CLIENT;

    static {
        setDefaultPollInterval(100L, MILLISECONDS);
        try {
            BROKER.setPersistent(false);
            BROKER.start();

            pg = EmbeddedPostgres.builder()
                .setDataDirectory(resourceFilePath("data") + separator + "pgdata-" + TestUtils.randomString(10))
                .start();

            APP = TestUtils.buildPgsqlApp("jdbc:postgresql://localhost:" + pg.getPort() + "/postgres",
                    "postgres", "postgres", config("notifications.type", "JMS"),
                    config("notifications.connectionString", "vm://localhost"));
            APP.before();
            APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));

            CLIENT = TestUtils.buildClient(APP);
        } catch (final IOException ex) {
            fail("Error initializing Trellis", ex);
        } catch (final Exception ex) {
            fail("Error starting broker", ex);
        }
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + APP.getLocalPort() + "/";
    }

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @AfterAll
    public static void cleanup() throws Exception {
        APP.after();
        pg.close();
    }
}
