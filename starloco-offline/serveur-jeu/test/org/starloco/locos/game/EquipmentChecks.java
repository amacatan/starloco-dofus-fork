package org.starloco.locos.game;

import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;

import java.util.List;

public final class EquipmentChecks {
    private static final int BELT_TEMPLATE_ID = 990_001;
    private static final int RING_TEMPLATE_ID = 990_002;

    private EquipmentChecks() {
    }

    public static void run() {
        ObjectTemplate belt = new ObjectTemplate(BELT_TEMPLATE_ID, "", "Test belt",
                Constant.ITEM_TYPE_CEINTURE, 1, 1, 1, 1, "", "", 0, 0, 0, 0);
        ObjectTemplate ring = new ObjectTemplate(RING_TEMPLATE_ID, "", "Test ring",
                Constant.ITEM_TYPE_ANNEAU, 1, 1, 1, 1, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(belt);
        World.world.addObjTemplate(ring);

        replacingTheTargetSlotIsAllowed();
        duplicateEquipmentInAnotherSlotIsRejected();
        movingItemDoesNotDuplicateItself();
        inventoryCopiesAreIgnored();
    }

    private static void replacingTheTargetSlotIsAllowed() {
        GameObject equippedBelt = item(1, BELT_TEMPLATE_ID, Constant.ITEM_POS_CEINTURE);
        GameObject movingBelt = item(4, BELT_TEMPLATE_ID, Constant.ITEM_POS_NO_EQUIPED);

        check(!GameClient.hasSameTemplateEquippedOutsideTarget(
                        List.of(equippedBelt, movingBelt), movingBelt, Constant.ITEM_POS_CEINTURE),
                "An equipped belt must not prevent another copy from replacing it");
    }

    private static void duplicateEquipmentInAnotherSlotIsRejected() {
        GameObject firstRing = item(2, RING_TEMPLATE_ID, Constant.ITEM_POS_ANNEAU1);
        GameObject movingRing = item(4, RING_TEMPLATE_ID, Constant.ITEM_POS_NO_EQUIPED);

        check(GameClient.hasSameTemplateEquippedOutsideTarget(
                        List.of(firstRing, movingRing), movingRing, Constant.ITEM_POS_ANNEAU2),
                "The duplicate guard must still reject the same template in another slot");
    }

    private static void movingItemDoesNotDuplicateItself() {
        GameObject movingRing = item(2, RING_TEMPLATE_ID, Constant.ITEM_POS_ANNEAU1);

        check(!GameClient.hasSameTemplateEquippedOutsideTarget(
                        List.of(movingRing), movingRing, Constant.ITEM_POS_ANNEAU2),
                "Moving equipped gear to another compatible slot must not count the item twice");
    }

    private static void inventoryCopiesAreIgnored() {
        GameObject inventoryItem = item(3, BELT_TEMPLATE_ID, Constant.ITEM_POS_NO_EQUIPED);
        GameObject movingBelt = item(4, BELT_TEMPLATE_ID, Constant.ITEM_POS_NO_EQUIPED);

        check(!GameClient.hasSameTemplateEquippedOutsideTarget(
                        List.of(inventoryItem, movingBelt), movingBelt, Constant.ITEM_POS_CEINTURE),
                "An inventory copy is not an equipped duplicate");
    }

    private static GameObject item(int guid, int templateId, int position) {
        return new GameObject(guid, templateId, 1, position, "", 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
