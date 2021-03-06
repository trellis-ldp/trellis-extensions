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
package org.trellisldp.ext.cassandra;

import org.trellisldp.api.TrellisRuntimeException;

/**
 * Thrown to indicate that application initialization was interrupted.
 */
public class InterruptedStartupException extends TrellisRuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * An application initialization exception.
     * @param message the message
     * @param cause the cause
     */
    public InterruptedStartupException(final String message, final InterruptedException cause) {
        super(message, cause);
    }
}
