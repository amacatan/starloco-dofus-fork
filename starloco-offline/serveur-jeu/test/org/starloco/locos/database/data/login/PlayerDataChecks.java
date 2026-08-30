package org.starloco.locos.database.data.login;

import org.starloco.locos.client.Player;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class PlayerDataChecks {
    private PlayerDataChecks() {
    }

    public static void run() throws Exception {
        reloadUsesTheOwningAccountId();
        equalPlayerAndAccountIdsRemainSupported();
    }

    private static void reloadUsesTheOwningAccountId() throws Exception {
        Player player = player(1_001, 42);

        check(PlayerData.questProgressAccountId(player) == 42,
                "A player reload must fetch quest progress by account id");
        check(PlayerData.questProgressAccountId(player) != player.getId(),
                "A distinct character id must never be reused as the quest account id");
    }

    private static void equalPlayerAndAccountIdsRemainSupported() throws Exception {
        Player player = player(77, 77);

        check(PlayerData.questProgressAccountId(player) == 77,
                "Reloading must still work when character and account ids happen to match");
    }

    private static Player player(int playerId, int accountId)
            throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(unsafe, player, "id", playerId);
        setInt(unsafe, player, "_accID", accountId);
        return player;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setInt(Unsafe unsafe, Player player,
                               String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = Player.class.getDeclaredField(fieldName);
        unsafe.putInt(player, unsafe.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
