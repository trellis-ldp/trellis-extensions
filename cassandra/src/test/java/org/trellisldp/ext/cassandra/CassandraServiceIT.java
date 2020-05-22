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
package org.trellisldp.ext.cassandra;

import static java.lang.Boolean.TRUE;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.trellisldp.api.RDFFactory;

class CassandraServiceIT {

    protected RDF rdfFactory = RDFFactory.getInstance();

    @RegisterExtension
    protected static CassandraConnection connection = new CassandraConnection();

    protected IRI createIRI(final String iri) {
        return rdfFactory.createIRI(iri);
    }

    protected void waitTwoSeconds() {
        await().pollDelay(Duration.ofSeconds(2)).until(() -> TRUE);
    }
}
