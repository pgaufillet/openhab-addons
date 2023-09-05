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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wemo.internal.http.WemoHttpCall;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WemoHandler} is responsible for handling commands, which are
 * sent to one of the channels and to update their states.
 *
 * @author Hans-Jörg Merk - Initial contribution
 * @author Kai Kreuzer - some refactoring for performance and simplification
 * @author Stefan Bußweiler - Added new thing status handling
 * @author Erdoan Hadzhiyusein - Adapted the class to work with the new DateTimeType
 * @author Mihir Patil - Added standby switch
 */
@NonNullByDefault
public abstract class WemoHandler extends WemoBaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(WemoHandler.class);

    private final Object upnpLock = new Object();
    private final Object jobLock = new Object();

    private Map<String, Boolean> subscriptionState = new HashMap<>();

    private @Nullable ScheduledFuture<?> pollingJob;

    public WemoHandler(Thing thing, UpnpIOService upnpIOService, WemoHttpCall wemoHttpCaller) {
        super(thing, upnpIOService, wemoHttpCaller);

        logger.debug("Creating a WemoHandler for thing '{}'", getThing().getUID());
    }

    @Override
    public void initialize() {
        Configuration configuration = getConfig();

        if (configuration.get(UDN) != null) {
            logger.debug("Initializing WemoHandler for UDN '{}'", configuration.get(UDN));
            UpnpIOService localService = service;
            if (localService != null) {
                localService.registerParticipant(this);
            }
            host = getHost();
            pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, DEFAULT_REFRESH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/config-status.error.missing-udn");
            logger.debug("Cannot initalize WemoHandler. UDN not set.");
        }
    }

    @Override
    public void dispose() {
        logger.debug("WemoHandler disposed for thing {}", getThing().getUID());

        ScheduledFuture<?> job = this.pollingJob;
        if (job != null) {
            job.cancel(true);
        }
        this.pollingJob = null;
        removeSubscription();
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
                    synchronized (upnpLock) {
                        subscriptionState = new HashMap<>();
                    }
                    return;
                }
                updateStatus(ThingStatus.ONLINE);
                updateWemoState();
                addSubscription();
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
        } else if (CHANNEL_STATE.equals(channelUID.getId())) {
            if (command instanceof OnOffType) {
                try {
                    boolean binaryState = OnOffType.ON.equals(command) ? true : false;
                    String soapHeader = "\"urn:Belkin:service:basicevent:1#SetBinaryState\"";
                    String content = createBinaryStateContent(binaryState);
                    String wemoCallResponse = wemoHttpCaller.executeCall(wemoURL, soapHeader, content);
                    if (wemoCallResponse != null && logger.isTraceEnabled()) {
                        logger.trace("wemoCall to URL '{}' for device '{}'", wemoURL, getThing().getUID());
                        logger.trace("wemoCall with soapHeader '{}' for device '{}'", soapHeader, getThing().getUID());
                        logger.trace("wemoCall with content '{}' for device '{}'", content, getThing().getUID());
                        logger.trace("wemoCall with response '{}' for device '{}'", wemoCallResponse,
                                getThing().getUID());
                    }
                } catch (Exception e) {
                    logger.error("Failed to send command '{}' for device '{}': {}", command, getThing().getUID(),
                            e.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                }
                updateStatus(ThingStatus.ONLINE);
            }
        }
    }

    @Override
    public void onServiceSubscribed(@Nullable String service, boolean succeeded) {
        if (service != null) {
            logger.debug("WeMo {}: Subscription to service {} {}", getUDN(), service,
                    succeeded ? "succeeded" : "failed");
            subscriptionState.put(service, succeeded);
        }
    }

    private synchronized void addSubscription() {
        synchronized (upnpLock) {
            UpnpIOService localService = service;
            if (localService != null) {
                if (localService.isRegistered(this)) {
                    logger.debug("Checking WeMo GENA subscription for '{}'", getThing().getUID());

                    ThingTypeUID thingTypeUID = thing.getThingTypeUID();
                    String subscription = BASICEVENT;

                    if (subscriptionState.get(subscription) == null) {
                        logger.debug("Setting up GENA subscription {}: Subscribing to service {}...", getUDN(),
                                subscription);
                        localService.addSubscription(this, subscription, SUBSCRIPTION_DURATION_SECONDS);
                        subscriptionState.put(subscription, true);
                    }

                    if (THING_TYPE_INSIGHT.equals(thingTypeUID)) {
                        subscription = INSIGHTEVENT;
                        if (subscriptionState.get(subscription) == null) {
                            logger.debug("Setting up GENA subscription {}: Subscribing to service {}...", getUDN(),
                                    subscription);
                            localService.addSubscription(this, subscription, SUBSCRIPTION_DURATION_SECONDS);
                            subscriptionState.put(subscription, true);
                        }
                    }
                } else {
                    logger.debug(
                            "Setting up WeMo GENA subscription for '{}' FAILED - service.isRegistered(this) is FALSE",
                            getThing().getUID());
                }
            }
        }
    }

    private synchronized void removeSubscription() {
        synchronized (upnpLock) {
            UpnpIOService localService = service;
            if (localService != null) {
                if (localService.isRegistered(this)) {
                    logger.debug("Removing WeMo GENA subscription for '{}'", getThing().getUID());
                    ThingTypeUID thingTypeUID = thing.getThingTypeUID();
                    String subscription = BASICEVENT;

                    if (subscriptionState.get(subscription) != null) {
                        logger.debug("WeMo {}: Unsubscribing from service {}...", getUDN(), subscription);
                        localService.removeSubscription(this, subscription);
                    }

                    if (THING_TYPE_INSIGHT.equals(thingTypeUID)) {
                        subscription = INSIGHTEVENT;
                        if (subscriptionState.get(subscription) != null) {
                            logger.debug("WeMo {}: Unsubscribing from service {}...", getUDN(), subscription);
                            localService.removeSubscription(this, subscription);
                        }
                    }
                    subscriptionState = new HashMap<>();
                    localService.unregisterParticipant(this);
                }
            }
        }
    }

    /**
     * The {@link updateWemoState} polls the actual state of a WeMo device and
     * calls {@link onValueReceived} to update the statemap and channels..
     *
     */
    protected void updateWemoState() {
        String actionService = BASICACTION;
        String localhost = getHost();
        if (localhost.isEmpty()) {
            logger.error("Failed to get actual state for device '{}': IP address missing", getThing().getUID());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/config-status.error.missing-ip");
            return;
        }
        String action = "GetBinaryState";
        String variable = "BinaryState";
        String value = null;
        if ("insight".equals(getThing().getThingTypeUID().getId())) {
            action = "GetInsightParams";
            variable = "InsightParams";
            actionService = INSIGHTACTION;
        }
        String wemoURL = getWemoURL(localhost, actionService);
        if (wemoURL == null) {
            logger.error("Failed to get actual state for device '{}': URL cannot be created", getThing().getUID());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/config-status.error.missing-url");
            return;
        }
        String soapHeader = "\"urn:Belkin:service:" + actionService + ":1#" + action + "\"";
        String content = createStateRequestContent(action, actionService);
        try {
            String wemoCallResponse = wemoHttpCaller.executeCall(wemoURL, soapHeader, content);
            if (wemoCallResponse != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("wemoCall to URL '{}' for device '{}'", wemoURL, getThing().getUID());
                    logger.trace("wemoCall with soapHeader '{}' for device '{}'", soapHeader, getThing().getUID());
                    logger.trace("wemoCall with content '{}' for device '{}'", content, getThing().getUID());
                    logger.trace("wemoCall with response '{}' for device '{}'", wemoCallResponse, getThing().getUID());
                }
                if ("InsightParams".equals(variable)) {
                    value = substringBetween(wemoCallResponse, "<InsightParams>", "</InsightParams>");
                } else {
                    value = substringBetween(wemoCallResponse, "<BinaryState>", "</BinaryState>");
                }
                if (value.length() != 0) {
                    logger.trace("New state '{}' for device '{}' received", value, getThing().getUID());
                    this.onValueReceived(variable, value, actionService + "1");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get actual state for device '{}': {}", getThing().getUID(), e.getMessage());
        }
    }
}
