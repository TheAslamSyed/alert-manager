package com.democorp.bank.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes logfmt lines (service=... msg="..." key=value) so Loki queries like
 * {@code {app="democorp-bank"} | logfmt | service="payment-service"} work.
 */
public final class BankLog {

    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    private BankLog() {
    }

    public static void info(String service, String msg, Object... kv) {
        logger(service).info(line(service, msg, kv));
    }

    public static void warn(String service, String msg, Object... kv) {
        logger(service).warn(line(service, msg, kv));
    }

    public static void error(String service, String msg, Object... kv) {
        logger(service).error(line(service, msg, kv));
    }

    private static Logger logger(String service) {
        return LOGGERS.computeIfAbsent(service, s -> LoggerFactory.getLogger("democorp." + s));
    }

    static String line(String service, String msg, Object... kv) {
        StringBuilder sb = new StringBuilder("service=").append(service)
                .append(" msg=\"").append(msg.replace('"', '\'')).append('"');
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String value = String.valueOf(kv[i + 1]);
            sb.append(' ').append(kv[i]).append('=');
            if (value.contains(" ")) {
                sb.append('"').append(value.replace('"', '\'')).append('"');
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }
}
