package org.starloco.locos.tool.packetfilter;

import java.util.HashMap;
import java.util.Map;

public class PacketFilter {

    private final int maxConnections;
    private final int restrictedTime;
    private final Map<String, IpInstance> ipInstances = new HashMap<>();
    private boolean safe;

    public PacketFilter() {
        this.maxConnections = 8;
        this.restrictedTime = 1000;
    }

    synchronized boolean safeCheck(String ip) {
        return unSafeCheck(ip);
    }

    boolean unSafeCheck(String ip) {
        IpInstance ipInstance = find(ip);

        if (ipInstance.isBanned()) {
            return false;
        } else {
            ipInstance.addConnection();

            if (ipInstance.getLastConnection() + this.restrictedTime >= System.currentTimeMillis()) {
                if (ipInstance.getConnections() < this.maxConnections)
                    return true;
                else {
                    ipInstance.ban();
                    return false;
                }
            } else {
                ipInstance.updateLastConnection();
                ipInstance.resetConnections();
            }
            return true;
        }
    }

    public boolean authorizes(String ip) {
        return safe ? safeCheck(ip) : unSafeCheck(ip);
    }

    public PacketFilter activeSafeMode() {
        this.safe = true;
        return this;
    }

    private IpInstance find(String ip) {
        ip = clearIp(ip);

        IpInstance result = ipInstances.get(ip);
        if (result != null)
            return result;

        result = new IpInstance();
        ipInstances.put(ip, result);
        return result;
    }

    private String clearIp(String ip) {
        if (ip == null) {
            return "";
        }
        if (ip.startsWith("[")) {
            int bracket = ip.indexOf(']');
            return bracket > 1 ? ip.substring(1, bracket) : ip;
        }

        int firstColon = ip.indexOf(':');
        int lastColon = ip.lastIndexOf(':');
        return firstColon > 0 && firstColon == lastColon ? ip.substring(0, firstColon) : ip;
    }
}
