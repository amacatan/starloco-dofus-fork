package org.starloco.locos.login.packet;

import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Main;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.object.Account;

import java.util.Locale;

class AccountName {

    static void verify(LoginClient client, String name) {
        if (!isValid(name) || !AccountName.loadAndSetAccount(client, name)) {
            client.send("AlEf");
            client.kick();
            return;
        }
        client.setStatus(LoginClient.Status.WAIT_PASSWORD);
    }

    static boolean isValid(String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,29}");
    }

    static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean loadAndSetAccount(LoginClient client, String name) {
        Account account = Main.database.getAccountData().load(normalize(name));

        if(account == null) return false;

        client.setAccount(account);
        account.setClient(client);
        return true;
    }

    static void verifyJWS(LoginClient client,String name, String ip) {
        String currentIP = client.getIoSession().getRemoteAddress().toString().substring(1).split(":")[0];
        if (!isValid(name) || !ip.equals(currentIP) || !AccountName.loadAndSetAccount(client, name)) {
            client.send("AlEf");
            client.kick();
            return;
        }
        // A valid, IP-bound game-server JWS is the authenticated alternative
        // to the legacy password flow used when switching characters.
        Config.loginServer.registerAuthenticated(client);
        client.setStatus(LoginClient.Status.SERVER);

    }
}
