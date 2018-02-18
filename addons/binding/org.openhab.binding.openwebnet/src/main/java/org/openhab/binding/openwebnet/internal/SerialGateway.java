/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openwebnet.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.TooManyListenersException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.openwebnet.internal.exception.PortNotConnected;
import org.openhab.binding.openwebnet.internal.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.NRSerialPort;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 * The {@link SerialGateway} class manages the connection to a serial gateway (mainly used for Zigbee)
 *
 * @author Antoine Laydier
 *
 */
@NonNullByDefault
class SerialGateway extends InternalGateway implements SerialPortEventListener {

    private static final int BAUD_RATE = 19200; // Baud rate as per documentation

    @SuppressWarnings("null")
    private final Logger logger = LoggerFactory.getLogger(SerialGateway.class);
    @SuppressWarnings("null")
    private final ByteBuffer inputBuffer = ByteBuffer.allocate(200);;

    private final NRSerialPort serial;
    private final String portName;

    public SerialGateway(Parser parser, String serialPortName) {
        super(parser);
        this.serial = new NRSerialPort(serialPortName, BAUD_RATE);
        this.portName = serialPortName;
    }

    @Override
    public void connect() {
        serial.connect();
        try {
            serial.addEventListener(this);
        } catch (TooManyListenersException e) {
            // if this happens, the registration have already been done.
        }
        notifyConnected();
    }

    @Override
    public void write(@Nullable String data) throws IOException {
        if (data == null) {
            logger.warn("Try to write an 'null' message");
            return;
        }
        if (serial.isConnected()) {
            logger.debug("Writing \"{}\" on serial port {}", data, portName);
            serial.getOutputStream().write(data.getBytes());
        } else {
            logger.warn("Write attempt of \"{}\" failed to to unconnected serial port {}", data, portName);
            throw new PortNotConnected("Serial port " + this.toString() + " not connected. Write cannot be performed.");
        }
    }

    @Override
    public void close() {
        if (serial.isConnected()) {
            serial.disconnect();
            notifyDisconnected();
        }
    }

    /* SerialPortEventListener */
    @Override
    public void serialEvent(@Nullable SerialPortEvent ev) {
        if ((ev == null) || (ev.getEventType() != SerialPortEvent.DATA_AVAILABLE)) {
            logger.warn("Unexpected serialEvent wake-up");
            return;
        }

        @SuppressWarnings("null")
        Optional<SerialPort> srcPort = Optional.of((SerialPort) ev.getSource());
        srcPort.ifPresent(value -> {
            try {
                InputStream evStream = value.getInputStream();
                inputBuffer.clear();
                int position = evStream.read(inputBuffer.array(), inputBuffer.position(), inputBuffer.remaining());
                inputBuffer.position(position);
                inputBuffer.flip();
                logger.debug("<{}> received.", inputBuffer.toString());
                getParser().checkInput(evStream);
                getParser().parse(inputBuffer);
            } catch (IOException e) {
                // FIXME: silent fail for now.
                logger.warn("read failure ({})", e.getLocalizedMessage());
            }
        });
        logger.debug("End of serialEvent.");
    }

    @Override
    public String toString() {
        return "Serial gateway on " + portName;
    }
}
