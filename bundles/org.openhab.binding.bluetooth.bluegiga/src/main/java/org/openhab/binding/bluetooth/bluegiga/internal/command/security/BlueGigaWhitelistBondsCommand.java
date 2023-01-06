/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.bluetooth.bluegiga.internal.command.security;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.bluetooth.bluegiga.internal.BlueGigaCommand;

/**
 * Class to implement the BlueGiga command <b>whitelistBonds</b>.
 * <p>
 * This command will add all bonded devices with a known public or static address to the local
 * devices white list. Previous entries in the white list will be first cleared. This command
 * can't be used while advertising, scanning or being connected.
 * <p>
 * This class provides methods for processing BlueGiga API commands.
 * <p>
 * Note that this code is autogenerated. Manual changes may be overwritten.
 *
 * @author Chris Jackson - Initial contribution of Java code generator
 */
@NonNullByDefault
public class BlueGigaWhitelistBondsCommand extends BlueGigaCommand {
    public static int COMMAND_CLASS = 0x05;
    public static int COMMAND_METHOD = 0x07;

    @Override
    public int[] serialize() {
        // Serialize the header
        serializeHeader(COMMAND_CLASS, COMMAND_METHOD);

        return getPayload();
    }

    @Override
    public String toString() {
        return "BlueGigaWhitelistBondsCommand []";
    }
}
