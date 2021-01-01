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
package org.trellisldp.ext.aws;

import static com.amazonaws.services.sns.AmazonSNSClientBuilder.defaultClient;
import static java.time.Instant.now;
import static java.util.Collections.singleton;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trellisldp.api.Event;
import org.trellisldp.api.EventSerializationService;
import org.trellisldp.api.EventService;
import org.trellisldp.api.RDFFactory;
import org.trellisldp.event.jackson.DefaultEventSerializationService;
import org.trellisldp.vocabulary.AS;
import org.trellisldp.vocabulary.LDP;

@EnabledIfSystemProperty(named = "trellis.test.aws", matches = "true")
@ExtendWith(MockitoExtension.class)
class SNSEventServiceTest {

    private static final RDF rdf = RDFFactory.getInstance();
    private static final IRI identifier = rdf.createIRI("trellis:event/123456");
    private static final IRI object = rdf.createIRI("http://example.com/resource");
    private static final IRI agent = rdf.createIRI("http://example.com/agent");
    private static final Instant time = now();
    private static final EventSerializationService serializer = new DefaultEventSerializationService();

    @Mock
    private Event mockEvent;

    @Test
    @EnabledIfSystemProperty(named = "trellis.aws.topic", matches = "arn:aws:sns:.*")
    void testEvent() {
        when(mockEvent.getIdentifier()).thenReturn(identifier);
        when(mockEvent.getAgents()).thenReturn(singleton(agent));
        when(mockEvent.getObject()).thenReturn(of(object));
        when(mockEvent.getTypes()).thenReturn(singleton(AS.Create));
        when(mockEvent.getObjectTypes()).thenReturn(singleton(LDP.RDFSource));
        when(mockEvent.getCreated()).thenReturn(time);
        when(mockEvent.getInbox()).thenReturn(empty());

        final EventService svc = new SNSEventService(serializer);
        svc.emit(mockEvent);
        verify(mockEvent).getIdentifier();
    }

    @Test
    void testEventError() {
        when(mockEvent.getIdentifier()).thenReturn(identifier);
        when(mockEvent.getAgents()).thenReturn(singleton(agent));
        when(mockEvent.getObject()).thenReturn(of(object));
        when(mockEvent.getTypes()).thenReturn(singleton(AS.Create));
        when(mockEvent.getObjectTypes()).thenReturn(singleton(LDP.RDFSource));
        when(mockEvent.getCreated()).thenReturn(time);
        when(mockEvent.getInbox()).thenReturn(empty());

        final EventService svc = new SNSEventService(serializer, defaultClient(),
                "arn:aws:sns:us-east-1:12345678:NonExistent");
        svc.emit(mockEvent);
        verify(mockEvent).getIdentifier();
    }
}
