package org.starloco.locos.entity;

import org.starloco.locos.client.Player;

import java.util.Collection;

public final class CollectorChecks {

    private CollectorChecks() {
    }

    public static void run() {
        defenseCapacityReservesTheCollectorCell();
        defenseSnapshotsNeverExposeTheLiveMap();
    }

    private static void defenseCapacityReservesTheCollectorCell() {
        check(Collector.defenseCapacity(8) == 7,
                "A standard team must reserve one place for the collector");
        check(Collector.defenseCapacity(4) == 3,
                "Small maps must expose only maxTeam minus the collector");
        check(Collector.defenseCapacity(20) == 7,
                "Retro supports at most seven collector defenders");
        check(Collector.defenseCapacity(1) == 0,
                "A map with one team place cannot accept a defender");
        check(Collector.defenseCapacity(-1) == 0,
                "Invalid map capacities must not open defender slots");
    }

    private static void defenseSnapshotsNeverExposeTheLiveMap() {
        Collector collector = new Collector(1, 1, 1, (byte) 0,
                1, (short) 1, (short) 1, null, 0L, "", 0L, 0L);
        Collection<Player> snapshot = collector.getDefenseFightSnapshot();
        snapshot.add(null);
        check(collector.getDefenseFightSnapshot().isEmpty(),
                "Mutating a defense snapshot must not mutate the collector");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
