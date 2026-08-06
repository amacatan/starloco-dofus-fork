package org.starloco.locos.fight;

import org.starloco.locos.client.Player;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class FighterChecks {

    private FighterChecks() {
    }

    public static void run() throws ReflectiveOperationException {
        playerFightersKeepTheirCurrentLife();
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
