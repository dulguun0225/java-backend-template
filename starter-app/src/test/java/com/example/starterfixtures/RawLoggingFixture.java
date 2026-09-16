package com.example.starterfixtures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RawLoggingFixture {
    private static final Logger LOG = LoggerFactory.getLogger(RawLoggingFixture.class);

    void log() {
        LOG.info("raw");
    }
}
