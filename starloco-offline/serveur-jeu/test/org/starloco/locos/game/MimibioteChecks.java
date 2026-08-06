package org.starloco.locos.game;

import org.starloco.locos.client.Player;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;

public final class MimibioteChecks {
    private static final int APPARAT_TEMPLATE_ID = 990_003;

    private MimibioteChecks() {
    }

    public static void run() throws ReflectiveOperationException {
        ObjectTemplate apparatTemplate = new ObjectTemplate(
                APPARAT_TEMPLATE_ID, "", "Test apparat", Constant.ITEM_TYPE_COIFFE,
                1, 1, 1, 1, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(apparatTemplate);

        stackedApparatIsRestoredWithoutSelfMerging();
        legacySelfReferenceRestoresExactlyOneItem();
        transferredLegacyStackIsNeverReused();
        singleApparatKeepsItsOriginalIdentity();
        isolatedReferenceSurvivesItemSerialization();
        malformedReferenceIsRejected();
        overflowingLegacyStackIsRejected();
    }

    private static void stackedApparatIsRestoredWithoutSelfMerging()
            throws ReflectiveOperationException {
        GameObject sourceStack = item(101, 3);
        GameObject isolatedApparat = item(102, 1);

        GameObject storedApparat = GameClient.isolateMimibioteApparat(
                sourceStack, () -> isolatedApparat);

        check(storedApparat == isolatedApparat,
                "A stacked apparat must store a detached one-item object");
        check(storedApparat.getGuid() != sourceStack.getGuid(),
                "The detached apparat must not reuse the remaining stack GUID");

        sourceStack.setQuantity(sourceStack.getQuantity() - 1);
        Player player = playerWithItem(sourceStack);
        boolean addedAsNewStack = player.addItem(storedApparat, true, false);

        check(!addedAsNewStack, "The detached apparat must merge into the remaining stack");
        check(sourceStack.getQuantity() == 3,
                "Dissociating a three-item apparat stack must restore 3, not duplicate it to 4");
        check(player.getItems().size() == 1,
                "The restored apparat must not leave a second inventory stack");
    }

    private static void legacySelfReferenceRestoresExactlyOneItem() {
        GameObject legacyStack = item(103, 2);
        HashMap<Integer, GameObject> inventory = new HashMap<>();
        inventory.put(legacyStack.getGuid(), legacyStack);

        GameObject restored = GameClient.restoreOwnedMimibioteApparat(inventory, legacyStack);

        check(restored == legacyStack,
                "An existing mimibiote may still reference its remaining apparat stack");
        check(legacyStack.getQuantity() == 3,
                "A legacy stack must receive one apparat instead of being added to itself");
    }

    private static void transferredLegacyStackIsNeverReused() {
        GameObject transferredStack = item(106, 2);
        String value = Integer.toHexString(transferredStack.getGuid()) + ";"
                + Integer.toHexString(transferredStack.getTemplate().getId());
        GameClient.MimibioteApparatReference reference =
                GameClient.parseMimibioteApparatReference(value);

        check(reference != null && !reference.isolated,
                "An old unmarked apparat reference must be recognized as legacy data");
        check(GameClient.requiresLegacyApparatClone(reference, null),
                "A legacy stack no longer owned by the player must be copied one item at a time");
        check(transferredStack.getQuantity() == 2,
                "Restoring a legacy apparat must not modify a stack transferred to another player");
    }

    private static void singleApparatKeepsItsOriginalIdentity() {
        GameObject singleApparat = item(104, 1);
        boolean[] cloneRequested = {false};

        GameObject storedApparat = GameClient.isolateMimibioteApparat(singleApparat, () -> {
            cloneRequested[0] = true;
            return item(105, 1);
        });

        check(storedApparat == singleApparat,
                "A single apparat can be detached from the inventory without cloning");
        check(!cloneRequested[0], "A one-item apparat must not create an unnecessary clone");
    }

    private static void isolatedReferenceSurvivesItemSerialization() {
        GameObject apparat = item(107, 1);
        GameObject mimibiotedItem = item(108, 1);
        mimibiotedItem.addTxtStat(Constant.STATS_MIMIBIOTE,
                GameClient.encodeMimibioteApparatReference(apparat));

        GameObject reloadedItem = new GameObject(109, APPARAT_TEMPLATE_ID, 1,
                Constant.ITEM_POS_NO_EQUIPED, mimibiotedItem.encodeStats(), 0);
        GameClient.MimibioteApparatReference reference =
                GameClient.parseMimibioteApparatReference(
                        reloadedItem.getTxtStat().get(Constant.STATS_MIMIBIOTE));

        check(reference != null && reference.isolated,
                "The isolated-apparat marker must survive item serialization");
        check(reference.guid == apparat.getGuid(),
                "Serializing a mimibiote must preserve its detached apparat GUID");
        check(reference.templateId == APPARAT_TEMPLATE_ID,
                "Serializing a mimibiote must preserve its appearance template");
    }

    private static void malformedReferenceIsRejected() {
        check(GameClient.parseMimibioteApparatReference(";") == null,
                "An empty mimibiote reference must not throw or restore an item");
        check(GameClient.parseMimibioteApparatReference("not-hex;10") == null,
                "A non-hexadecimal mimibiote reference must be rejected");
    }

    private static void overflowingLegacyStackIsRejected() {
        GameObject legacyStack = item(110, Integer.MAX_VALUE);
        HashMap<Integer, GameObject> inventory = new HashMap<>();
        inventory.put(legacyStack.getGuid(), legacyStack);

        check(GameClient.restoreOwnedMimibioteApparat(inventory, legacyStack) == null,
                "A full integer stack must not overflow while restoring an old mimibiote");
        check(legacyStack.getQuantity() == Integer.MAX_VALUE,
                "A rejected legacy restoration must leave the stack unchanged");
    }

    private static Player playerWithItem(GameObject item) throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);

        Field objectsField = Player.class.getDeclaredField("objects");
        unsafe.putObject(player, unsafe.objectFieldOffset(objectsField), new HashMap<Integer, GameObject>());
        player.getItems().put(item.getGuid(), item);
        return player;
    }

    private static GameObject item(int guid, int quantity) {
        return new GameObject(guid, APPARAT_TEMPLATE_ID, quantity,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
    }

    private static void check(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
