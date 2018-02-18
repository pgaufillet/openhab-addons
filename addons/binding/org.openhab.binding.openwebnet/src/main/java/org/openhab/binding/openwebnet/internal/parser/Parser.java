/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openwebnet.internal.parser;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.openwebnet.internal.listener.ResponseListener;
import org.openhab.binding.openwebnet.internal.parser.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * The {@link Parser} class parses the received Bytes from serial line, generate callback.
 *
 * @author Antoine Laydier
 *
 */
@NonNullByDefault
public class Parser {

    private static final String OPENWEBNET_SEPARATOR = "##";

    @SuppressWarnings("null")
    private final Logger logger = LoggerFactory.getLogger(Parser.class);

    private class InnerThread extends Thread {

        private LinkedBlockingQueue<CharBuffer> queue;
        private StringBuilder buffer;

        InnerThread(LinkedBlockingQueue<CharBuffer> queue) {
            this.buffer = new StringBuilder();
            this.queue = queue;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("Parser Thread");
            logger.debug("{} start", this);
            boolean loop = true;
            while (loop) {
                try {
                    buffer.append(queue.take());
                    int position;
                    while ((position = buffer.indexOf(Parser.OPENWEBNET_SEPARATOR)) >= 0) {
                        position += Parser.OPENWEBNET_SEPARATOR.length();
                        @Nullable
                        String message = buffer.substring(0, position);
                        if (message != null) {
                            logger.debug("{} message {} received", this, message);
                            buffer.delete(0, position);
                            Response answer = Response.find(message);
                            if (answer == null) {
                                logger.warn("\"{}\" received but not understood", message);
                            } else {
                                logger.debug("\"{}\" received ", answer.getClass().getSimpleName());
                                answer.process(message, listener);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    // leave
                    loop = false;
                } catch (Exception e2) {
                    logger.error("{} -> Unexpected error.", this, e2);
                }
            }
            logger.debug("{} finish", this);
        }
    }

    private Thread worker;

    private @Nullable InputStream currentStream; // only used to check if the InputStream has changed (no read from it)
    private ResponseListener listener;
    private LinkedBlockingQueue<CharBuffer> queue;

    /**
     * Define the packet separator of OpenWebNet protocol
     *
     * @param listener listener to for callback
     *
     */
    public Parser(ResponseListener listener) {
        this.currentStream = null;
        this.listener = listener;
        this.queue = new LinkedBlockingQueue<>();
        this.worker = new InnerThread(queue);
        this.worker.start();
    }

    /**
     * Parse a received buffer for messages
     *
     * @param newData buffer to parse
     */
    // Only needed for Maven
    @SuppressWarnings("null")
    public void parse(ByteBuffer newData) {
        queue.add(StandardCharsets.US_ASCII.decode(newData));
    }

    /**
     * Check is stream provided is the same as current one, if not reset the input buffer.
     * This method need to be called prior to can any parse().
     */
    public void checkInput(InputStream stream) {
        if (currentStream == null) {
            currentStream = stream;
        }
        if (!stream.equals(currentStream)) {
            logger.debug("Stream changed ... Flush and create new inner task");
            worker.interrupt();
            this.queue = new LinkedBlockingQueue<>();
            this.worker = new InnerThread(queue);
            this.worker.start();
        }
    }

}
