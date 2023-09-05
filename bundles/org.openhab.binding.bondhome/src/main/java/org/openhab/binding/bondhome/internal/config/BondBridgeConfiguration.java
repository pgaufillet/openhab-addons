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
package org.openhab.binding.bondhome.internal.config;

import static org.openhab.binding.bondhome.internal.BondHomeBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link BondBridgeConfiguration} class contains fields mapping thing
 * configuration parameters.
 *
 * @author Sara Geleskie Damiano - Initial contribution
 */
@NonNullByDefault
public class BondBridgeConfiguration {

    /**
     * Configuration for a Bond Bridge
     */
    public @Nullable String serialNumber;
    public @Nullable String localToken;
    public @Nullable String ipAddress;

    public @Nullable String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
