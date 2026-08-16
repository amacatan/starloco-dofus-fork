package org.starloco.locos.client;

import org.starloco.locos.area.map.GameCase;
import org.starloco.locos.area.map.GameMap;
import org.starloco.locos.area.map.MapData;
import org.starloco.locos.area.map.ScriptMapData;
import org.starloco.locos.fight.Fight;
import org.starloco.locos.game.GameClient;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import org.starloco.locos.game.world.World;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerJobActionChecks {
    private PlayerJobActionChecks() {
    }

    public static void run() throws Exception {
        GameMap map = testMap(5, 5);
        installTemplate(596);
        installTemplate(1860);
        GameObject shortRod = new GameObject(-996_001, 596, 1,
                Constant.ITEM_POS_ARME, "", 0);
        GameObject longRod = new GameObject(-996_002, 1860, 1,
                Constant.ITEM_POS_ARME, "", 0);

        check(Player.isObjectActionInRange(map, 21, 20, 24, null),
                "A non-fishing job action must be accepted from an adjacent cell");
        check(!Player.isObjectActionInRange(map, 23, 20, 24, null),
                "A non-fishing job action must be rejected beyond an adjacent cell");
        check(Player.isObjectActionInRange(map, 21, 20, 124, shortRod),
                "Fishing must use the equipped rod's exact maximum range");
        check(!Player.isObjectActionInRange(map, 23, 20, 124, shortRod),
                "A short rod must not fish beyond its configured range");
        check(Player.isObjectActionInRange(map, 23, 20, 124, longRod),
                "A long rod must retain its configured fishing range");
        check(!Player.isObjectActionInRange(map, 20, 20, 124, longRod),
                "Fishing must not target the player's own cell");
        check(!Player.isObjectActionInRange(map, 23, 20, 133, longRod),
                "Emptying fish is a workshop action and must remain adjacent");
        check(Player.isObjectActionInRange(map, 21, 20, 121, null),
                "Special workbenches must remain usable from an adjacent cell");

        check(Player.requiresAvailablePods(24)
                        && Player.requiresAvailablePods(102),
                "Job gathering and wells must enforce available pods");
        check(!Player.requiresAvailablePods(153)
                        && !Player.requiresAvailablePods(181),
                "Storage and crushers must stay available to overloaded players");
        check(Player.isFishingGatherSkill(124)
                        && Player.isFishingGatherSkill(140)
                        && !Player.isFishingGatherSkill(133),
                "Only actual fishing gathers may use rod range");

        rejectedActionsAreNeverRegistered(map);
    }

    private static void rejectedActionsAreNeverRegistered(GameMap map)
            throws Exception {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setObject(unsafe, player, Player.class, "curMap", map);
        setObject(unsafe, player, Player.class, "curCell", map.getCase(21));
        setObject(unsafe, player, Player.class, "fight",
                unsafe.allocateInstance(Fight.class));

        GameClient client = new GameClient(new IoSessionStub().session());
        setObject(unsafe, client, GameClient.class, "player", player);

        Method parseAction = GameClient.class.getDeclaredMethod(
                "parseAction", String.class);
        parseAction.setAccessible(true);
        parseAction.invoke(client, "GA50020;24");

        Field actionsField = GameClient.class.getDeclaredField("actions");
        actionsField.setAccessible(true);
        Map<?, ?> actions = (Map<?, ?>) actionsField.get(client);
        check(actions.isEmpty(),
                "A rejected map action must not leave an action 500 registered");
        check(player.getGameAction() == null,
                "A rejected map action must not become the player's current action");
    }

    private static GameMap testMap(int width, int height) throws Exception {
        Unsafe unsafe = unsafe();
        ScriptMapData data = (ScriptMapData) unsafe.allocateInstance(
                ScriptMapData.class);
        setInt(unsafe, data, MapData.class, "width", width);
        setInt(unsafe, data, MapData.class, "height", height);

        GameMap map = (GameMap) unsafe.allocateInstance(GameMap.class);
        setObject(unsafe, map, GameMap.class, "data", data);
        int cellCount = width * height + (width - 1) * (height - 1);
        List<GameCase> cases = new ArrayList<>(cellCount);
        for (int cellID = 0; cellID < cellCount; cellID++)
            cases.add(new GameCase(map, cellID));
        setObject(unsafe, map, GameMap.class, "cases", cases);
        return map;
    }

    private static void installTemplate(int id) {
        World.world.addObjTemplate(new ObjectTemplate(id, "", "Test rod " + id,
                20, 1, 1, 1, 0, "", "", 0, 0, 0, 0));
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setObject(Unsafe unsafe, Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static void setInt(Unsafe unsafe, Object target, Class<?> owner,
                               String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putInt(target, unsafe.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
