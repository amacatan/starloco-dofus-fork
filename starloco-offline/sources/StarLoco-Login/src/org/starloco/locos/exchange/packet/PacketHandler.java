package org.starloco.locos.exchange.packet;

import org.starloco.locos.exchange.ExchangeClient;
import org.starloco.locos.object.Server;

public class PacketHandler {

    public static void parser(ExchangeClient client, String packet) {
        if (client == null) {
            return;
        }
        if (packet == null || packet.isEmpty()) {
            client.kick();
            return;
        }

        try {
            switch (packet.charAt(0)) {
                case 'F': // Free places
                    requireServer(client);
                    if (packet.length() < 2) {
                        throw new IllegalArgumentException("Missing free-place count");
                    }
                    int freePlaces = Integer.parseInt(packet.substring(1));
                    client.getServer().setFreePlaces(freePlaces);
                    break;

                case 'S': // Server
                    if (packet.length() < 2) {
                        throw new IllegalArgumentException("Missing server command");
                    }
                    switch (packet.charAt(1)) {
                        case 'H': // Host
                            requireServer(client);
                            Server server = client.getServer();
                            String[] s = packet.substring(2).split(";", -1);
                            if (s.length != 2 || s[0].isEmpty()) {
                                throw new IllegalArgumentException("Malformed host packet");
                            }
                            int port = Integer.parseInt(s[1]);
                            if (port < 1 || port > 65535) {
                                throw new IllegalArgumentException("Invalid game port");
                            }
                            server.setIp(s[0]);
                            server.setPort(port);
                            client.send("SHK");
                            break;

                        case 'K': // Key
                            s = packet.substring(2).split(";", -1);
                            if (s.length != 3) {
                                throw new IllegalArgumentException("Malformed key packet");
                            }
                            int id = Integer.parseInt(s[0]);
                            String key = s[1];
                            freePlaces = Integer.parseInt(s[2]);

                            server = Server.get(id);

                            if (server == null || !server.getKey().equals(key)) {
                                client.send("SKR");
                                client.kick();
                                return;
                            }

                            ExchangeClient previousClient = server.getClient();
                            server.setClient(client);
                            client.setServer(server);
                            if (previousClient != null && previousClient != client) {
                                previousClient.kick();
                            }
                            server.setFreePlaces(freePlaces);
                            client.send("SKK");
                            break;

                        case 'S': // Statut
                            requireServer(client);
                            if (packet.length() < 3) {
                                throw new IllegalArgumentException("Missing server state");
                            }
                            client.getServer().setState(Integer.parseInt(packet.substring(2)));
                            break;

                        default:
                            throw new IllegalArgumentException("Unknown server command");
                    }
                    break;

                case 'D' : // Data
                    requireServer(client);
                    if (packet.length() < 2) {
                        throw new IllegalArgumentException("Missing data command");
                    }
                    if (packet.charAt(1) == 'M') {
                        Server.servers.values().stream()
                                .filter(server -> server != null && server.getClient() != null && server.getId() != client.getServer().getId())
                                .forEach(server -> server.send(packet + ""));
                    }
                    break;

                default:
                    System.err.println("Packet undefined \"" + packet + "\"");
                    break;
            }
        } catch (Exception e) {
            System.err.println("Rejected malformed exchange packet: " + e.getMessage());
            client.kick();
        }
    }

    private static void requireServer(ExchangeClient client) {
        if (client.getServer() == null || client.getServer().getClient() != client) {
            throw new IllegalStateException("Game server is not authenticated");
        }
    }
}
