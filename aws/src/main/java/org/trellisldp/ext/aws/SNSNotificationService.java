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
import static java.util.Objects.requireNonNull;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.slf4j.LoggerFactory.getLogger;

import com.amazonaws.services.sns.AmazonSNS;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.trellisldp.api.Notification;
import org.trellisldp.api.NotificationSerializationService;
import org.trellisldp.api.NotificationService;

/**
 * An SNS notification service.
 */
@ApplicationScoped
public class SNSNotificationService implements NotificationService {

    public static final String CONFIG_AWS_TOPIC = "trellis.aws.topic";

    private static final Logger LOGGER = getLogger(SNSNotificationService.class);

    private final NotificationSerializationService serializer;
    private final AmazonSNS sns;
    private final String topic;

    /**
     * Cretae an SNS-bases notification service.
     * @param serializer the notification serializer
     */
    @Inject
    public SNSNotificationService(final NotificationSerializationService serializer) {
        this(serializer, defaultClient(), getConfig().getValue(CONFIG_AWS_TOPIC, String.class));
    }

    /**
     * Cretae an SNS-bases notification service.
     * @param serializer the notification serializer
     * @param client the SNS client
     * @param topic the topic ARN
     */
    public SNSNotificationService(final NotificationSerializationService serializer, final AmazonSNS client,
            final String topic) {
        this.serializer = requireNonNull(serializer, "the notification serializer may not be null!");
        this.sns = requireNonNull(client, "the SNS client may not be null!");
        this.topic = requireNonNull(topic, "the SNS topic may not be null!");
        LOGGER.info("Using AWS for notifications. SNS topic: {}", topic);
    }

    @Override
    public void emit(final Notification notification) {
        requireNonNull(notification, "Cannot emit a null notification!");
        try {
            sns.publish(topic, serializer.serialize(notification));
        } catch (final Exception ex) {
            LOGGER.error("Error writing to SNS topic {}: {}", topic, ex.getMessage());
        }
    }
}
