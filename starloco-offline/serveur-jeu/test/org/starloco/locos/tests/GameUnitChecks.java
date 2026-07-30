package org.starloco.locos.tests;

import org.starloco.locos.database.data.game.DropDataChecks;
import org.starloco.locos.database.data.game.ExtraMonsterDataChecks;
import org.starloco.locos.database.data.login.AccountDataChecks;
import org.starloco.locos.game.GameClient;
import org.starloco.locos.game.GameHandler;
import org.starloco.locos.game.world.WorldExtraMonsterChecks;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Logging;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Calendar;

public final class GameUnitChecks {
    private GameUnitChecks() {
    }

    public static void main(String[] args) throws Exception {
        Logging.USE_LOG = false;
        emptyPacketsAreIgnored();
        shortPacketsAreIgnored();
        quickPingIsAnswered();
        dateAndTimeUseTheProtocolClock();
        wrappedPacketsPreserveTheirPayload();
        remoteAddressesSupportIpv4AndIpv6();
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

    private static void shortPacketsAreIgnored() throws Exception {
        Config.encryption = false;
        Config.debug = false;

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());

        client.parsePacket("A");
        client.parsePacket("B");
        client.parsePacket("G");

        check(stub.writeCount() == 1,
                "Truncated packet families must not disconnect the client or trigger a response");
    }

    private static void quickPingIsAnswered() throws Exception {
        Config.encryption = false;
        Config.debug = false;

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        client.parsePacket("qping");

        check(stub.writeCount() == 2, "Quick ping must receive one response");
        check("q".equals(stub.writes().get(1)), "Quick ping must use the Retro quick-pong packet");
    }

    private static void dateAndTimeUseTheProtocolClock() throws Exception {
        Config.encryption = false;
        Config.debug = false;

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        long before = System.currentTimeMillis();
        client.parsePacket("BD");
        long after = System.currentTimeMillis();

        check(stub.writeCount() == 3, "Date request must return BD and BT");

        String datePacket = String.valueOf(stub.writes().get(1));
        String[] date = datePacket.substring(2).split("\\|");
        Calendar now = Calendar.getInstance();
        check(date.length == 3, "BD packet must contain year, month and day");
        check(Integer.parseInt(date[0]) == now.get(Calendar.YEAR),
                "BD packet must contain the current year");
        check(Integer.parseInt(date[1]) == now.get(Calendar.MONTH),
                "BD month is zero-based in the Retro protocol");
        check(Integer.parseInt(date[2]) == now.get(Calendar.DAY_OF_MONTH),
                "BD packet must contain the current day");

        String timePacket = String.valueOf(stub.writes().get(2));
        long serverTime = Long.parseLong(timePacket.substring(2));
        check(serverTime >= before && serverTime <= after,
                "BT must contain the current epoch time without a hard-coded timezone offset");
    }

    private static void wrappedPacketsPreserveTheirPayload() {
        String packet = "ùsignatureùBM*|où ça";
        check("BM*|où ça".equals(GameHandler.unwrapClientPacket(packet)),
                "The wrapper separator must not truncate an accented chat payload");
        check(GameHandler.unwrapClientPacket("ùincompleteù") == null,
                "An empty wrapped payload must be ignored");
        check("BD".equals(GameHandler.unwrapClientPacket("BD")),
                "An ordinary packet must remain unchanged");
    }

    private static void remoteAddressesSupportIpv4AndIpv6() throws Exception {
        IoSessionStub ipv4 = new IoSessionStub();
        check("127.0.0.1".equals(GameHandler.remoteIp(ipv4.session())),
                "IPv4 addresses must be extracted without the port");

        InetAddress loopbackV6 = InetAddress.getByName("::1");
        IoSessionStub ipv6 = new IoSessionStub(new InetSocketAddress(loopbackV6, 12345));
        check(loopbackV6.getHostAddress().equals(GameHandler.remoteIp(ipv6.session())),
                "IPv6 addresses must not be truncated at the first colon");
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
