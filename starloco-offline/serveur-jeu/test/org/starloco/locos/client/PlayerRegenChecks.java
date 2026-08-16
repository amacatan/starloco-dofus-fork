package org.starloco.locos.client;

import org.starloco.locos.fight.Fight;
import org.starloco.locos.game.GameClient;
import org.starloco.locos.game.world.World;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class PlayerRegenChecks {
    private static final int TEST_ACCOUNT_ID = -996_100;

    private PlayerRegenChecks() {
    }

    public static void run() throws Exception {
        fightingDisablesRegenAndLeavingRestoresNormalRate();
        resettingConnectionStateRestoresStandingRegen();
    }

    private static void resettingConnectionStateRestoresStandingRegen()
            throws Exception {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setObject(unsafe, player, Player.class, "fight",
                unsafe.allocateInstance(Fight.class));
        setObject(unsafe, player, Player.class, "follower", new HashMap<>());
        setBoolean(unsafe, player, Player.class, "sitted", true);
        setBoolean(unsafe, player, Player.class, "isGhost", true);
        setInt(unsafe, player, Player.class, "regenRate", 1000);
        setLong(unsafe, player, Player.class, "regenTime", 1L);

        long beforeReset = System.currentTimeMillis();
        player.resetVars();
        long afterReset = System.currentTimeMillis();

        check(player.getFight() == null && !player.isSitted() && !player.isGhost(),
                "Resetting a connection must restore the ordinary out-of-fight state");
        check(getInt(player, "regenRate") == 2000,
                "Resetting a seated connection must restore normal server-side regeneration");
        check(isBetween(getLong(player, "regenTime"), beforeReset, afterReset),
                "Resetting a connection must start a new regeneration interval");
    }

    private static void fightingDisablesRegenAndLeavingRestoresNormalRate()
            throws Exception {
        Unsafe unsafe = unsafe();
        IoSessionStub session = new IoSessionStub();
        GameClient client = new GameClient(session.session());
        Account account = (Account) unsafe.allocateInstance(Account.class);
        setInt(unsafe, account, Account.class, "id", TEST_ACCOUNT_ID);
        account.setGameClient(client);

        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(unsafe, player, Player.class, "_accID", TEST_ACCOUNT_ID);
        setInt(unsafe, player, Player.class, "regenRate", 2000);
        setLong(unsafe, player, Player.class, "regenTime", 1L);

        Map<Integer, Account> accounts = accounts();
        Account previous = accounts.put(TEST_ACCOUNT_ID, account);
        try {
            Fight fight = (Fight) unsafe.allocateInstance(Fight.class);
            int packetsBeforeFight = session.writeCount();
            long beforeFight = System.currentTimeMillis();
            player.setFight(fight);
            long afterFight = System.currentTimeMillis();

            check(player.getFight() == fight,
                    "Entering a fight must retain the fight reference");
            check(getInt(player, "regenRate") == 0,
                    "Regeneration must be disabled while fighting");
            check(isBetween(getLong(player, "regenTime"), beforeFight, afterFight),
                    "Entering a fight must reset the regeneration timestamp");
            check("ILF0".equals(session.writes().get(packetsBeforeFight)),
                    "Entering a fight must stop client-side regeneration");

            setLong(unsafe, player, Player.class, "regenTime", 1L);
            long beforeLeave = System.currentTimeMillis();
            player.setFight(null);
            long afterLeave = System.currentTimeMillis();

            check(player.getFight() == null,
                    "Leaving a fight must clear the fight reference");
            check(getInt(player, "regenRate") == 2000,
                    "Standing regeneration after a fight must take two seconds per life point");
            check(isBetween(getLong(player, "regenTime"), beforeLeave, afterLeave),
                    "Leaving a fight must not count the fight duration as regeneration time");
            check("ILS2000".equals(session.writes().get(packetsBeforeFight + 1)),
                    "Leaving a fight must advertise the normal regeneration rate");
        } finally {
            if (previous == null)
                accounts.remove(TEST_ACCOUNT_ID);
            else
                accounts.put(TEST_ACCOUNT_ID, previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Account> accounts() throws ReflectiveOperationException {
        Field field = World.class.getDeclaredField("accounts");
        field.setAccessible(true);
        return (Map<Integer, Account>) field.get(World.world);
    }

    private static boolean isBetween(long value, long minimum, long maximum) {
        return value >= minimum && value <= maximum;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setInt(Unsafe unsafe, Object target, Class<?> owner,
                               String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putInt(target, unsafe.objectFieldOffset(field), value);
    }

    private static void setLong(Unsafe unsafe, Object target, Class<?> owner,
                                String fieldName, long value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putLong(target, unsafe.objectFieldOffset(field), value);
    }

    private static void setBoolean(Unsafe unsafe, Object target, Class<?> owner,
                                   String fieldName, boolean value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putBoolean(target, unsafe.objectFieldOffset(field), value);
    }

    private static void setObject(Unsafe unsafe, Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static int getInt(Player player, String fieldName)
            throws ReflectiveOperationException {
        Field field = Player.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(player);
    }

    private static long getLong(Player player, String fieldName)
            throws ReflectiveOperationException {
        Field field = Player.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
