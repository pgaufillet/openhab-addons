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
package org.openhab.binding.vizio.internal.console;

import static org.openhab.binding.vizio.internal.VizioBindingConstants.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.vizio.internal.VizioException;
import org.openhab.binding.vizio.internal.communication.VizioCommunicator;
import org.openhab.binding.vizio.internal.dto.pairing.PairingComplete;
import org.openhab.binding.vizio.internal.handler.VizioHandler;
import org.openhab.core.io.console.Console;
import org.openhab.core.io.console.extensions.AbstractConsoleCommandExtension;
import org.openhab.core.io.console.extensions.ConsoleCommandExtension;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link VizioCommandExtension} is responsible for handling console commands
 *
 * @author Michael Lobstein - Initial contribution
 */

@NonNullByDefault
@Component(service = ConsoleCommandExtension.class)
public class VizioCommandExtension extends AbstractConsoleCommandExtension {
    private static final String START_PAIRING = "start_pairing";
    private static final String SUBMIT_CODE = "submit_code";

    private final ThingRegistry thingRegistry;
    private final HttpClient httpClient;

    @Activate
    public VizioCommandExtension(final @Reference ThingRegistry thingRegistry,
            final @Reference HttpClientFactory httpClientFactory) {
        super("vizio", "Interact with the Vizio binding to get an authentication token from the TV.");
        this.thingRegistry = thingRegistry;
        this.httpClient = httpClientFactory.getCommonHttpClient();
    }

    @Override
    public void execute(String[] args, Console console) {
        if (args.length == 3) {
            Thing thing = null;
            try {
                ThingUID thingUID = new ThingUID(args[0]);
                thing = thingRegistry.get(thingUID);
            } catch (IllegalArgumentException e) {
                thing = null;
            }
            ThingHandler thingHandler = null;
            VizioHandler handler = null;
            if (thing != null) {
                thingHandler = thing.getHandler();
                if (thingHandler instanceof VizioHandler) {
                    handler = (VizioHandler) thingHandler;
                }
            }
            if (thing == null) {
                console.println("Bad thing id '" + args[0] + "'");
                printUsage(console);
            } else if (thingHandler == null) {
                console.println("No handler initialized for the thing id '" + args[0] + "'");
                printUsage(console);
            } else if (handler == null) {
                console.println("'" + args[0] + "' is not a Vizio thing id");
                printUsage(console);
            } else {
                String host = (String) thing.getConfiguration().get(PROPERTY_HOST_NAME);
                BigDecimal port = (BigDecimal) thing.getConfiguration().get(PROPERTY_PORT);

                if (host == null || host.isEmpty() || port.signum() < 1) {
                    console.println(
                            "Error! Host Name and Port must be specified in thing configuration before paring.");
                    return;
                } else if (host.contains(":")) {
                    // format for ipv6
                    host = "[" + host + "]";
                }

                VizioCommunicator communicator = new VizioCommunicator(httpClient, host, port.intValue(), EMPTY);

                switch (args[1]) {
                    case START_PAIRING:
                        try {
                            Random rng = new Random();

                            int pairingDeviceId = rng.nextInt(100000);
                            int pairingToken = communicator.starPairing(args[2], pairingDeviceId).getItem()
                                    .getPairingReqToken();
                            if (pairingToken != -1) {
                                handler.setPairingDeviceId(pairingDeviceId);
                                handler.setPairingToken(pairingToken);

                                console.println("Pairing has been started!");
                                console.println(
                                        "Please note the 4 digit code displayed on the TV and substitute it into the following console command:");
                                console.println(
                                        "openhab:vizio " + handler.getThing().getUID() + " " + SUBMIT_CODE + " <NNNN>");
                            } else {
                                console.println("Unable to obtain pairing token!");
                            }
                        } catch (VizioException e) {
                            console.println("Error! Unable to start pairing process.");
                            console.println("Exception was: " + e.getMessage());
                        }
                        break;
                    case SUBMIT_CODE:
                        try {
                            int pairingDeviceId = handler.getPairingDeviceId();
                            int pairingToken = handler.getPairingToken();

                            if (pairingDeviceId < 0 || pairingToken < 0) {
                                console.println("Error! '" + START_PAIRING + "' command must be completed first.");
                                console.println(
                                        "Please issue the following command and substitute the desired device name.");
                                console.println("openhab:vizio " + handler.getThing().getUID() + " " + START_PAIRING
                                        + " <deviceName>");
                                break;
                            }

                            Integer.valueOf(args[2]);
                            PairingComplete authTokenResp = communicator.submitPairingCode(pairingDeviceId, args[2],
                                    pairingToken);
                            if (authTokenResp.getItem().getAuthToken() != EMPTY) {
                                console.println("Pairing complete!");
                                console.println("The auth token: " + authTokenResp.getItem().getAuthToken()
                                        + " was received and will be added to the thing configuration.");
                                console.println(
                                        "If the thing is provisioned via a file, the token must be manually added to the thing configuration.");

                                handler.setPairingDeviceId(-1);
                                handler.setPairingToken(-1);
                                handler.saveAuthToken(authTokenResp.getItem().getAuthToken());
                            } else {
                                console.println("Unable to obtain auth token!");
                            }
                        } catch (NumberFormatException nfe) {
                            console.println(
                                    "Error! Pairing code must be numeric. Check console command and try again.");
                        } catch (VizioException e) {
                            console.println("Error! Unable to complete pairing process.");
                            console.println("Exception was: " + e.getMessage());
                        }
                        break;
                    default:
                        printUsage(console);
                        break;
                }
            }
        } else {
            printUsage(console);
        }
    }

    @Override
    public List<String> getUsages() {
        return List.of(new String[] {
                buildCommandUsage("<thingUID> " + START_PAIRING + " <deviceName>", "start pairing process"),
                buildCommandUsage("<thingUID> " + SUBMIT_CODE + " <pairingCode>", "submit pairing code") });
    }
}
