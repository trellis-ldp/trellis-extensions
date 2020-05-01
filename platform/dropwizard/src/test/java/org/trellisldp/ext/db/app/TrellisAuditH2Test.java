/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
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
package org.trellisldp.ext.db.app;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.junit.jupiter.api.Assertions.fail;

import io.dropwizard.testing.DropwizardTestSupport;

import javax.ws.rs.client.Client;

import org.junit.jupiter.api.AfterAll;

/**
 * Audit tests.
 */
class TrellisAuditH2Test extends AbstractAuditTests {
    private static final DropwizardTestSupport<AppConfiguration> APP = TestUtils.buildH2App(
                "jdbc:h2:file:./build/data/h2-" + TestUtils.randomString(10));
    private static final Client CLIENT = TestUtils.buildClient(APP);

    static {
        init();
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
    static void cleanup() {
        APP.after();
    }

    private static void init() {
        try {
            APP.getApplication().run("db", "migrate", resourceFilePath("trellis-config.yml"));
        } catch (final Exception ex) {
            fail(ex.getMessage(), ex);
        }
    }
}
