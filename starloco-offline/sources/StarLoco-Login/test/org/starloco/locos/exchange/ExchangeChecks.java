package org.starloco.locos.exchange;

import org.starloco.locos.object.Server;
import org.starloco.locos.tests.IoSessionStub;

public final class ExchangeChecks {
    private ExchangeChecks() {
    }

    public static void run() {
        IoSessionStub unauthenticatedSession = new IoSessionStub();
        ExchangeHandler handler = new ExchangeHandler();
        handler.sessionCreated(unauthenticatedSession.session());
        handler.sessionClosed(unauthenticatedSession.session());

        Server.servers.clear();
        Server server = new Server(601, "secret", 0);

        IoSessionStub refusedSession = new IoSessionStub();
        ExchangeClient refused = new ExchangeClient(refusedSession.session());
        org.starloco.locos.exchange.packet.PacketHandler.parser(refused, "SK601;wrong;10");
        check(refusedSession.closeCount() == 1, "A bad exchange key must close the session");
        check("SKR".equals(refusedSession.lastPacket()), "A bad exchange key must be refused");
        check(server.getClient() == null, "A refused client must never be attached to the server");

        IoSessionStub acceptedSession = new IoSessionStub();
        ExchangeClient accepted = new ExchangeClient(acceptedSession.session());
        org.starloco.locos.exchange.packet.PacketHandler.parser(accepted, "SK601;secret;10");
        check(acceptedSession.closeCount() == 0, "A valid exchange key must keep the session open");
        check("SKK".equals(acceptedSession.lastPacket()), "A valid exchange key must be acknowledged");
        check(server.getClient() == accepted && accepted.getServer() == server,
                "A valid exchange client must be attached in both directions");

        IoSessionStub replacementSession = new IoSessionStub();
        ExchangeClient replacement = new ExchangeClient(replacementSession.session());
        org.starloco.locos.exchange.packet.PacketHandler.parser(replacement, "SK601;secret;9");
        check(acceptedSession.closeCount() == 1, "Replacing a game server must close its previous session");
        check(server.getClient() == replacement, "The replacement must become the only active exchange client");

        server.setState(1);
        org.starloco.locos.exchange.packet.PacketHandler.parser(accepted, "SS0");
        check(server.getState() == 1, "A stale exchange session must not change the server state");

        IoSessionStub malformedSession = new IoSessionStub();
        org.starloco.locos.exchange.packet.PacketHandler.parser(
                new ExchangeClient(malformedSession.session()), "S"
        );
        check(malformedSession.closeCount() == 1, "A short exchange packet must be closed safely");

        Server.servers.clear();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
