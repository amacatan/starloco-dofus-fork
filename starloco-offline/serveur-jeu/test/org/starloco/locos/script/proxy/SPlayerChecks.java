package org.starloco.locos.script.proxy;

import org.starloco.locos.client.Player;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class SPlayerChecks {
    private static final int TEMPLATE_BASE = 998_600;
    private static final int GUID_BASE = 998_700;
    private static final List<Integer> templateIds = new ArrayList<>();
    private static final List<Integer> objectIds = new ArrayList<>();
    private static int nextTemplateId = TEMPLATE_BASE;
    private static int nextGuid = GUID_BASE;

    private SPlayerChecks() {
    }

    public static void run() throws Exception {
        try {
            newInventoryItemIsCached();
            stackMergeDeletesTheTemporaryRow();
            positionedItemIsPersistedBeforeExposure();
            failedPositionPersistenceRollsBackCreation();
            occupiedPositionIsRejectedBeforeCreation();
            invalidInputsFailWithoutAnException();
        } finally {
            for (int guid : objectIds)
                World.world.forgetGameObject(guid);
            for (int templateId : templateIds)
                World.world.getObjectsTemplates().remove(templateId);
        }
    }

    private static void newInventoryItemIsCached() throws Exception {
        StubTemplate template = template(false);
        Player player = player();
        TrackingObjectData objects = new TrackingObjectData();

        check(SPlayer.addItem(player, template.getId(), 2,
                        Constant.ITEM_POS_NO_EQUIPED, true, false, objects),
                "A valid scripted item grant must succeed");

        GameObject created = template.lastCreated;
        check(template.callCount == 1 && template.lastQuantity == 2
                        && template.lastUseMax,
                "The scripted grant must preserve quantity and perfect-stat options");
        check(player.getItems().get(created.getGuid()) == created,
                "A new scripted item must enter the player's inventory");
        check(World.world.getGameObjects().contains(created),
                "A new scripted item must enter the world object cache");
        check(objects.updateCalls == 0 && objects.deleteCalls == 0,
                "An unequipped new item needs no corrective database write");
    }

    private static void stackMergeDeletesTheTemporaryRow() throws Exception {
        StubTemplate template = template(false);
        Player player = player();
        GameObject existing = item(template, 2, Constant.ITEM_POS_NO_EQUIPED);
        player.getItems().put(existing.getGuid(), existing);
        World.world.addGameObject(existing);
        TrackingObjectData objects = new TrackingObjectData();

        check(SPlayer.addItem(player, template.getId(), 3,
                        Constant.ITEM_POS_NO_EQUIPED, false, false, objects),
                "A successful scripted stack merge must report success");

        GameObject temporary = template.lastCreated;
        check(player.getItems().size() == 1 && existing.getQuantity() == 5,
                "A scripted stack merge must credit the exact requested quantity");
        check(!player.getItems().containsKey(temporary.getGuid())
                        && !World.world.getGameObjects().contains(temporary),
                "The temporary merged object must not remain live");
        check(objects.deleteCalls == 1 && objects.deleted == temporary,
                "The temporary merged object's database row must be deleted");
        check(objects.updateCalls == 0,
                "An unequipped stack merge needs no position update");
    }

    private static void positionedItemIsPersistedBeforeExposure() throws Exception {
        StubTemplate template = template(false);
        Player player = player();
        GameObject inventoryStack = item(template, 4,
                Constant.ITEM_POS_NO_EQUIPED);
        player.getItems().put(inventoryStack.getGuid(), inventoryStack);
        TrackingObjectData objects = new TrackingObjectData();

        check(SPlayer.addItem(player, template.getId(), 1,
                        Constant.ITEM_POS_ROLEPLAY_BUFF, false, false, objects),
                "A scripted item must be addable to a free role-play slot");

        GameObject positioned = template.lastCreated;
        check(positioned.getPosition() == Constant.ITEM_POS_ROLEPLAY_BUFF,
                "The scripted item must keep its requested position");
        check(objects.updateCalls == 1 && objects.updated == positioned
                        && objects.updatedPosition == Constant.ITEM_POS_ROLEPLAY_BUFF,
                "The requested position must be durable before inventory exposure");
        check(player.getItems().size() == 2
                        && inventoryStack.getQuantity() == 4,
                "A positioned item must not merge into an inventory stack");
        check(World.world.getGameObjects().contains(positioned)
                        && objects.deleteCalls == 0,
                "A positioned item must remain cached and persisted");
    }

    private static void failedPositionPersistenceRollsBackCreation()
            throws Exception {
        StubTemplate template = template(false);
        Player player = player();
        TrackingObjectData objects = new TrackingObjectData();
        objects.updateResult = false;

        check(!SPlayer.addItem(player, template.getId(), 1,
                        Constant.ITEM_POS_ROLEPLAY_BUFF, false, false, objects),
                "A non-durable equipped position must reject the scripted grant");

        GameObject rejected = template.lastCreated;
        check(player.getItems().isEmpty()
                        && !World.world.getGameObjects().contains(rejected),
                "A rejected positioned item must never become player-visible");
        check(objects.updateCalls == 1 && objects.deleteCalls == 1
                        && objects.deleted == rejected,
                "A rejected positioned item must remove its freshly-created row");
    }

    private static void occupiedPositionIsRejectedBeforeCreation()
            throws Exception {
        StubTemplate template = template(false);
        Player player = player();
        GameObject equipped = item(template, 1,
                Constant.ITEM_POS_ROLEPLAY_BUFF);
        player.getItems().put(equipped.getGuid(), equipped);
        TrackingObjectData objects = new TrackingObjectData();

        check(!SPlayer.addItem(player, template.getId(), 1,
                        Constant.ITEM_POS_ROLEPLAY_BUFF, false, false, objects),
                "A scripted grant must not overwrite an occupied position");
        check(template.callCount == 0 && objects.updateCalls == 0
                        && objects.deleteCalls == 0,
                "An occupied position must be rejected before object creation");
    }

    private static void invalidInputsFailWithoutAnException() throws Exception {
        Player player = player();
        TrackingObjectData objects = new TrackingObjectData();
        int missingTemplate = nextTemplateId++;

        check(!SPlayer.addItem(player, missingTemplate, 1,
                        Constant.ITEM_POS_NO_EQUIPED, false, false, objects),
                "An unknown scripted item template must fail cleanly");

        StubTemplate rejectedTemplate = template(false);
        check(!SPlayer.addItem(player, rejectedTemplate.getId(), 0,
                        Constant.ITEM_POS_NO_EQUIPED, false, false, objects),
                "A non-positive scripted item quantity must be rejected");
        check(rejectedTemplate.callCount == 0,
                "An invalid quantity must be rejected before object creation");

        StubTemplate nullTemplate = template(true);
        check(!SPlayer.addItem(player, nullTemplate.getId(), 1,
                        Constant.ITEM_POS_NO_EQUIPED, false, false, objects),
                "A failed object insertion must fail cleanly");
        check(player.getItems().isEmpty() && objects.updateCalls == 0
                        && objects.deleteCalls == 0,
                "Failed object creation must not mutate inventory or persistence");
    }

    private static StubTemplate template(boolean returnNull) {
        int id = nextTemplateId++;
        StubTemplate template = new StubTemplate(id, returnNull);
        World.world.addObjTemplate(template);
        templateIds.add(id);
        return template;
    }

    private static GameObject item(ObjectTemplate template, int quantity,
                                   int position) {
        int guid = nextGuid++;
        GameObject item = new GameObject(guid, template.getId(), quantity,
                position, "", 0);
        objectIds.add(guid);
        return item;
    }

    private static Player player() throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setObject(unsafe, player, Player.class, "objects",
                new HashMap<Integer, GameObject>());
        return player;
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

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class StubTemplate extends ObjectTemplate {
        private final boolean returnNull;
        private int callCount;
        private int lastQuantity;
        private boolean lastUseMax;
        private GameObject lastCreated;

        private StubTemplate(int id, boolean returnNull) {
            super(id, "", "Scripted item test", Constant.ITEM_TYPE_RESSOURCE,
                    1, 1, 1, 0, "", "", 0, 0, 0, 0);
            this.returnNull = returnNull;
        }

        @Override
        public GameObject createNewItem(int quantity, boolean useMax) {
            callCount++;
            lastQuantity = quantity;
            lastUseMax = useMax;
            if (returnNull) return null;
            lastCreated = item(this, quantity, Constant.ITEM_POS_NO_EQUIPED);
            return lastCreated;
        }
    }

    private static final class TrackingObjectData extends ObjectData {
        private boolean updateResult = true;
        private int updateCalls;
        private int deleteCalls;
        private int updatedPosition;
        private GameObject updated;
        private GameObject deleted;

        private TrackingObjectData() {
            super(null);
        }

        @Override
        public synchronized boolean updateSafely(GameObject entity) {
            updateCalls++;
            updated = entity;
            updatedPosition = entity.getPosition();
            return updateResult;
        }

        @Override
        public synchronized boolean deleteSafely(GameObject entity) {
            deleteCalls++;
            deleted = entity;
            return true;
        }
    }
}
