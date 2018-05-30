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
package org.trellisldp.ext.jdbc;

import static org.trellisldp.api.RDFUtils.getInstance;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;

/**
 * Utilities for the JDBC resource service.
 */
final class JDBCUtils {

    private static final RDF rdf = getInstance();

    public static RDFTerm getBaseIRI(final RDFTerm object) {
        if (object instanceof IRI) {
            final String iri = ((IRI) object).getIRIString().split("#")[0];
            return rdf.createIRI(iri);
        }
        return object;
    }

    private JDBCUtils() {
        // prevent instantiation
    }
}
