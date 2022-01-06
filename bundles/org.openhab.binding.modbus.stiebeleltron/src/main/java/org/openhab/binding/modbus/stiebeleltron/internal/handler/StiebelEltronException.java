/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.modbus.stiebeleltron.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Thrown when the stiebel eltron handler sees an error.
 *
 * @author Paul Frank - Initial contribution
 */
@SuppressWarnings("serial")
@NonNullByDefault
public class StiebelEltronException extends Exception {

    public StiebelEltronException() {
    }

    public StiebelEltronException(String message) {
        super(message);
    }
}
