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
package org.trellisldp.ext.quarkus;

import static org.trellisldp.app.AppUtils.printBanner;

import io.quarkus.arc.DefaultBean;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.trellisldp.common.DefaultTimemapGenerator;
import org.trellisldp.common.TimemapGenerator;

/**
 * Web Application wrapper.
 */
@ApplicationPath("/")
@ApplicationScoped
public class TrellisApplication extends Application {

    private TimemapGenerator timemapGenerator = new DefaultTimemapGenerator();

    @PostConstruct
    void init() throws IOException {
        printBanner("Trellis Database Application", "org/trellisldp/app/banner.txt");
    }

    @Produces
    @DefaultBean
    TimemapGenerator getTimemapGenerator() {
        return timemapGenerator;
    }
}
