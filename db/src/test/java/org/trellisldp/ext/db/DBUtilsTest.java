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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.trellisldp.vocabulary.RDF.langString;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.junit.jupiter.api.Test;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.XSD;

/**
 * DB Utility tests.
 */
public class DBUtilsTest {

    private static final RDF rdf = new SimpleRDF();

    @Test
    public void testGetBaseIRI() {
        final IRI iri = rdf.createIRI("http://example.com/resource#fragment");
        final IRI iri2 = rdf.createIRI("http://example.com/resource");
        assertNotEquals(iri, DBUtils.getBaseIRI(iri));
        assertEquals(iri2, DBUtils.getBaseIRI(iri));

        final Literal literal = rdf.createLiteral("A literal value", "en");
        assertEquals(literal, DBUtils.getBaseIRI(literal));
    }

    @Test
    public void testGetObjectValue() {
        final String url = "http://example.com/resource";
        final IRI iri = rdf.createIRI(url);
        assertEquals(url, DBUtils.getObjectValue(iri));

        final String lexicalForm = "A literal value";
        final Literal literal = rdf.createLiteral(lexicalForm, "en");
        assertEquals(lexicalForm, DBUtils.getObjectValue(literal));

        assertNull(DBUtils.getObjectValue(rdf.createBlankNode()));
    }

    @Test
    public void testGetObjectLang() {
        final String lexicalForm = "A literal value";
        final String lang = "en";
        final Literal literal = rdf.createLiteral(lexicalForm, lang);
        assertEquals(lang, DBUtils.getObjectLang(literal));

        assertNull(DBUtils.getObjectLang(rdf.createLiteral(lexicalForm)));
        assertNull(DBUtils.getObjectLang(rdf.createIRI("http://example.com/")));
    }

    @Test
    public void testGetObjectDatatype() {
        final String lexicalForm = "5";
        final Literal literal = rdf.createLiteral(lexicalForm, XSD.positiveInteger);
        assertEquals(XSD.positiveInteger.getIRIString(), DBUtils.getObjectDatatype(literal));

        assertEquals(langString.getIRIString(), DBUtils.getObjectDatatype(rdf.createLiteral(lexicalForm, "en")));
        assertEquals(XSD.string_.getIRIString(), DBUtils.getObjectDatatype(rdf.createLiteral(lexicalForm)));
        assertNull(DBUtils.getObjectDatatype(rdf.createIRI("http://example.com/")));
    }

    @Test
    public void testGetBinary() {
        assertTrue(DBUtils.getBinary(LDP.NonRDFSource, "file:///path/to/resource",
                    1529417263892L, "text/plain", 10L).isPresent());
        assertFalse(DBUtils.getBinary(LDP.RDFSource, "file:///path/to/resource",
                    1529417263892L, "text/plain", 10L).isPresent());
        assertFalse(DBUtils.getBinary(LDP.NonRDFSource, null,
                    1529417263892L, "text/plain", 10L).isPresent());
        assertFalse(DBUtils.getBinary(LDP.NonRDFSource, "file:///path/to/resource",
                    null, "text/plain", 10L).isPresent());
    }

}
