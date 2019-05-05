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
package org.trellisldp.ext.db.app;

import org.trellisldp.test.AbstractApplicationAuthTests;

abstract class AbstractAuthzTests extends AbstractApplicationAuthTests {

    @Override
    public String getUser1Credentials() {
        return "acoburn:secret";
    }

    @Override
    public String getUser2Credentials() {
        return "user:password";
    }

    @Override
    public String getJwtSecret() {
        return "gCjvrNoj8us4SXZQUENBunut85+s/XPN5T9+dxol8L2YXgY6QISuVd02oRcuPb/3ewrICaEnAGvm4QYdszgBIA==";
    }

    @Override
    public String getAdminWebId() {
        return "http://admin.example.com/#me";
    }
}
