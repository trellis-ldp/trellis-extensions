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

import io.dropwizard.testing.DropwizardTestSupport;

import java.io.IOException;

import javax.ws.rs.client.Client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Run LDP-Related Tests.
 */
public class TrellisLdpH2Test extends AbstractLdpTests {

    private static final DropwizardTestSupport<AppConfiguration> H2_APP = TestUtils.buildH2App(
            "jdbc:h2:file:./build/data/h2-" + TestUtils.randomString(10));
    private static final Client CLIENT = TestUtils.buildClient(H2_APP);

    @BeforeAll
    public static void setup() throws Exception {
        H2_APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));
    }

    @Override
    public Client getClient() {
        return CLIENT;
    }

    @Override
    public String getBaseURL() {
        return "http://localhost:" + H2_APP.getLocalPort() + "/";
    }

    @AfterAll
    public static void cleanup() throws IOException {
        H2_APP.after();
    }
}
