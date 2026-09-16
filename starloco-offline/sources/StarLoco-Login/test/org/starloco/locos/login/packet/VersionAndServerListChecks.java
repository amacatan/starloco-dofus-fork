package org.starloco.locos.login.packet;

import org.starloco.locos.exchange.ExchangeClient;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.login.LoginServer;
import org.starloco.locos.object.Account;
import org.starloco.locos.object.Player;
import org.starloco.locos.object.Server;
import org.starloco.locos.tests.IoSessionStub;

import java.util.Locale;

public final class VersionAndServerListChecks {
    private VersionAndServerListChecks() {
    }

    public static void run() {
        check(AccountName.isValid("abc"), "A three-character account name must be accepted");
        check(AccountName.isValid("Retro_1419.test-account"),
                "Underscore, dot and hyphen must be accepted after the first character");
        check(AccountName.isValid("a23456789012345678901234567890"),
                "A 30-character account name must be accepted");
        check(!AccountName.isValid("_retro"), "An account name must start with a letter or digit");
        check(!AccountName.isValid("ab"), "An account name shorter than three characters must be rejected");
        check(!AccountName.isValid("a234567890123456789012345678901"),
                "An account name longer than 30 characters must be rejected");
        check(!AccountName.isValid("retro@1419"), "Characters outside the portal contract must be rejected");
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check("retro_i_test".equals(AccountName.normalize("RETRO_I_TEST")),
                    "ASCII account normalization must not depend on the host locale");
        } finally {
            Locale.setDefault(originalLocale);
        }

        check(Version.isCompatible("1.41.9", "1.41.9"), "The exact Retro release must be accepted");
        check(Version.isCompatible("1.41.9e", "1.41.9"), "The Electron suffix must be optional");
        check(Version.isCompatible("1.41.9", "1.41.9e"), "The configured Electron suffix must be optional");
        check(!Version.isCompatible("1.41.10", "1.41.9"), "A newer, unverified protocol must be rejected");
        check(!Version.isCompatible("1.41", "1.41.9"), "A truncated version must be rejected");
        check(!Version.isCompatible("not-a-version", "1.41.9"), "A malformed version must be rejected");
        check(!Version.isCompatible(" 1.41.9e", "1.41.9"),
                "Whitespace must not weaken the exact release contract");

        String salt = "abcdefghijklmnopqrstuvwxyzabcdef";
        check(Password.decryptPassword("#1aa", salt) != null,
                "A structurally valid legacy password packet must be decoded");
        check(Password.decryptPassword("#1a", salt) == null,
                "An odd legacy password payload must be rejected");
        check(Password.decryptPassword("#1a!", salt) == null,
                "Characters outside the legacy alphabet must be rejected");
        check(Password.decryptPassword("#1" + repeat('a', 66), salt) == null,
                "A password longer than the 32-character salt must be rejected");
        check(!Password.isValidPass(null, Password.encrypt("secret")),
                "A rejected legacy payload must fail authentication without an exception");
        wrongPasswordCannotEvictAuthenticatedSession();

        Server.servers.clear();
        Config.loginServer = null;

        Server online = new Server(601, "eratz", 0);
        online.setClient(new ExchangeClient(new IoSessionStub().session()));
        online.setState(1);

        Server offline = new Server(602, "offline", 0);
        offline.setClient(new ExchangeClient(new IoSessionStub().session()));
        offline.setState(0);

        new Server(603, "disconnected", 0);

        check("AH601;1;110;1|602;0;110;0|603;0;110;0".equals(Server.getHostList()),
                "AH must expose all servers with their real availability status");
        String publicServerDescription = BasicAdministration.publicServerDescription(online);
        check("- id:601".equals(publicServerDescription),
                "Administration diagnostics must identify a server without exposing its shared key");
        check(!publicServerDescription.contains(online.getKey()),
                "The game-server shared key must never be returned to an administrator");

        IoSessionStub accountSession = new IoSessionStub();
        Account account = new Account(42, "new-account", "hash", "NewAccount", "", (byte) 0, 0, (byte) 0, 0);
        account.setClient(new org.starloco.locos.login.LoginClient(accountSession.session(), "abcdefghijklmnopqrstuvwxyzabcdef"));

        check("0|601,1".equals(ServerList.serverList(account)),
                "Retro 1.41.9 requires an advertised count of one to select a world with an empty account");

        account.addPlayer(new Player(10, 601, 0));
        account.addPlayer(new Player(11, 601, 0));
        check("0|601,2".equals(ServerList.serverList(account)),
                "The real character count must be kept once it is positive");

        Server.servers.clear();
    }

    private static void wrongPasswordCannotEvictAuthenticatedSession() {
        LoginServer loginServer = new LoginServer();
        Config.loginServer = loginServer;

        IoSessionStub authenticatedSession = new IoSessionStub();
        LoginClient authenticated = new LoginClient(
                authenticatedSession.session(),
                "abcdefghijklmnopqrstuvwxyzabcdef"
        );
        Account authenticatedAccount = new Account(
                42, "victim_user", Password.encrypt("Correct123"), "Victim",
                "", (byte) 0, 0, (byte) 0, 0
        );
        authenticated.setAccount(authenticatedAccount);
        authenticatedAccount.setClient(authenticated);
        loginServer.registerAuthenticated(authenticated);

        IoSessionStub attackerSession = new IoSessionStub();
        LoginClient attacker = new LoginClient(
                attackerSession.session(),
                "abcdefghijklmnopqrstuvwxyzabcdef"
        );
        Account attemptedAccount = new Account(
                42, "victim_user", Password.encrypt("Correct123"), "Victim",
                "", (byte) 0, 0, (byte) 0, 0
        );
        attacker.setAccount(attemptedAccount);
        attemptedAccount.setClient(attacker);
        attacker.setStatus(LoginClient.Status.WAIT_PASSWORD);

        PacketHandler.parser(attacker, "#1aa");

        check(attackerSession.closeCount() == 1,
                "A wrong password must close the unauthenticated attempt");
        check(authenticatedSession.closeCount() == 0,
                "A wrong password must not close the authenticated session");
        check(loginServer.getAuthenticatedClient("victim_user") == authenticated,
                "A wrong password must not replace the authenticated session registry entry");

        authenticated.kick();
        Config.loginServer = null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(character);
        }
        return result.toString();
    }
}
