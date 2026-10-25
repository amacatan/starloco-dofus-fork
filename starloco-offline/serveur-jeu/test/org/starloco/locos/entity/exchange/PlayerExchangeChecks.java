package org.starloco.locos.entity.exchange;

import org.starloco.locos.client.Player;
import org.starloco.locos.game.world.World;
import org.starloco.locos.game.world.World.Couple;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class PlayerExchangeChecks {
    private PlayerExchangeChecks() {
    }

    public static void run() throws Exception {
        exchangePlayer1ToPlayer2_stacksSimilarItems();
        exchangePlayer2ToPlayer1_stacksSimilarItems();
    }

    private static void exchangePlayer1ToPlayer2_stacksSimilarItems() throws Exception {
        int templateId = 998_001;
        ObjectTemplate template = new ObjectTemplate(templateId, "", "Test Fer",
                Constant.ITEM_TYPE_RESSOURCE, 1, 1, 0, 0,
                "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);

        GameObject itemP1 = new GameObject(998_002, templateId, 5,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        GameObject itemP2 = new GameObject(998_003, templateId, 10,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        World.world.addGameObject(itemP1);
        World.world.addGameObject(itemP2);

        Player p1 = playerWithInventory(998_010, itemP1);
        Player p2 = playerWithInventory(998_011, itemP2);

        PlayerExchange exchange = new PlayerExchange(p1, p2);

        Method transferMethod = PlayerExchange.class.getDeclaredMethod(
                "transferExchangeItem", Player.class, Player.class, Couple.class);
        transferMethod.setAccessible(true);
        transferMethod.invoke(exchange, p1, p2, new Couple<>(itemP1.getGuid(), 5));

        check(!p1.getItems().containsKey(itemP1.getGuid()), "Player 1 must not contain transferred item");
        check(p2.getItems().containsKey(itemP2.getGuid()), "Player 2 must retain the merged item");
        check(p2.getItems().get(itemP2.getGuid()).getQuantity() == 15, "Player 2 merged stack quantity must be 15");
        check(World.world.getGameObject(itemP1.getGuid()) == null, "Old merged item must be removed from World");
    }

    private static void exchangePlayer2ToPlayer1_stacksSimilarItems() throws Exception {
        int templateId = 998_101;
        ObjectTemplate template = new ObjectTemplate(templateId, "", "Test Bois",
                Constant.ITEM_TYPE_RESSOURCE, 1, 1, 0, 0,
                "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);

        GameObject itemP1 = new GameObject(998_102, templateId, 10,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        GameObject itemP2 = new GameObject(998_103, templateId, 5,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        World.world.addGameObject(itemP1);
        World.world.addGameObject(itemP2);

        Player p1 = playerWithInventory(998_110, itemP1);
        Player p2 = playerWithInventory(998_111, itemP2);

        PlayerExchange exchange = new PlayerExchange(p1, p2);

        Method transferMethod = PlayerExchange.class.getDeclaredMethod(
                "transferExchangeItem", Player.class, Player.class, Couple.class);
        transferMethod.setAccessible(true);
        transferMethod.invoke(exchange, p2, p1, new Couple<>(itemP2.getGuid(), 5));

        check(!p2.getItems().containsKey(itemP2.getGuid()), "Player 2 must not contain transferred item");
        check(p1.getItems().containsKey(itemP1.getGuid()), "Player 1 must retain the merged item");
        check(p1.getItems().get(itemP1.getGuid()).getQuantity() == 15, "Player 1 merged stack quantity must be 15 (symmetrical stacking!)");
        check(World.world.getGameObject(itemP2.getGuid()) == null, "Old merged item must be removed from World");
    }

    private static Player playerWithInventory(int id, GameObject... items)
            throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);

        Field idField = Player.class.getDeclaredField("id");
        unsafe.putInt(player, unsafe.objectFieldOffset(idField), id);
        Map<Integer, GameObject> inventory = new HashMap<>();
        for (GameObject item : items)
            inventory.put(item.getGuid(), item);
        Field objectsField = Player.class.getDeclaredField("objects");
        unsafe.putObject(player, unsafe.objectFieldOffset(objectsField), inventory);
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
