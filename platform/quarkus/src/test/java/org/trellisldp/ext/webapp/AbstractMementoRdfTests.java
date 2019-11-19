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
package org.trellisldp.ext.webapp;

import static javax.ws.rs.client.ClientBuilder.newBuilder;

import io.quarkus.test.common.http.TestHTTPResource;

import java.net.URL;

import javax.ws.rs.client.Client;

import org.trellisldp.test.MementoResourceTests;

abstract class AbstractMementoRdfTests implements MementoResourceTests {

    private static final Client client = newBuilder().build();

    @TestHTTPResource
    URL url;

    private String resource;
    private String binary;

    @Override
    public Client getClient() {
        return client;
    }

    @Override
    public String getBaseURL() {
        return url.toString();
    }

    @Override
    public void setBinaryLocation(final String location) {
        binary = location;
    }

    @Override
    public String getBinaryLocation() {
        return binary;
    }

    @Override
    public void setResourceLocation(final String location) {
        resource = location;
    }

    @Override
    public String getResourceLocation() {
        return resource;
    }
}
