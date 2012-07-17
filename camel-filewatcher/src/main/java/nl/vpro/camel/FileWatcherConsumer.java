/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.vpro.camel;

import java.io.File;
import java.net.URI;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultScheduledPollConsumer;
import org.apache.camel.util.FileUtil;

public class FileWatcherConsumer extends DefaultScheduledPollConsumer {
    public static enum Event {
        STARTED, CREATED, UPDATED, DELETED
    }

    private final FileWatcherEndpoint endpoint;

    private File watchedFile;

    private long previousLastModified = 0;

    private boolean firstPoll = true;

    FileWatcherConsumer(FileWatcherEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        if(noFileSinceStartup()) {
            firstPoll = false;
            return 0;
        }

        if(previousFileWasDeleted()) {
            exchangeFileDeleted();
            // do not update timestamp when an exception is not handled
            previousLastModified = 0;
            return 1;
        }

        final long newLastModified = watchedFile.lastModified();

        if(notModified(newLastModified)) {
            return 0;
        }

        Event event;
        if(routeStartedOrFileCreated()) {
            if(firstPoll) {
                event = Event.STARTED;
            } else {
                event = Event.CREATED;
            }
        } else {
            event = Event.UPDATED;
        }

        exchangeFile(event);
        // do not update fields when an exception is not handled
        this.previousLastModified = newLastModified;
        this.firstPoll = false;
        return 1;
    }

    @Override
    protected void doStart() throws Exception {
        String watchedFilePath = resolvePath();
        watchedFile = new File(watchedFilePath);

        if(watchedFile.isDirectory()) {
            throw new IllegalArgumentException("Endpoint " + endpoint + " should not point to a directory + " + watchedFile.getAbsolutePath());
        }

        if(watchedFile.exists() && !watchedFile.canRead()) {
            throw new SecurityException("Can not read file: " + watchedFile.getAbsolutePath());
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        previousLastModified = 0;
        firstPoll = true;
    }

    private boolean noFileSinceStartup() {
        return previousLastModified == 0 && !watchedFile.exists();
    }

    private boolean previousFileWasDeleted() {
        return previousLastModified > 0 && !watchedFile.exists();
    }

    private boolean notModified(long newLastModified) {
        return newLastModified <= this.previousLastModified;
    }

    private boolean routeStartedOrFileCreated() {
        return this.previousLastModified == 0 && watchedFile.exists();
    }

    private String resolvePath() {
        URI uri = endpoint.getEndpointConfiguration().getURI();
        String authority = uri.getAuthority();
        String path = uri.getPath();

        if(authority == null) {
            return FileUtil.normalizePath(path);  // absolute path
        }

        return FileUtil.normalizePath(authority + path);
    }

    private void exchangeFileDeleted() throws Exception {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader("fileWatchEvent", Event.DELETED.name());
        process(exchange);
    }

    private void exchangeFile(Event event) throws Exception {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader("fileWatchEvent", event.name());
        exchange.getIn().setBody(watchedFile);
        process(exchange);
    }

    private void process(Exchange exchange) throws Exception {
        try {
            getProcessor().process(exchange);
        } finally {
            if(exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
