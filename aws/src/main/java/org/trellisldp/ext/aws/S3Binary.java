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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;

import java.io.InputStream;

import org.trellisldp.api.Binary;

/**
 * A Trellis binary implementation, based on an S3 data storage layer.
 */
public class S3Binary implements Binary {

    private final AmazonS3 client;
    private final String bucketName;
    private final String key;

    /**
     * Create an S3-based Binary object.
     * @param client the aws client
     * @param bucketName the bucket name
     * @param key the key
     */
    public S3Binary(final AmazonS3 client, final String bucketName, final String key) {
        this.client = client;
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public InputStream getContent() {
        return client.getObject(new GetObjectRequest(bucketName, key)).getObjectContent();
    }

    @Override
    public InputStream getContent(final int from, final int to) {
        return client
                .getObject(new GetObjectRequest(bucketName, key).withRange(from, to)).getObjectContent();
    }
}
