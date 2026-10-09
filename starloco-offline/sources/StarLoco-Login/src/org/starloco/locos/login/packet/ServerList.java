package org.starloco.locos.login.packet;

import org.starloco.locos.kernel.Console;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.object.Account;
import org.starloco.locos.object.Player;
import org.starloco.locos.object.Server;
import org.starloco.locos.tool.packetfilter.VPNAuthorization;

class ServerList {

    public static void get(LoginClient client) {
        Console.instance.write("[" + client.getIoSession().getId() + "] Check if he's running with a VPN");

        if(!VPNAuthorization.getInstance().isUnderVPN(client)) {
            client.send("AxK" + serverList(client.getAccount()));
            Console.instance.write("[" + client.getIoSession().getId() + "] wasn't under VPN");
        } else {
            client.send("M029");
            client.kick();
            Console.instance.write("[" + client.getIoSession().getId() + "] was under VPN");
        }
    }

    static String serverList(Account account) {
        StringBuilder sb = new StringBuilder(account.getSubscribeRemaining() + "");

        // Include all registered servers with their EXACT and HONEST character counts (0 if no characters exist).
        // This ensures the client shows (0) for empty servers and never displays phantom/fake characters.
        for (Server server : Server.servers.values()) {
            if (server == null) {
                continue;
            }
            int characterCount = characterNumber(account, server.getId());
            sb.append("|").append(server.getId()).append(",").append(characterCount);
        }

        Console.instance.write("[" + account.getClient().getIoSession().getId() + "] Sending list of server of account name " + account.getName() + ". List : '" + sb.toString() + "'");
        return sb.toString();
    }

    private static int characterNumber(Account account, int server) {
        int i = 0;

        for (Player character : account.getPlayers().values())
            if(character != null)
                if (character.getServer() == server)
                    i++;

        return i;
    }
}
