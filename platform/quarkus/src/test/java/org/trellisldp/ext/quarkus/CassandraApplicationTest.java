/*
 * Copyright (c) 2021 Aaron Coburn and individual contributors
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
package org.trellisldp.ext.quarkus;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@DisabledOnOs(WINDOWS)
@EnabledIfSystemProperty(named = "trellis.test.cassandra", matches = "true")
@QuarkusTest
class CassandraApplicationTest extends AbstractApplicationTests {

    @BeforeAll
    static void setUp() {
        System.setProperty("quarkus.flyway.migrate-at-start", "false");
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty("quarkus.flyway.migrate-at-start");
    }
}
