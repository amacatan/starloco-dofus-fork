package org.starloco.locos.login;

public final class LoginLoggingChecks {
    private LoginLoggingChecks() {
    }

    public static void run() {
        String salt = "abcdefghijklmnopqrstuvwxyzabcdef";
        String encryptedPassword = "#1abcdefghijklmnopqrstuvwxyz";
        String jws = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb3VldXIifQ.signature";
        String ticket = "AYK127.0.0.1:5555;42";
        String secretQuestion = "Nom de mon premier familier ?";
        String exchangeKey = "shared-game-server-key";

        checkRedacted(
                LoginPacketRedactor.outgoing("HC" + salt),
                salt,
                "The HC salt must never be written to the logs"
        );
        checkRedacted(
                LoginPacketRedactor.incoming(encryptedPassword, LoginClient.Status.WAIT_PASSWORD),
                encryptedPassword.substring(2),
                "The encrypted password must never be written to the logs"
        );
        checkRedacted(
                LoginPacketRedactor.incoming("raw-password-payload", LoginClient.Status.WAIT_PASSWORD),
                "raw-password-payload",
                "Prefix-less legacy password payloads must also be redacted"
        );
        checkRedacted(
                LoginPacketRedactor.incoming(jws, LoginClient.Status.WAIT_GAMESERVER_JWS),
                jws,
                "The game-server JWS must never be written to the logs"
        );
        checkRedacted(
                LoginPacketRedactor.outgoing(ticket),
                ticket.substring(3),
                "The AYK game ticket must never be written to the logs"
        );
        checkRedacted(
                LoginPacketRedactor.outgoing("AQ" + secretQuestion),
                secretQuestion,
                "The secret recovery question must never be written to the logs"
        );
        checkRedacted(
                LoginPacketRedactor.exchange("SK601;" + exchangeKey + ";10"),
                exchangeKey,
                "The game-server exchange key must never be written to the logs"
        );
        check("SK?".equals(LoginPacketRedactor.exchange("SK?")),
                "Non-secret exchange diagnostics must remain useful in logs");
        check("1.41.9".equals(LoginPacketRedactor.incoming("1.41.9", LoginClient.Status.WAIT_VERSION)),
                "Non-sensitive protocol data must remain useful in logs");
    }

    private static void checkRedacted(String logged, String secret, String message) {
        check(logged.contains("[REDACTED]"), message + " (missing marker)");
        check(!logged.contains(secret), message + " (secret still present)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
