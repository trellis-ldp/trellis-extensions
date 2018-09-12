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

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;

import io.dropwizard.testing.DropwizardTestSupport;

import java.io.IOException;

import javax.ws.rs.client.Client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Run LDP-Related Tests.
 */
@EnabledIfEnvironmentVariable(named = "TRAVIS", matches = "true")
public class TrellisLdpMysqlTest extends AbstractLdpTests {

    private static final DropwizardTestSupport<AppConfiguration> MYSQL_APP = TestUtils.buildMysqlApp(
            "jdbc:mysql://localhost/trellis", "travis", "");
    private static final Client CLIENT = TestUtils.buildClient(MYSQL_APP);

    @BeforeAll
    public static void setup() throws Exception {
        MYSQL_APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));
    }

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + MYSQL_APP.getLocalPort() + "/";
    }

    @AfterAll
    public static void cleanup() throws IOException {
        MYSQL_APP.after();
    }
}
