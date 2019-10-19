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

import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.Map;

import org.trellisldp.api.NamespaceService;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.AS;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.FOAF;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Memento;
import org.trellisldp.vocabulary.OA;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.RDFS;
import org.trellisldp.vocabulary.SKOS;
import org.trellisldp.vocabulary.Time;
import org.trellisldp.vocabulary.VCARD;
import org.trellisldp.vocabulary.XSD;

/**
 * A simple, in-memory namespace service.
 *
 * <p>This service will load some standard namespaces/prefixes and read
 * system properties into the namespace maping if they are defined like so:
 * "trellis.ns-myprefix=http://example.com/namespace"
 */
public class DefaultNamespaceService implements NamespaceService {

    private static final String PREFIX = "trellis.ns-";

    private final Map<String, String> namespaces = new HashMap<>();

    /**
     * Create a simple, in-memory namespace service.
     */
    public DefaultNamespaceService() {
        namespaces.put("ldp", LDP.getNamespace());
        namespaces.put("acl", ACL.getNamespace());
        namespaces.put("as", AS.getNamespace());
        namespaces.put("dc", DC.getNamespace());
        namespaces.put("memento", Memento.getNamespace());
        namespaces.put("prov", PROV.getNamespace());
        namespaces.put("rdf", RDF.getNamespace());
        namespaces.put("rdfs", RDFS.getNamespace());
        namespaces.put("skos", SKOS.getNamespace());
        namespaces.put("time", Time.getNamespace());
        namespaces.put("xsd", XSD.getNamespace());
        namespaces.put("foaf", FOAF.getNamespace());
        namespaces.put("oa", OA.getNamespace());
        namespaces.put("vcard", VCARD.getNamespace());
        System.getProperties().stringPropertyNames().forEach(k -> {
            if (k.startsWith(PREFIX)) {
                namespaces.put(k.substring(PREFIX.length()), System.getProperty(k));
            }
        });
    }

    @Override
    public Map<String, String> getNamespaces() {
        return unmodifiableMap(namespaces);
    }

    @Override
    public boolean setPrefix(final String prefix, final String namespace) {
        return true;
    }
}
