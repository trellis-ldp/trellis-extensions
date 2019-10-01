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
package org.trellisldp.ext.db.webapp;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@DisabledOnOs(WINDOWS)
@EnabledIfEnvironmentVariable(named = "QUARKUS_EXTERNAL_PGSQL", matches = "true")
@QuarkusTest
class PgsqlApplicationTest extends AbstractApplicationTests {

    @BeforeAll
    static void setUp() throws Exception {
        System.setProperty("quarkus.datasource.username", "postgres");
        System.clearProperty("quarkus.datasource.password");
        System.setProperty("quarkus.datasource.url", "jdbc:postgresql://localhost/trellis");
    }

    @AfterAll
    static void tearDown() throws Exception {
        System.clearProperty("quarkus.datasource.url");
        System.clearProperty("quarkus.datasource.username");
    }
}
