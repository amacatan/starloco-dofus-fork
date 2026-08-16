package org.starloco.locos.fight;

import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Stalk;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.OptionalLong;

public final class FighterChecks {

    private FighterChecks() {
    }

    public static void run() throws ReflectiveOperationException {
        playerFightersKeepTheirCurrentLife();
        aggressionTrackingChecksHandleMissingContracts();
        teamOneJoinUsesItsOwnPlacementCellsAndRemainingTime();
    }

    private static void teamOneJoinUsesItsOwnPlacementCellsAndRemainingTime() {
        long remainingTime = 12_345L;
        OptionalLong teamOneCountdown = Fight.getJoinCountdown(
                1,
                2, 3,
                1, 2,
                remainingTime);

        check(teamOneCountdown.isPresent(),
                "Team one must compare its placement cells with its own fighter count");
        check(teamOneCountdown.getAsLong() == remainingTime,
                "Team one must receive the fight's remaining placement countdown");

        check(!Fight.getJoinCountdown(1, 1, 3, 2, 2, remainingTime).isPresent(),
                "Team one must reject a join when all of its placement cells are occupied");
        check(!Fight.getJoinCountdown(1, 1, 3, 3, 2, remainingTime).isPresent(),
                "Team one must reject a join when fighters outnumber its placement cells");
    }

    private static void aggressionTrackingChecksHandleMissingContracts()
            throws ReflectiveOperationException {
        Player tracker = playerWithId(10);
        Player target = playerWithId(20);

        check(!Fight.hasStalkTarget(null, target),
                "A missing opposing player must not count as a tracking contract");
        check(!Fight.hasStalkTarget(tracker, target),
                "An opponent without a tracking contract must be ignored");

        tracker.setStalk(new Stalk(0, null));
        check(!Fight.hasStalkTarget(tracker, target),
                "A tracking contract without a target must be ignored");

        tracker.setStalk(new Stalk(0, target));
        check(Fight.hasStalkTarget(tracker, target),
                "A matching tracking contract must still be detected");
    }

    private static void playerFightersKeepTheirCurrentLife() throws ReflectiveOperationException {
        Player player = playerWithLife(37, 100);

        Fighter fighter = Fighter.NewPlayer(null, player);

        check(fighter.getPdv() == 37,
                "A player must enter a fight with their current life, not full life");
        check(fighter.getPdvMax() == 100,
                "Keeping current life must not change the fighter's maximum life");
    }

    private static Player playerWithLife(int current, int maximum)
            throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(player, "curPdv", current);
        setInt(player, "maxPdv", maximum);
        setInt(player, "regenRate", 0);
        return player;
    }

    private static Player playerWithId(int id) throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(player, "id", id);
        return player;
    }

    private static void setInt(Player player, String name, int value)
            throws ReflectiveOperationException {
        Field field = Player.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(player, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
