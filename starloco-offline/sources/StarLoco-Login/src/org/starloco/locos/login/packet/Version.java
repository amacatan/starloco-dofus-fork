package org.starloco.locos.login.packet;

import org.starloco.locos.kernel.Config;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.login.LoginClient.Status;
import org.starloco.locos.login.LoginPacketRedactor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Version {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(e)?$");

    public final int major;
    public final int minor;
    public final int revision;
    public final boolean isElectron;

    private Version(int major, int minor, int revision, boolean isElectron) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.isElectron = isElectron;
    }

    boolean isSameRelease(Version other) {
        return other != null
                && this.major == other.major
                && this.minor == other.minor
                && this.revision == other.revision;
    }

    static Version fromString(String str) {
        if (str == null) {
            throw new IllegalArgumentException("Version is missing");
        }

        Matcher matcher = VERSION_PATTERN.matcher(str);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed version: " + str);
        }

        try {
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4) != null
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed version: " + str, e);
        }
    }

    static boolean isCompatible(String clientVersion, String serverVersion) {
        try {
            return fromString(clientVersion).isSameRelease(fromString(serverVersion));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static void verify(LoginClient client, String version) {
        if (!isCompatible(version, Config.version)) {
            System.out.println("[" + client.getIoSession().getId() + "] The version of the client '"
                    + LoginPacketRedactor.incoming(version, client.getStatus())
                    + "' is not like the server '" + Config.version + "'. The client going to be kicked.");
            client.send("AlEv" + Config.version);
            client.kick();
            return;
        }

        client.setStatus(Status.WAIT_ACCOUNT);
    }
}
