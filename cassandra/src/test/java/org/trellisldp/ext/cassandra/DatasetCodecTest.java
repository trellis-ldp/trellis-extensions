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
package org.trellisldp.ext.cassandra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.trellisldp.ext.cassandra.DatasetCodec.DATASET_CODEC;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.apache.jena.riot.RiotException;
import org.junit.jupiter.api.Test;

class DatasetCodecTest {

    private static final RDF rdf = new SimpleRDF();

    @Test
    void badParse() {
        assertThrows(RiotException.class, () -> DATASET_CODEC.parse("SGDF   &&$$$dfshgou;sdfhgoudfhogh"));
    }

    @Test
    void testParse() throws Exception {
        final String nQuad1 = "<s> <p> <o> <g> .";
        final Quad q1 = quad(iri("g"), iri("s"), iri("p"), iri("o"));
        final String nQuad2 = "<s1> <p1> \"foo\" .";
        final Quad q2 = quad(null, iri("s1"), iri("p1"), rdf.createLiteral("foo"));
        final String nQuad3 = "<s> <p> <o> <g2> .";
        final Quad q3 = quad(iri("g2"), iri("s"), iri("p"), iri("o"));
        final String nQuads = String.join("\n", nQuad1, nQuad2, nQuad3);
        try (Dataset dataset = DATASET_CODEC.parse(nQuads)) {
            assertEquals(3, dataset.size());
            for (Quad q : new Quad[] { q1, q2, q3 })
                assertTrue(dataset.contains(q));
        }
    }

    @Test
    void testDeserialize() throws Exception {
        final String nQuad1 = "<s> <p> <o> <g> .";
        final Quad q1 = quad(iri("g"), iri("s"), iri("p"), iri("o"));
        final String nQuad2 = "<s1> <p1> \"foo\" .";
        final Quad q2 = quad(null, iri("s1"), iri("p1"), rdf.createLiteral("foo"));
        final String nQuad3 = "<s> <p> <o> <g2> .";
        final Quad q3 = quad(iri("g2"), iri("s"), iri("p"), iri("o"));
        final ByteBuffer nQuads = ByteBuffer.wrap(String.join("\n", nQuad1, nQuad2, nQuad3).getBytes(UTF_8));
        try (Dataset dataset = DATASET_CODEC.decode(nQuads, null)) {
            assertEquals(3, dataset.size());
            for (Quad q : new Quad[] { q1, q2, q3 })
                assertTrue(dataset.contains(q));
        }
    }

    @Test
    void testFormat() throws Exception {
        final String nQuad = "<s> <p> <o> <g> .";
        final Quad q = quad(iri("g"), iri("s"), iri("p"), iri("o"));
        try (Dataset dataset = rdf.createDataset()) {
            dataset.add(q);
            final String nQuads = DATASET_CODEC.format(dataset);
            assertEquals(1, dataset.size());
            assertEquals(nQuad, nQuads.trim());
        }
    }

    @Test
    void edgeCase1() {
        assertEquals(0, DATASET_CODEC.parse(null).size());
    }

    @Test
    void edgeCase2() {
        assertEquals(null, DATASET_CODEC.format(null));
    }

    @Test
    void edgeCase3() throws Exception {
        try (Dataset empty = rdf.createDataset()) {
            assertEquals(null, DATASET_CODEC.format(empty));
        }
    }

    @Test
    void edgeCase4() throws Exception {
        try (Dataset empty = rdf.createDataset()) {
            assertEquals(null, DATASET_CODEC.encode(empty, null));
        }
    }

    @Test
    void edgeCase5() {
        assertEquals(null, DATASET_CODEC.encode(null, null));
    }

    @Test
    void edgeCase6() {
        assertEquals(0, DATASET_CODEC.decode(null, null).size());
    }

    @Test
    void badData() {
        assertThrows(RiotException.class, () -> DATASET_CODEC.encode(new BadDataset(), null));
    }

    private Quad quad(final BlankNodeOrIRI g, final BlankNodeOrIRI s, final IRI p, final RDFTerm o) {
        return rdf.createQuad(g, s, p, o);
    }

    private IRI iri(final String v) {
        return rdf.createIRI(v);
    }

    /**
     * {@link #stream()} throws a {@code RiotException} for testing.
     *
     */
    private static final class BadDataset implements Dataset {
        @Override
        public void add(final Quad quad) {
            // no-op
        }

        @Override
        public void add(final BlankNodeOrIRI graphName, final BlankNodeOrIRI subject, final IRI predicate,
                final RDFTerm object) {
            // no-op
        }

        @Override
        public boolean contains(final Quad quad) {
            return false;
        }

        @Override
        public boolean contains(final Optional<BlankNodeOrIRI> graphName, final BlankNodeOrIRI subject,
                final IRI predicate, final RDFTerm object) {
            return false;
        }

        @Override
        public Graph getGraph() {
            return null;
        }

        @Override
        public Optional<Graph> getGraph(final BlankNodeOrIRI graphName) {
            return null;
        }

        @Override
        public Stream<BlankNodeOrIRI> getGraphNames() {
            return null;
        }

        @Override
        public void remove(final Quad quad) {
            // no-op
        }

        @Override
        public void remove(final Optional<BlankNodeOrIRI> graphName, final BlankNodeOrIRI subject,
                final IRI predicate, final RDFTerm object) {
            // no-op
        }

        @Override
        public void clear() {
            // no-op
        }

        @Override
        public long size() {
            return 1;
        }

        @Override
        public Stream<? extends Quad> stream() {
            throw new RiotException();
        }

        @Override
        public Stream<? extends Quad> stream(final Optional<BlankNodeOrIRI> graphName, final BlankNodeOrIRI subject,
                final IRI predicate, final RDFTerm object) {
            return null;
        }
    }
}
