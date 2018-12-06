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

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.junit.jupiter.api.Assertions.fail;

import io.dropwizard.testing.DropwizardTestSupport;

import java.io.IOException;

import javax.ws.rs.client.Client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Audit tests.
 */
@EnabledIfEnvironmentVariable(named = "TRAVIS", matches = "true")
public class TrellisAuditPgsqlTest extends AbstractAuditTests {

    private static final DropwizardTestSupport<AppConfiguration> PG_APP = TestUtils.buildPgsqlApp(
            "jdbc:postgresql://localhost/trellis", "postgres", "");

    private static final Client CLIENT = TestUtils.buildClient(PG_APP);

    static {
        try {
            PG_APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));
        } catch (final Exception ex) {
            fail(ex.getMessage(), ex);
        }
    }

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + PG_APP.getLocalPort() + "/";
    }

    @AfterAll
    public static void cleanup() throws IOException {
        PG_APP.after();
    }
}
