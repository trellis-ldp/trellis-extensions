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
package org.trellisldp.ext.db;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.jdbi.v3.core.Jdbi;
import org.trellisldp.api.NamespaceService;

/**
 * A namespace service that stores data in a database.
 */
public class DBNamespaceService implements NamespaceService {

    private final Jdbi jdbi;

    /**
     * Create a namespace service.
     * @param ds the datasource
     */
    @Inject
    public DBNamespaceService(final DataSource ds) {
        this(Jdbi.create(ds));
    }

    /**
     * Create a namespace service.
     * @param jdbi the Jdbi object
     */
    public DBNamespaceService(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public Map<String, String> getNamespaces() {
        final Map<String, String> namespaces = new HashMap<>();
        jdbi.useHandle(handle ->
                handle.select("SELECT prefix, namespace FROM namespaces")
                    .map((rs, ctx) -> new SimpleImmutableEntry<>(rs.getString("prefix"), rs.getString("namespace")))
                    .forEach(pair -> namespaces.put(pair.getKey(), pair.getValue())));
        return namespaces;
    }

    @Override
    public boolean setPrefix(final String prefix, final String namespace) {
        jdbi.useHandle(handle ->
            handle.execute("INSERT INTO namespaces (prefix, namespace) VALUES (?, ?)", prefix, namespace));
        return true;
    }
}
