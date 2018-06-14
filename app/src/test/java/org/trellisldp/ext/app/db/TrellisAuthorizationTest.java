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
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;
import static org.junit.jupiter.api.Assertions.fail;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.io.Resources;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.DropwizardTestSupport;

import java.io.IOException;

import javax.ws.rs.client.Client;

import org.apache.commons.text.RandomStringGenerator;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.trellisldp.test.AbstractApplicationAuthTests;

/**
 * Authorization tests.
 */
public class TrellisAuthorizationTest extends AbstractApplicationAuthTests {

    private static Logger LOGGER = getLogger(TrellisAuthorizationTest.class);

    private static EmbeddedPostgres pg = null;

    private static DropwizardTestSupport<AppConfiguration> APP;

    private static Client CLIENT;

    static {

        try {
            pg = EmbeddedPostgres.builder()
                .setDataDirectory(resourceFilePath("data") + "/pgdata-" + new RandomStringGenerator.Builder()
                .withinRange('a', 'z').build().generate(10)).start();
            // SET UP DATABASE
            Jdbi.create(pg.getPostgresDatabase()).useHandle(handle ->
                handle.execute(Resources.toString(Resources.getResource("create.pgsql"), UTF_8)));

            APP = new DropwizardTestSupport<AppConfiguration>(TrellisApplication.class,
                        resourceFilePath("trellis-config.yml"),
                        config("database.url", "jdbc:postgresql://localhost:" + pg.getPort() + "/postgres"),
                        config("auth.basic.usersFile", resourceFilePath("users.auth")),
                        config("binaries", resourceFilePath("data") + "/binaries"),
                        config("mementos", resourceFilePath("data") + "/mementos"),
                        config("namespaces", resourceFilePath("data/namespaces.json")));

            APP.before();

            CLIENT = new JerseyClientBuilder(APP.getEnvironment()).build("test client");
            CLIENT.property(CONNECT_TIMEOUT, 5000);
            CLIENT.property(READ_TIMEOUT, 5000);

        } catch (final IOException ex) {
            LOGGER.error("Error initializing Trellis", ex);
            fail(ex.getMessage());
        }
    }

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + APP.getLocalPort() + "/";
    }

    @Override
    public String getUser1Credentials() {
        return "acoburn:secret";
    }

    @Override
    public String getUser2Credentials() {
        return "user:password";
    }

    @Override
    public String getJwtSecret() {
        return "secret";
    }

    @AfterAll
    public static void cleanup() {
        APP.after();
    }
}
