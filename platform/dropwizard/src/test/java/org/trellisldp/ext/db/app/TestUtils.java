/*
 * Copyright (c) 2017 - 2020 Aaron Coburn and individual contributors
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

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.io.File.separator;
import static java.util.Arrays.asList;
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.Client;

import org.apache.commons.text.RandomStringGenerator;
import org.trellisldp.api.RuntimeTrellisException;

/**
 * Testing utilities.
 */
final class TestUtils {

    private static final ConfigOverride MEMENTOS = config("mementos", resourceFilePath("data") + separator
            + "mementos-" + randomString(8));
    private static final ConfigOverride BINARIES = config("binaries", resourceFilePath("data") + separator
            + "binaries-" + randomString(8));
    private static final ConfigOverride NAMESPACES = config("namespaces", resourceFilePath("data/namespaces.json"));

    public static String randomString(final int length) {
        return new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(length);
    }

    private static List<ConfigOverride> defaultConfigs(final ConfigOverride... overrides) {
        final List<ConfigOverride> configs = new ArrayList<>(asList(BINARIES, MEMENTOS, NAMESPACES));
        configs.addAll(asList(overrides));
        return configs;
    }

    public static DropwizardTestSupport<AppConfiguration> buildPgsqlApp(final String dbUrl, final String dbUser,
            final String dbPassword, final ConfigOverride... overrides) {
        final List<ConfigOverride> confs = defaultConfigs(overrides);
        confs.addAll(asList(config("database.url", dbUrl), config("database.user", dbUser),
                     config("database.password", dbPassword)));
        return buildGenericApp(confs);
    }

    public static DropwizardTestSupport<AppConfiguration> buildMysqlApp(final String dbUrl, final String dbUser,
            final String dbPassword, final ConfigOverride... overrides) {
        final List<ConfigOverride> confs = defaultConfigs(overrides);
        confs.addAll(asList(config("database.driverClass", "com.mysql.cj.jdbc.Driver"),
                     config("database.url", dbUrl), config("database.user", dbUser),
                     config("database.password", dbPassword)));
        return buildGenericApp(confs);
    }

    public static DropwizardTestSupport<AppConfiguration> buildH2App(final String dbUrl,
            final ConfigOverride... overrides) {
        final List<ConfigOverride> confs = defaultConfigs(overrides);
        confs.addAll(asList(config("database.driverClass", "org.h2.Driver"),
                    config("database.url", dbUrl)));
        return buildGenericApp(confs);
    }

    public static DropwizardTestSupport<AppConfiguration> buildGenericApp(final List<ConfigOverride> overrides) {
        final DropwizardTestSupport<AppConfiguration> app = new DropwizardTestSupport<>(TrellisApplication.class,
                resourceFilePath("trellis-config.yml"), overrides.toArray(new ConfigOverride[0]));
        try {
            app.before();
        } catch (final Exception ex) {
            throw new RuntimeTrellisException("Error starting application", ex);
        }
        return app;
    }

    public static Client buildClient(final DropwizardTestSupport<AppConfiguration> app) {
        final Client client = new JerseyClientBuilder(app.getEnvironment()).build("test client");
        client.property(CONNECT_TIMEOUT, 5000);
        client.property(READ_TIMEOUT, 5000);
        return client;
    }

    private TestUtils() {
        // prevent instantiation
    }
}
