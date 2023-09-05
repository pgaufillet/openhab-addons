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
package org.openhab.binding.wemo.internal.handler;

import static org.openhab.binding.wemo.internal.WemoBindingConstants.*;
import static org.openhab.binding.wemo.internal.WemoUtil.*;

import java.io.StringReader;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wemo.internal.http.WemoHttpCall;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.upnp.UpnpIOParticipant;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The {@link WemoMakerHandler} is responsible for handling commands, which are
 * sent to one of the channels and to update their states.
 *
 * @author Hans-Jörg Merk - Initial contribution
 */
@NonNullByDefault
public class WemoMakerHandler extends AbstractWemoHandler implements UpnpIOParticipant {

    private final Logger logger = LoggerFactory.getLogger(WemoMakerHandler.class);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_MAKER);

    private final Object jobLock = new Object();

    private @Nullable UpnpIOService service;

    private WemoHttpCall wemoCall;

    private String host = "";

    private @Nullable ScheduledFuture<?> pollingJob;

    public WemoMakerHandler(Thing thing, UpnpIOService upnpIOService, WemoHttpCall wemoHttpcaller) {
        super(thing, wemoHttpcaller);

        this.service = upnpIOService;
        this.wemoCall = wemoHttpcaller;

        logger.debug("Creating a WemoMakerHandler for thing '{}'", getThing().getUID());
    }

    @Override
    public void initialize() {
        Configuration configuration = getConfig();

        if (configuration.get(UDN) != null) {
            logger.debug("Initializing WemoMakerHandler for UDN '{}'", configuration.get(UDN));
            UpnpIOService localService = service;
            if (localService != null) {
                localService.registerParticipant(this);
            }
            host = getHost();
            pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, DEFAULT_REFRESH_INTERVALL_SECONDS,
                    TimeUnit.SECONDS);
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/config-status.error.missing-udn");
            logger.debug("Cannot initalize WemoMakerHandler. UDN not set.");
        }
    }

    @Override
    public void dispose() {
        logger.debug("WeMoMakerHandler disposed.");

        ScheduledFuture<?> job = this.pollingJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(true);
        }
        this.pollingJob = null;
        UpnpIOService localService = service;
        if (localService != null) {
            localService.unregisterParticipant(this);
        }
    }

    private void poll() {
        synchronized (jobLock) {
            if (pollingJob == null) {
                return;
            }
            try {
                logger.debug("Polling job");
                host = getHost();
                // Check if the Wemo device is set in the UPnP service registry
                // If not, set the thing state to ONLINE/CONFIG-PENDING and wait for the next poll
                if (!isUpnpDeviceRegistered()) {
                    logger.debug("UPnP device {} not yet registered", getUDN());
                    updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                            "@text/config-status.pending.device-not-registered [\"" + getUDN() + "\"]");
                    return;
                }
                updateStatus(ThingStatus.ONLINE);
                updateWemoState();
            } catch (Exception e) {
                logger.debug("Exception during poll: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String localHost = getHost();
        if (localHost.isEmpty()) {
            logger.error("Failed to send command '{}' for device '{}': IP address missing", command,
                    getThing().getUID());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/config-status.error.missing-ip");
            return;
        }
        String wemoURL = getWemoURL(localHost, BASICACTION);
        if (wemoURL == null) {
            logger.error("Failed to send command '{}' for device '{}': URL cannot be created", command,
                    getThing().getUID());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/config-status.error.missing-url");
            return;
        }
        if (command instanceof RefreshType) {
            try {
                updateWemoState();
            } catch (Exception e) {
                logger.debug("Exception during poll", e);
            }
        } else if (channelUID.getId().equals(CHANNEL_RELAY)) {
            if (command instanceof OnOffType) {
                try {
                    boolean binaryState = OnOffType.ON.equals(command) ? true : false;
                    String soapHeader = "\"urn:Belkin:service:basicevent:1#SetBinaryState\"";
                    String content = createBinaryStateContent(binaryState);
                    String wemoCallResponse = wemoCall.executeCall(wemoURL, soapHeader, content);
                    if (wemoCallResponse != null && logger.isTraceEnabled()) {
                        logger.trace("wemoCall to URL '{}' for device '{}'", wemoURL, getThing().getUID());
                        logger.trace("wemoCall with soapHeader '{}' for device '{}'", soapHeader, getThing().getUID());
                        logger.trace("wemoCall with content '{}' for device '{}'", content, getThing().getUID());
                        logger.trace("wemoCall with response '{}' for device '{}'", wemoCallResponse,
                                getThing().getUID());
                    }
                } catch (Exception e) {
                    logger.error("Failed to send command '{}' for device '{}' ", command, getThing().getUID(), e);
                }
            }
        }
    }

    private boolean isUpnpDeviceRegistered() {
        UpnpIOService localService = service;
        if (localService != null) {
            return localService.isRegistered(this);
        }
        return false;
    }

    @Override
    public String getUDN() {
        return (String) this.getThing().getConfiguration().get(UDN);
    }

    /**
     * The {@link updateWemoState} polls the actual state of a WeMo Maker.
     */
    protected void updateWemoState() {
        String localHost = getHost();
        if (localHost.isEmpty()) {
            logger.error("Failed to get actual state for device '{}': IP address missing", getThing().getUID());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/config-status.error.missing-ip");
            return;
        }
        String actionService = DEVICEACTION;
        String wemoURL = getWemoURL(localHost, actionService);
        if (wemoURL == null) {
            logger.error("Failed to get actual state for device '{}': URL cannot be created", getThing().getUID());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/config-status.error.missing-url");
            return;
        }
        try {
            String action = "GetAttributes";
            String soapHeader = "\"urn:Belkin:service:" + actionService + ":1#" + action + "\"";
            String content = createStateRequestContent(action, actionService);
            String wemoCallResponse = wemoCall.executeCall(wemoURL, soapHeader, content);
            if (wemoCallResponse != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("wemoCall to URL '{}' for device '{}'", wemoURL, getThing().getUID());
                    logger.trace("wemoCall with soapHeader '{}' for device '{}'", soapHeader, getThing().getUID());
                    logger.trace("wemoCall with content '{}' for device '{}'", content, getThing().getUID());
                    logger.trace("wemoCall with response '{}' for device '{}'", wemoCallResponse, getThing().getUID());
                }
                try {
                    String stringParser = substringBetween(wemoCallResponse, "<attributeList>", "</attributeList>");
                    logger.trace("Escaped Maker response for device '{}' :", getThing().getUID());
                    logger.trace("'{}'", stringParser);

                    // Due to Belkins bad response formatting, we need to run this twice.
                    stringParser = unescapeXml(stringParser);
                    stringParser = unescapeXml(stringParser);
                    logger.trace("Maker response '{}' for device '{}' received", stringParser, getThing().getUID());

                    stringParser = "<data>" + stringParser + "</data>";

                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    // see
                    // https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html
                    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    dbf.setXIncludeAware(false);
                    dbf.setExpandEntityReferences(false);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    InputSource is = new InputSource();
                    is.setCharacterStream(new StringReader(stringParser));

                    Document doc = db.parse(is);
                    NodeList nodes = doc.getElementsByTagName("attribute");

                    // iterate the attributes
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element element = (Element) nodes.item(i);

                        NodeList deviceIndex = element.getElementsByTagName("name");
                        Element line = (Element) deviceIndex.item(0);
                        String attributeName = getCharacterDataFromElement(line);
                        logger.trace("attributeName: {}", attributeName);

                        NodeList deviceID = element.getElementsByTagName("value");
                        line = (Element) deviceID.item(0);
                        String attributeValue = getCharacterDataFromElement(line);
                        logger.trace("attributeValue: {}", attributeValue);

                        switch (attributeName) {
                            case "Switch":
                                State relayState = "0".equals(attributeValue) ? OnOffType.OFF : OnOffType.ON;
                                logger.debug("New relayState '{}' for device '{}' received", relayState,
                                        getThing().getUID());
                                updateState(CHANNEL_RELAY, relayState);
                                break;
                            case "Sensor":
                                State sensorState = "1".equals(attributeValue) ? OnOffType.OFF : OnOffType.ON;
                                logger.debug("New sensorState '{}' for device '{}' received", sensorState,
                                        getThing().getUID());
                                updateState(CHANNEL_SENSOR, sensorState);
                                break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse attributeList for WeMo Maker '{}'", this.getThing().getUID(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get attributes for device '{}'", getThing().getUID(), e);
        }
    }

    public String getHost() {
        String localHost = host;
        if (!localHost.isEmpty()) {
            return localHost;
        }
        UpnpIOService localService = service;
        if (localService != null) {
            URL descriptorURL = localService.getDescriptorURL(this);
            if (descriptorURL != null) {
                return descriptorURL.getHost();
            }
        }
        return "";
    }

    @Override
    public void onStatusChanged(boolean status) {
    }

    @Override
    public void onServiceSubscribed(@Nullable String service, boolean succeeded) {
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
    }
}
