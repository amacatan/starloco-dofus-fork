package org.starloco.locos.login;

import java.util.regex.Pattern;

/**
 * Keeps authentication material out of the login logs while preserving enough
 * packet type information to diagnose the protocol flow.
 */
public final class LoginPacketRedactor {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern JWS = Pattern.compile(
            "^eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"
    );

    private LoginPacketRedactor() {
    }

    public static String incoming(String packet, LoginClient.Status status) {
        if (packet == null) {
            return "<null>";
        }
        if (status == LoginClient.Status.WAIT_PASSWORD || packet.startsWith("#1")) {
            return "#1" + REDACTED;
        }
        if (status == LoginClient.Status.WAIT_GAMESERVER_JWS || looksLikeJws(packet)) {
            return "JWS" + REDACTED;
        }
        return redactByPrefix(packet);
    }

    public static String outgoing(Object packet) {
        return redactByPrefix(packet == null ? "<null>" : packet.toString());
    }

    public static String exchange(String packet) {
        if (packet == null) {
            return "<null>";
        }
        // SK<server>;<shared-key>;<slots> authenticates a game server.
        if (packet.startsWith("SK") && packet.indexOf(';') >= 0) {
            return "SK" + REDACTED;
        }
        return packet;
    }

    private static String redactByPrefix(String packet) {
        if (packet.startsWith("HC")) {
            return "HC" + REDACTED;
        }
        if (packet.startsWith("AYK")) {
            return "AYK" + REDACTED;
        }
        if (packet.startsWith("AQ")) {
            return "AQ" + REDACTED;
        }
        if (packet.startsWith("#1")) {
            return "#1" + REDACTED;
        }
        if (looksLikeJws(packet)) {
            return "JWS" + REDACTED;
        }
        return packet;
    }

    private static boolean looksLikeJws(String packet) {
        return JWS.matcher(packet).matches();
    }
}
