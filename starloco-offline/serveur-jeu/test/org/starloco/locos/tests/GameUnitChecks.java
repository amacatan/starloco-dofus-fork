package org.starloco.locos.tests;

import org.starloco.locos.database.data.game.DropDataChecks;
import org.starloco.locos.database.data.game.ExtraMonsterDataChecks;
import org.starloco.locos.database.data.login.AccountDataChecks;
import org.starloco.locos.game.GameClient;
import org.starloco.locos.game.GameHandler;
import org.starloco.locos.game.world.WorldExtraMonsterChecks;
import org.starloco.locos.kernel.Config;

public final class GameUnitChecks {
    private GameUnitChecks() {
    }

    public static void main(String[] args) throws Exception {
        emptyPacketsAreIgnored();
        gameTicketsAreRedacted();
        AccountDataChecks.run();
        DropDataChecks.run();
        ExtraMonsterDataChecks.run();
        WorldExtraMonsterChecks.run();
        System.out.println("Game Java checks: OK");
    }

    private static void emptyPacketsAreIgnored() throws Exception {
        Config.encryption = false;
        Config.debug = false;

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        stub.session().setAttribute("client", client);

        GameHandler handler = new GameHandler();
        handler.messageReceived(stub.session(), "");
        handler.messageReceived(stub.session(), "\n");
        handler.messageReceived(stub.session(), "ùwrappedù");
        handler.messageReceived(stub.session(), null);

        check(stub.writeCount() == 1, "Empty packets must not trigger a response or an exception");
    }

    private static void gameTicketsAreRedacted() {
        check("AT[REDACTED]".equals(GameClient.packetForLog("ATsecret-ticket")),
                "Game tickets must never be written to logs");
        check("AT[REDACTED]".equals(GameClient.packetForLog("atsecret-ticket")),
                "Ticket redaction must not depend on header casing");
        check("ALK0".equals(GameClient.packetForLog("ALK0")),
                "Ordinary packets must remain diagnosable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
