package org.starloco.locos.tests;

import org.starloco.locos.exchange.ExchangeChecks;
import org.starloco.locos.database.data.AccountDataChecks;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Console;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.login.LoginHandler;
import org.starloco.locos.login.LoginLoggingChecks;
import org.starloco.locos.login.packet.VersionAndServerListChecks;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class LoginUnitChecks {
    private LoginUnitChecks() {
    }

    public static void main(String[] args) {
        new File("logs").mkdirs();
        Console.instance = new Console();
        loginClientAndMalformedPackets();
        remoteAddressesSupportIpv4AndIpv6();
        AccountDataChecks.run();
        LoginLoggingChecks.run();
        VersionAndServerListChecks.run();
        ExchangeChecks.run();
        System.out.println("Login Java checks: OK");
    }

    private static void loginClientAndMalformedPackets() {
        Config.loginServer = null;
        Config.version = "1.41.9e";

        IoSessionStub directKickSession = new IoSessionStub();
        new LoginClient(directKickSession.session(), "abcdefghijklmnopqrstuvwxyzabcdef").kick();
        check(directKickSession.closeCount() == 1, "A pre-authentication kick must be null-safe");

        IoSessionStub malformedVersionSession = new IoSessionStub();
        LoginClient malformedVersion = new LoginClient(
                malformedVersionSession.session(),
                "abcdefghijklmnopqrstuvwxyzabcdef"
        );
        org.starloco.locos.login.packet.PacketHandler.parser(malformedVersion, "bad");
        check(malformedVersionSession.closeCount() == 1, "A malformed version must close cleanly");
        check("AlEv1.41.9e".equals(malformedVersionSession.lastPacket()),
                "A malformed version must advertise the configured release");

        IoSessionStub shortServerPacketSession = new IoSessionStub();
        LoginClient shortServerPacket = new LoginClient(
                shortServerPacketSession.session(),
                "abcdefghijklmnopqrstuvwxyzabcdef"
        );
        shortServerPacket.setStatus(LoginClient.Status.SERVER);
        org.starloco.locos.login.packet.PacketHandler.parser(shortServerPacket, "A");
        check(shortServerPacketSession.closeCount() == 1, "A short authenticated packet must close cleanly");
        check("AlEf".equals(shortServerPacketSession.lastPacket()),
                "A short authenticated packet must return a generic login error");
    }

    private static void remoteAddressesSupportIpv4AndIpv6() {
        IoSessionStub ipv4 = new IoSessionStub();
        check("127.0.0.1".equals(LoginHandler.remoteIp(ipv4.session())),
                "Login must extract an IPv4 address without its port");

        try {
            InetAddress loopbackV6 = InetAddress.getByName("::1");
            IoSessionStub ipv6 = new IoSessionStub(new InetSocketAddress(loopbackV6, 12345));
            check(loopbackV6.getHostAddress().equals(LoginHandler.remoteIp(ipv6.session())),
                    "Login must not truncate an IPv6 address at the first colon");
        } catch (Exception error) {
            throw new AssertionError("IPv6 loopback must be available to the unit test", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
