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
package org.trellisldp.ext.aws;

import static com.amazonaws.services.s3.AmazonS3ClientBuilder.defaultClient;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Predicate.isEqual;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.TrellisUtils.getInstance;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.IOException;
import java.time.Instant;
import java.util.SortedSet;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.text.RandomStringGenerator;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.MementoService;
import org.trellisldp.api.Resource;
import org.trellisldp.api.RuntimeTrellisException;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

public class S3MementoServiceTest {

    private static final RDF rdf = getInstance();
    private static final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);
    private static final int length = 10;
    private static final String base = new RandomStringGenerator.Builder().withinRange('a', 'z')
        .build().generate(length);

    @AfterAll
    public static void tearDown() throws Exception {
        final AmazonS3 client = defaultClient();
        final String bucket = ConfigProvider.getConfig().getValue(S3MementoService.CONFIG_MEMENTO_BUCKET, String.class);
        client.listObjects(bucket, "mementos/" + base).getObjectSummaries().stream()
            .map(S3ObjectSummary::getKey).forEach(key -> client.deleteObject(bucket, key));
    }

    @Test
    public void testMementoRDFSource() {
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + "mementos/" + base + "/resource");
        final Resource res = mock(Resource.class);
        final Instant time = now();
        final Quad quad = rdf.createQuad(Trellis.PreferUserManaged, identifier, DC.title, rdf.createLiteral("Title"));
        final Quad acl = rdf.createQuad(Trellis.PreferAccessControl, identifier, ACL.mode, ACL.Read);
        when(res.getModified()).thenReturn(time);
        when(res.getIdentifier()).thenReturn(identifier);
        when(res.getContainer()).thenReturn(of(root));
        when(res.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(res.getBinaryMetadata()).thenReturn(empty());
        when(res.getMembershipResource()).thenReturn(empty());
        when(res.getMemberRelation()).thenReturn(empty());
        when(res.getMemberOfRelation()).thenReturn(empty());
        when(res.getInsertedContentRelation()).thenReturn(empty());
        when(res.stream()).thenAnswer(inv -> Stream.of(quad, acl));

        final MementoService svc = new S3MementoService();
        assertDoesNotThrow(svc.put(res).toCompletableFuture()::join);
        svc.get(identifier, time).thenAccept(r -> {
            assertEquals(identifier, r.getIdentifier());
            assertEquals(LDP.RDFSource, r.getInteractionModel());
            assertEquals(time, r.getModified());
            assertEquals(of(root), r.getContainer());
            assertTrue(r.stream().anyMatch(isEqual(quad)));
            assertFalse(r.getBinaryMetadata().isPresent());
            assertFalse(r.getMembershipResource().isPresent());
            assertFalse(r.getMemberRelation().isPresent());
            assertFalse(r.getMemberOfRelation().isPresent());
            assertFalse(r.getInsertedContentRelation().isPresent());
            assertTrue(r.hasAcl());
        }).toCompletableFuture().join();

        final Resource res2 = mock(Resource.class);
        final Instant time2 = time.plusSeconds(10L);
        final Quad quad2 = rdf.createQuad(Trellis.PreferUserManaged, identifier, DC.title,
                rdf.createLiteral("Better Title"));
        when(res2.getModified()).thenReturn(time2);
        when(res2.getIdentifier()).thenReturn(identifier);
        when(res2.getContainer()).thenReturn(of(root));
        when(res2.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(res2.getBinaryMetadata()).thenReturn(empty());
        when(res2.getMembershipResource()).thenReturn(empty());
        when(res2.getMemberRelation()).thenReturn(empty());
        when(res2.getMemberOfRelation()).thenReturn(empty());
        when(res2.getInsertedContentRelation()).thenReturn(empty());
        when(res2.stream()).thenAnswer(inv -> Stream.of(quad2, acl));
        assertDoesNotThrow(svc.put(res2).toCompletableFuture()::join);

        svc.mementos(identifier).thenAccept(mementos -> {
            assertTrue(mementos.contains(time.truncatedTo(SECONDS)));
            assertTrue(mementos.contains(time2.truncatedTo(SECONDS)));
        });

        svc.get(identifier, time.plusSeconds(5L)).thenAccept(r ->
                assertEquals(time, r.getModified()));
        svc.get(identifier, time2.plusSeconds(5L)).thenAccept(r ->
                assertEquals(time2, r.getModified()));
    }

    @Test
    public void testMementoNonRDFSource() {
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + "mementos/" + base + "/binary");
        final BinaryMetadata binary = BinaryMetadata.builder(rdf.createIRI("s3://bucket/binary")).mimeType("text/plain")
            .build();
        final Resource res = mock(Resource.class);
        final Instant time = now();
        final Quad quad = rdf.createQuad(Trellis.PreferUserManaged, identifier, DC.title, rdf.createLiteral("Title"));
        when(res.getModified()).thenReturn(time);
        when(res.getIdentifier()).thenReturn(identifier);
        when(res.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(res.getContainer()).thenReturn(empty());
        when(res.getBinaryMetadata()).thenReturn(of(binary));
        when(res.getMembershipResource()).thenReturn(empty());
        when(res.getMemberRelation()).thenReturn(empty());
        when(res.getMemberOfRelation()).thenReturn(empty());
        when(res.getInsertedContentRelation()).thenReturn(empty());
        when(res.stream()).thenAnswer(inv -> Stream.of(quad));

        final MementoService svc = new S3MementoService();
        assertDoesNotThrow(svc.put(res).toCompletableFuture()::join);
        svc.get(identifier, time).thenAccept(r -> {
            assertEquals(identifier, r.getIdentifier());
            assertEquals(LDP.NonRDFSource, r.getInteractionModel());
            assertEquals(time, r.getModified());
            assertTrue(r.stream().anyMatch(isEqual(quad)));
            assertTrue(r.getBinaryMetadata().isPresent());
            r.getBinaryMetadata().ifPresent(b -> {
                assertEquals(binary.getIdentifier(), b.getIdentifier());
                assertEquals(binary.getMimeType(), b.getMimeType());
            });
            assertFalse(r.getContainer().isPresent());
            assertFalse(r.getMembershipResource().isPresent());
            assertFalse(r.getMemberRelation().isPresent());
            assertFalse(r.getMemberOfRelation().isPresent());
            assertFalse(r.getInsertedContentRelation().isPresent());
            assertFalse(r.hasAcl());
        }).toCompletableFuture().join();

        svc.mementos(identifier).thenAccept(mementos -> assertTrue(mementos.contains(time)));
        svc.get(identifier, time.minusSeconds(10L)).thenAccept(r -> assertEquals(MISSING_RESOURCE, r));
    }

    @Test
    public void testMementoIndirectContainer() {
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + "mementos/" + base + "/container");
        final IRI member = rdf.createIRI(TRELLIS_DATA_PREFIX + "mementos/" + base + "/resource");
        final Resource res = mock(Resource.class);
        final Instant time = now();
        final Quad quad = rdf.createQuad(Trellis.PreferUserManaged, identifier, DC.title, rdf.createLiteral("Title"));
        final Quad acl = rdf.createQuad(Trellis.PreferAccessControl, identifier, ACL.mode, ACL.Write);
        when(res.getModified()).thenReturn(time);
        when(res.getIdentifier()).thenReturn(identifier);
        when(res.getContainer()).thenReturn(of(root));
        when(res.getInteractionModel()).thenReturn(LDP.IndirectContainer);
        when(res.getBinaryMetadata()).thenReturn(empty());
        when(res.getMembershipResource()).thenReturn(of(member));
        when(res.getMemberRelation()).thenReturn(of(LDP.member));
        when(res.getMemberOfRelation()).thenReturn(of(DC.isPartOf));
        when(res.getInsertedContentRelation()).thenReturn(of(LDP.MemberSubject));
        when(res.stream()).thenAnswer(inv -> Stream.of(quad, acl));

        final MementoService svc = new S3MementoService();
        assertDoesNotThrow(svc.put(res).toCompletableFuture()::join);
        svc.get(identifier, time).thenAccept(r -> {
            assertEquals(identifier, r.getIdentifier());
            assertEquals(LDP.IndirectContainer, r.getInteractionModel());
            assertEquals(time, r.getModified());
            assertEquals(of(root), r.getContainer());
            assertTrue(r.stream().anyMatch(isEqual(quad)));
            assertFalse(r.getBinaryMetadata().isPresent());
            assertEquals(of(member), r.getMembershipResource());
            assertEquals(of(LDP.member), r.getMemberRelation());
            assertEquals(of(DC.isPartOf), r.getMemberOfRelation());
            assertEquals(of(LDP.MemberSubject), r.getInsertedContentRelation());
            assertTrue(r.hasAcl());
        }).toCompletableFuture().join();

        svc.mementos(identifier).thenAccept(mementos -> assertTrue(mementos.contains(time)));
    }

    @Test
    public void testMementoError() {
        final Resource res = mock(Resource.class);
        when(res.getInteractionModel()).thenAnswer(inv -> {
            throw new IOException("Expected");
        });

        final MementoService svc = new S3MementoService();
        assertThrows(CompletionException.class, svc.put(res).toCompletableFuture()::join);
    }

    @Test
    public void testTruncated() {
        final AmazonS3 mockClient = mock(AmazonS3.class);
        final ListObjectsV2Result mockResult = mock(ListObjectsV2Result.class);
        final S3ObjectSummary obj1 = new S3ObjectSummary();
        obj1.setKey("object/key?version=1544042743");
        final S3ObjectSummary obj2 = new S3ObjectSummary();
        obj2.setKey("object/key?version=1544042912");
        final S3ObjectSummary obj3 = new S3ObjectSummary();
        obj3.setKey("object/key?version");

        when(mockClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResult);
        when(mockResult.getObjectSummaries()).thenReturn(singletonList(obj1)).thenReturn(asList(obj2, obj3));
        when(mockResult.getContinuationToken()).thenReturn("continuation");
        when(mockResult.isTruncated()).thenReturn(true).thenReturn(false);
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + "mementos/" + base + "/container");
        final MementoService svc = new S3MementoService(mockClient, "bucket", null);
        final SortedSet<Instant> m = svc.mementos(identifier).toCompletableFuture().join();
        assertEquals(2L, m.size());
    }

    @Test
    public void testResourceError() {
        final AmazonS3 mockClient = mock(AmazonS3.class);
        final ObjectMetadata mockMetadata = mock(ObjectMetadata.class);
        final GetObjectRequest mockRequest = mock(GetObjectRequest.class);
        final S3Object mockObject = mock(S3Object.class);

        when(mockClient.getObject(eq(mockRequest))).thenReturn(mockObject);
        when(mockObject.getObjectContent()).thenAnswer(inv -> {
            throw new IOException("Expected");
        });

        final Resource testResource = new S3Resource(mockMetadata, mockClient, mockRequest, "");
        assertThrows(RuntimeTrellisException.class, testResource::stream);
    }
}
