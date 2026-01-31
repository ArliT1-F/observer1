package com.icy404.observer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogUtil {
    public static final String MOD_ID = "observer";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private LogUtil() {
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
}