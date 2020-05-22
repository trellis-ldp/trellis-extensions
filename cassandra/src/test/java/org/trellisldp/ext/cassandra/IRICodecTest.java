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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.trellisldp.ext.cassandra.IRICodec.IRI_CODEC;

import java.nio.ByteBuffer;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.Test;
import org.trellisldp.api.RDFFactory;

class IRICodecTest {

    RDF rdf = RDFFactory.getInstance();

    @Test
    void badParse() {
        assertThrows(IllegalArgumentException.class, () -> IRI_CODEC.parse("SGDF   &&$$$dfshgou;sdfhgoudfhogh"));
    }

    @Test
    void testParse() {
        final IRI iri = rdf.createIRI("http://example.com");
        final String fieldForm = iri.getIRIString();
        assertEquals(iri, IRI_CODEC.parse(fieldForm));
    }

    @Test
    void testFormat() {
        final IRI iri = rdf.createIRI("http://example.com");
        final String fieldForm = iri.getIRIString();
        assertEquals(fieldForm, IRI_CODEC.format(iri));
    }

    @Test
    void testDeserialize() {
        final IRI iri = rdf.createIRI("http://example.com");
        final ByteBuffer fieldForm = ByteBuffer.wrap(iri.getIRIString().getBytes(UTF_8));
        assertEquals(iri, IRI_CODEC.decode(fieldForm, null));
    }

    @Test
    void nullForNull() {
        assertEquals(null, IRI_CODEC.parse(null));
        assertEquals(null, IRI_CODEC.format(null));
        assertEquals(null, IRI_CODEC.encode(null, null));
        assertEquals(null, IRI_CODEC.decode(null, null));
    }
}
