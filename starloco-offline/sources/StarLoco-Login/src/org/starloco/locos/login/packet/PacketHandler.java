package org.starloco.locos.login.packet;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Console;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.login.LoginPacketRedactor;

import java.util.Base64;

public class PacketHandler {

    public static void parser(LoginClient client, String packet) {
        if (client == null) {
            return;
        }
        if (packet == null) {
            rejectMalformed(client);
            return;
        }

        switch (client.getStatus()) {
            case WAIT_VERSION: // ok
                String[] parts = packet.split("\\|", -1);
                String version = parts[0];

                Console.instance.write("[" + client.getIoSession().getId() + "] Checking for version '"
                        + LoginPacketRedactor.incoming(version, client.getStatus()) + "'.");
                Version.verify(client, version);
                break;

            case WAIT_ACCOUNT: // a modifier
                if (packet.equals("#S")) {
                    // Character switch introduced in 1.39 and still used by Retro 1.41.9.
                    client.setStatus(LoginClient.Status.WAIT_GAMESERVER_JWS);
                    return;
                }
                if (packet.length() < 3) {
                    Console.instance.write("[" + client.getIoSession().getId() + "] Sending of packet '"
                            + LoginPacketRedactor.incoming(packet, client.getStatus())
                            + "' to verify the account. The client going to be kicked.");
                    client.send("AlEf");
                    client.kick();
                    return;
                }

                Console.instance.write("[" + client.getIoSession().getId() + "] Verification of account '"
                        + LoginPacketRedactor.incoming(packet, client.getStatus()) + "'.");
                AccountName.verify(client, packet);
                break;
            case WAIT_GAMESERVER_JWS:
                Console.instance.write("[" + client.getIoSession().getId()
                        + "] Verification of game-server token '"
                        + LoginPacketRedactor.incoming(packet, client.getStatus()) + "'.");
                try{
                    Claims result = Jwts.parserBuilder()
                        .requireIssuer("StarLocoGameServer")
                        .setAllowedClockSkewSeconds(5)
                        .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(Config.exchangeKey)))
                        .build().parseClaimsJws(packet).getBody();

                    String accID = result.getSubject();
                    String ip = result.get("ip", String.class);

                    AccountName.verifyJWS(client, accID, ip);
                    return;
                }catch(Exception e) {
                    client.send("AlEf");
                    client.kick();
                    return;
                }

            case WAIT_PASSWORD: // ok
                String encryptedPassword = packet.startsWith("#1") ? packet.substring(2) : packet;
                if (encryptedPassword.isEmpty()
                        || encryptedPassword.length() % 2 != 0
                        || encryptedPassword.length() > client.getKey().length() * 2
                        || !encryptedPassword.matches("[A-Za-z0-9_-]+")) {
                    Console.instance.write("[" + client.getIoSession().getId()
                            + "] Sending of packet '"
                            + LoginPacketRedactor.incoming(packet, client.getStatus())
                            + "' to verify the password. The client going to be kicked.");
                    client.send("AlEf");
                    client.kick();
                    return;
                }

                Console.instance.write("[" + client.getIoSession().getId()
                        + "] Verification of password '"
                        + LoginPacketRedactor.incoming(packet, client.getStatus()) + "'.");
                Password.verify(client, packet);
                break;

            case WAIT_NICKNAME: // ok
                Console.instance.write("[" + client.getIoSession().getId() + "] Verification of nickname '"
                        + LoginPacketRedactor.incoming(packet, client.getStatus()) + "'.");
                ChooseNickName.verify(client, packet);
                break;

            case SERVER:
                if (packet.length() < 2) {
                    rejectMalformed(client);
                    return;
                }
                switch (packet.substring(0, 2)) {
                    case "AF":
                        FriendServerList.get(client, packet.substring(2));
                        break;

                    case "Af": // ok
                        AccountQueue.verify(client);
                        break;

                    case "AX":
                        ServerSelected.get(client, packet.substring(2));
                        break;

                    case "Ax":
                        ServerList.get(client);
                        break;

                    case "BA":
                        BasicAdministration.execute(client, packet.substring(2));
                        break;

                    case "Ap":
                    case "Ai":
                        break;

                    default:
                        client.kick();
                        break;
                }
                break;
        }
    }

    private static void rejectMalformed(LoginClient client) {
        if (client.getStatus() == LoginClient.Status.WAIT_VERSION) {
            client.send("AlEv" + Config.version);
        } else {
            client.send("AlEf");
        }
        client.kick();
    }
}
