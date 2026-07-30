package org.starloco.locos.game;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.api.AbstractDofusMessage;
import org.starloco.locos.factory.DofusMessageFactory;
import org.starloco.locos.factory.EventDispatcherFactory;
import org.starloco.locos.game.filter.PacketFilter;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Config;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class GameHandler implements IoHandler {
    private final static Logger logger = LoggerFactory.getLogger(GameHandler.class);
    private final static PacketFilter filter = new PacketFilter().activeSafeMode();

    @Override
    public void sessionCreated(IoSession arg0) {
        if (!filter.authorizes(remoteIp(arg0))) {
            arg0.close(true);
        } else {
            World.world.logger.info("Session " + arg0.getId() + " created");
            arg0.setAttribute("client", new GameClient(arg0));
        }
    }

    @Override
    public void messageReceived(IoSession arg0, Object arg1) throws Exception {
        GameClient client = (GameClient) arg0.getAttribute("client");
        if (client == null || !(arg1 instanceof String)) {
            return;
        }

        String packet = (String) arg1;
        if (packet == null || packet.isEmpty()) {
            return;
        }

        if (Config.encryption && !packet.startsWith("AT") && !packet.startsWith("Ak")) {
            packet = World.world.getCryptManager().decryptMessage(packet, client.getPreparedKeys());
            if (packet != null) {
                if (!isGuildTextPacket(unwrapClientPacket(packet))) {
                    packet = packet.replace("\n", "");
                }
            } else {
                packet = (String) arg1;
            }
        }

        // Notes and guild information are multiline text fields in Retro. Keep
        // their frame intact so that an embedded line cannot be interpreted as
        // a second game packet.
        String unwrappedPacket = unwrapClientPacket(packet);
        String[] s = isGuildTextPacket(unwrappedPacket)
                ? new String[]{packet}
                : packet.split("\n");

        for(String p : s){
            if (p.isEmpty()) {
                continue;
            }
            p = unwrapClientPacket(p);
            if (p == null) {
                continue;
            }
            try {
                if(p.length() > 1) {
                    AbstractDofusMessage abstractDofusMessage = DofusMessageFactory.getMessage(p.substring(0, 2));
                    if (abstractDofusMessage != null) {
                        abstractDofusMessage.setInput(new StringBuilder(p.substring(2)));
                        abstractDofusMessage.deserialize();
                        abstractDofusMessage.setClient(client);
                        logger.info("Receive message: {} with header: {}", abstractDofusMessage.getClass().getName(), p.substring(0, 2));
                        EventDispatcherFactory.dispatch(abstractDofusMessage);
                        continue;
                    }
                }
                client.parsePacket(p);
            } catch(Exception e) {
                throw new Exception("Cannot process packet: " + GameClient.packetForLog(p), e);
            } finally {
                if (Config.debug) {
                    World.world.logger.trace((client.getPlayer() == null ? "" : client.getPlayer().getName())
                            + " <-- " + GameClient.packetForLog(p));
                }
            }
        }
    }

    public static String remoteIp(IoSession session) {
        SocketAddress remoteAddress = session == null ? null : session.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress inetAddress = (InetSocketAddress) remoteAddress;
            if (inetAddress.getAddress() != null) {
                return inetAddress.getAddress().getHostAddress();
            }
            return inetAddress.getHostString();
        }
        return remoteAddress == null ? "" : remoteAddress.toString();
    }

    public static String unwrapClientPacket(String packet) {
        if (packet == null || packet.isEmpty()) {
            return null;
        }
        if (packet.charAt(0) != 'ù') {
            return packet;
        }

        String[] wrappedPacket = packet.split("ù", 3);
        if (wrappedPacket.length < 3 || wrappedPacket[2].isEmpty()) {
            return null;
        }
        return wrappedPacket[2];
    }

    public static boolean isGuildTextPacket(String packet) {
        return packet != null && (packet.startsWith("gEN") || packet.startsWith("gEI"));
    }

    @Override
    public void sessionClosed(IoSession arg0) {
        this.kick(arg0);
        World.world.logger.info("Session " + arg0.getId() + " closed");
    }

    @Override
    public void exceptionCaught(IoSession arg0, Throwable arg1) {
        if(arg1 == null) return;
        if(arg1.getMessage() != null && (arg1 instanceof org.apache.mina.filter.codec.RecoverableProtocolDecoderException || arg1.getMessage().startsWith("Une connexion ") ||
                arg1.getMessage().startsWith("Connection reset by peer") || arg1.getMessage().startsWith("Connection timed out")))
            return;
        arg1.printStackTrace();
        if (Config.debug)
            World.world.logger.error("Exception connexion client : ", arg1);
        this.kick(arg0);
    }

    @Override
    public void messageSent(IoSession arg0, Object arg1) {
        GameClient client = (GameClient) arg0.getAttribute("client");

        if (client != null) {
            if (Config.debug) {
                String packet = (String) arg1;
                if (Config.encryption && !packet.startsWith("AT") && !packet.startsWith("HG"))
                    packet = World.world.getCryptManager().decryptMessage(packet, client.getPreparedKeys()).replace("\n", "");
                if (packet.startsWith("am")) return;
                World.world.logger.trace((client.getPlayer() == null ? "" : client.getPlayer().getName()) + " --> " + packet);
            }
        }
    }

    @Override
    public void inputClosed(IoSession ioSession) {
        ioSession.close(true);
    }

    @Override
    public void sessionIdle(IoSession arg0, IdleStatus arg1) {
        World.world.logger.info("Session " + arg0.getId() + " idle");
}

    @Override
    public void sessionOpened(IoSession arg0) {
        World.world.logger.info("Session " + arg0.getId() + " opened");
    }

    void kick(IoSession arg0) {
        GameClient client = (GameClient) arg0.getAttribute("client");
        if (client != null) {
            client.disconnect();
            client.kick();
            arg0.setAttribute("client", null);
        }
    }
}
