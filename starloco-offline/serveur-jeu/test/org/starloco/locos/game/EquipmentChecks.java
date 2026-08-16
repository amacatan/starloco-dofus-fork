package org.starloco.locos.game;

import org.starloco.locos.client.Account;
import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Stats;
import org.starloco.locos.area.map.CellsDataProvider;
import org.starloco.locos.area.map.GameCase;
import org.starloco.locos.area.map.GameMap;
import org.starloco.locos.area.map.MapData;
import org.starloco.locos.area.map.ScriptMapData;
import org.starloco.locos.database.data.game.ExperienceTables;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class EquipmentChecks {
    private static final int BELT_TEMPLATE_ID = 990_001;
    private static final int RING_TEMPLATE_ID = 990_002;
    private static final int CLASS_HAT_TEMPLATE_ID = 990_005;
    private static final int CLASS_RING_TEMPLATE_ID = 990_006;
    private static final int CLASS_CAPE_TEMPLATE_ID = 990_007;
    private static final int LEGACY_WEAPON_TEMPLATE_ID = 990_008;
    private static final int CONDITIONAL_CLASS_HAT_TEMPLATE_ID = 990_009;
    private static final int NORMAL_WEAPON_TEMPLATE_ID = 990_010;
    private static final int INCARNATION_TEMPLATE_ID = 9_544;
    private static final int TEST_ACCOUNT_ID = 991_005;
    private static final int SHARED_SPELL_ID = 0x10;
    private static final int RING_SPELL_EFFECT = 0x119;
    private static final int CAPE_SPELL_EFFECT = 0x11d;

    private EquipmentChecks() {
    }

    public static void run() throws ReflectiveOperationException {
        ObjectTemplate belt = new ObjectTemplate(BELT_TEMPLATE_ID, "", "Test belt",
                Constant.ITEM_TYPE_CEINTURE, 1, 1, 1, 1, "", "", 0, 0, 0, 0);
        ObjectTemplate ring = new ObjectTemplate(RING_TEMPLATE_ID, "", "Test ring",
                Constant.ITEM_TYPE_ANNEAU, 1, 1, 1, 1, "", "", 0, 0, 0, 0);
        ObjectTemplate classHat = new ObjectTemplate(CLASS_HAT_TEMPLATE_ID,
                "121#64#0#1", "Test class hat", Constant.ITEM_TYPE_COIFFE,
                200, 1, 1, 81, "", "", 0, 0, 0, 0);
        ObjectTemplate classRing = new ObjectTemplate(CLASS_RING_TEMPLATE_ID,
                "119#10#0#2", "Test class ring", Constant.ITEM_TYPE_ANNEAU,
                1, 1, 1, 81, "", "", 0, 0, 0, 0);
        ObjectTemplate classCape = new ObjectTemplate(CLASS_CAPE_TEMPLATE_ID,
                "11d#10#0#3", "Test class cape", Constant.ITEM_TYPE_CAPE,
                1, 1, 1, 81, "", "", 0, 0, 0, 0);
        ObjectTemplate legacyWeapon = new ObjectTemplate(LEGACY_WEAPON_TEMPLATE_ID,
                "63#10#19#0#1d10+15,3d7#-1", "Test legacy weapon",
                Constant.ITEM_TYPE_EPEE, 1, 1, 1, 201, "", "", 0, 0, 0, 0);
        ObjectTemplate conditionalClassHat = new ObjectTemplate(
                CONDITIONAL_CLASS_HAT_TEMPLATE_ID, "121#65#0#1",
                "Conditional class hat", Constant.ITEM_TYPE_COIFFE,
                1, 1, 1, 81, "BI", "", 0, 0, 0, 0);
        ObjectTemplate normalWeapon = new ObjectTemplate(
                NORMAL_WEAPON_TEMPLATE_ID, "", "Test normal weapon",
                Constant.ITEM_TYPE_EPEE, 1, 1, 1, 0,
                "", "", 0, 0, 0, 0);
        ObjectTemplate incarnation = new ObjectTemplate(
                INCARNATION_TEMPLATE_ID, "", "Test incarnation",
                Constant.ITEM_TYPE_EPEE, 1, 1, 1, 0,
                "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(belt);
        World.world.addObjTemplate(ring);
        World.world.addObjTemplate(classHat);
        World.world.addObjTemplate(classRing);
        World.world.addObjTemplate(classCape);
        World.world.addObjTemplate(legacyWeapon);
        World.world.addObjTemplate(conditionalClassHat);
        World.world.addObjTemplate(normalWeapon);
        World.world.addObjTemplate(incarnation);

        ExperienceTables previousExperiences = World.world.getExperiences();
        if (previousExperiences == null)
            World.world.setExperiences(testExperienceTables());

        try {
            replacingTheTargetSlotIsAllowed();
            duplicateEquipmentInAnotherSlotIsRejected();
            movingItemDoesNotDuplicateItself();
            inventoryCopiesAreIgnored();
            rejectedClassItemDoesNotGrantSpellBonus();
            invalidClassItemPositionDoesNotGrantSpellBonus();
            failedClassItemConditionDoesNotGrantSpellBonus();
            removingClassItemRestoresSharedSpellBonus();
            replacingClassItemRebuildsFromEquippedItems();
            legacySetsDoNotRemoveClassSpellBonuses();
            replacingIncarnationWithNormalWeaponRestoresPlayer();
            replacingIncarnationPreservesDifferentActiveMorph();
            droppingEquippedIncarnationRestoresPlayer();
            droppingEquippedClassItemRemovesSpellBonus();
            invalidConditionalEquipmentCanBeRemoved();
            persistedIncarnationMorphWithoutMatchingWeaponIsReconciled();
            persistedIncarnationMorphWithMatchingWeaponIsPreserved();
            persistedNonIncarnationMorphIsPreserved();
        } finally {
            if (previousExperiences == null)
                World.world.setExperiences(null);
        }
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

    private static void rejectedClassItemDoesNotGrantSpellBonus()
            throws ReflectiveOperationException {
        GameObject classHat = item(5, CLASS_HAT_TEMPLATE_ID,
                Constant.ITEM_POS_NO_EQUIPED);
        Unsafe unsafe = unsafe();
        Player player = playerWithItem(unsafe, classHat);
        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        Account account = accountFor(unsafe, client);

        setObject(unsafe, player, Player.class, "_accID", TEST_ACCOUNT_ID);
        setObject(unsafe, client, GameClient.class, "account", account);
        setObject(unsafe, client, GameClient.class, "player", player);

        client.movementObject("OM" + classHat.getGuid() + "|"
                + Constant.ITEM_POS_COIFFE + "|1");

        check(classHat.getPosition() == Constant.ITEM_POS_NO_EQUIPED,
                "A rejected class item must remain in the inventory");
        check(player.getObjectsClassSpell().isEmpty(),
                "A rejected class item must not grant a spell bonus");
        check(stub.writes().stream()
                        .noneMatch(packet -> String.valueOf(packet).startsWith("SB")),
                "A rejected class item must not send a spell-boost packet");
        check(stub.writes().stream().anyMatch(packet -> "OAEL".equals(packet)),
                "An over-level class item must be rejected by the equipment flow");
    }

    private static void invalidClassItemPositionDoesNotGrantSpellBonus()
            throws ReflectiveOperationException {
        GameObject classHat = item(13, CLASS_HAT_TEMPLATE_ID,
                Constant.ITEM_POS_NO_EQUIPED);
        TestContext context = contextWithItems(classHat);

        context.client.movementObject("OM" + classHat.getGuid() + "|"
                + Constant.ITEM_POS_ARME + "|1");

        check(classHat.getPosition() == Constant.ITEM_POS_NO_EQUIPED,
                "A class item sent to an invalid slot must remain in the inventory");
        check(context.player.getObjectsClassSpell().isEmpty(),
                "An invalid class-item slot must not grant a spell bonus");
        check(context.stub.writes().stream()
                        .noneMatch(packet -> String.valueOf(packet).startsWith("SB")),
                "An invalid class-item slot must not send a spell-boost packet");
    }

    private static void failedClassItemConditionDoesNotGrantSpellBonus()
            throws ReflectiveOperationException {
        GameObject classHat = item(14, CONDITIONAL_CLASS_HAT_TEMPLATE_ID,
                Constant.ITEM_POS_NO_EQUIPED);
        TestContext context = contextWithItems(classHat);

        context.client.movementObject("OM" + classHat.getGuid() + "|"
                + Constant.ITEM_POS_COIFFE + "|1");

        check(classHat.getPosition() == Constant.ITEM_POS_NO_EQUIPED,
                "A class item with unmet conditions must remain in the inventory");
        check(context.player.getObjectsClassSpell().isEmpty(),
                "Unmet class-item conditions must not grant a spell bonus");
        check(context.stub.writes().stream()
                        .noneMatch(packet -> String.valueOf(packet).startsWith("SB")),
                "Unmet class-item conditions must not send a spell-boost packet");
    }

    private static void removingClassItemRestoresSharedSpellBonus()
            throws ReflectiveOperationException {
        GameObject ring = item(6, CLASS_RING_TEMPLATE_ID, Constant.ITEM_POS_ANNEAU1);
        GameObject cape = item(7, CLASS_CAPE_TEMPLATE_ID, Constant.ITEM_POS_CAPE);
        TestContext context = contextWithItems(ring, cape);

        context.player.refreshObjectsClass();
        context.player.unequipedObjet(ring);

        World.Couple<Integer, Integer> bonus =
                context.player.getObjectsClassSpell().get(SHARED_SPELL_ID);
        check(bonus != null && bonus.first == CAPE_SPELL_EFFECT && bonus.second == 3,
                "Removing one class item must restore the shared spell bonus from equipped gear");
        check(context.stub.writes().stream().anyMatch(packet ->
                        String.valueOf(packet).startsWith(
                                "SB" + CAPE_SPELL_EFFECT + ";" + SHARED_SPELL_ID + ";3")),
                "The surviving shared spell bonus must be sent back to the client");
    }

    private static void replacingClassItemRebuildsFromEquippedItems()
            throws ReflectiveOperationException {
        GameObject oldRing = item(8, CLASS_RING_TEMPLATE_ID, Constant.ITEM_POS_ANNEAU1);
        GameObject replacementRing = item(9, CLASS_RING_TEMPLATE_ID,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject cape = item(10, CLASS_CAPE_TEMPLATE_ID, Constant.ITEM_POS_CAPE);
        TestContext context = contextWithItems(oldRing, replacementRing, cape);

        context.player.refreshObjectsClass();
        oldRing.setPosition(Constant.ITEM_POS_NO_EQUIPED);
        GameClient.removeClassSpellBonuses(context.player, oldRing.getTemplate());
        replacementRing.setPosition(Constant.ITEM_POS_ANNEAU1);
        context.player.refreshObjectsClass();

        World.Couple<Integer, Integer> bonus =
                context.player.getObjectsClassSpell().get(SHARED_SPELL_ID);
        check(bonus != null && bonus.first == RING_SPELL_EFFECT && bonus.second == 2,
                "Replacing a class item must rebuild bonuses from the final equipment state");
    }

    private static void legacySetsDoNotRemoveClassSpellBonuses()
            throws ReflectiveOperationException {
        GameObject ring = item(11, CLASS_RING_TEMPLATE_ID, Constant.ITEM_POS_ANNEAU1);
        GameObject legacyWeapon = item(12, LEGACY_WEAPON_TEMPLATE_ID,
                Constant.ITEM_POS_ARME);
        TestContext context = contextWithItems(ring, legacyWeapon);

        context.player.refreshObjectsClass();
        long boostsBeforeRemoval = context.stub.writes().stream()
                .filter(packet -> String.valueOf(packet).startsWith("SB"))
                .count();
        context.player.unequipedObjet(legacyWeapon);

        World.Couple<Integer, Integer> bonus =
                context.player.getObjectsClassSpell().get(SHARED_SPELL_ID);
        check(bonus != null && bonus.first == RING_SPELL_EFFECT && bonus.second == 2,
                "Panoplies 201-212 must not clear real class spell bonuses");
        long boostsAfterRemoval = context.stub.writes().stream()
                .filter(packet -> String.valueOf(packet).startsWith("SB"))
                .count();
        check(boostsAfterRemoval == boostsBeforeRemoval,
                "Panoplies 201-212 must not emit spell-boost removal packets");
    }

    private static void replacingIncarnationWithNormalWeaponRestoresPlayer()
            throws ReflectiveOperationException {
        GameObject incarnation = item(15, INCARNATION_TEMPLATE_ID,
                Constant.ITEM_POS_ARME);
        GameObject normalWeapon = item(16, NORMAL_WEAPON_TEMPLATE_ID,
                Constant.ITEM_POS_NO_EQUIPED);
        TestContext context = contextWithItems(incarnation, normalWeapon);
        markAsIncarnation(context.player);

        String movement = "OM" + normalWeapon.getGuid() + "|"
                + Constant.ITEM_POS_ARME + "|1";
        // A replacement is a two-pass legacy operation: first free the slot,
        // then replay the packet to equip the incoming item.
        movementWithoutPersistence(context, movement);
        movementWithoutPersistence(context, movement);

        check(incarnation.getPosition() == Constant.ITEM_POS_NO_EQUIPED,
                "Replacing an incarnation must move the former weapon to inventory");
        check(normalWeapon.getPosition() == Constant.ITEM_POS_ARME,
                "The normal replacement weapon must occupy the weapon slot");
        checkPlayerRestoredAfterIncarnation(context.player,
                "Replacing an incarnation with a normal weapon");
    }

    private static void droppingEquippedIncarnationRestoresPlayer()
            throws ReflectiveOperationException {
        GameObject incarnation = item(17, INCARNATION_TEMPLATE_ID,
                Constant.ITEM_POS_ARME);
        TestContext context = contextWithItems(incarnation);
        markAsIncarnation(context.player);

        drop(context, incarnation);

        check(!context.player.hasItemGuid(incarnation.getGuid()),
                "A completely dropped incarnation must leave the inventory");
        check(isDropped(context.player.getCurMap(), incarnation),
                "The incarnation must be present on a neighbouring map cell");
        checkPlayerRestoredAfterIncarnation(context.player,
                "Dropping an equipped incarnation");
    }

    private static void replacingIncarnationPreservesDifferentActiveMorph()
            throws ReflectiveOperationException {
        GameObject incarnation = item(20, INCARNATION_TEMPLATE_ID,
                Constant.ITEM_POS_ARME);
        GameObject normalWeapon = item(21, NORMAL_WEAPON_TEMPLATE_ID,
                Constant.ITEM_POS_NO_EQUIPED);
        TestContext context = contextWithItems(incarnation, normalWeapon);
        TestPlayer player = (TestPlayer) context.player;
        player.enterMorph(5, false);

        String movement = "OM" + normalWeapon.getGuid() + "|"
                + Constant.ITEM_POS_ARME + "|1";
        movementWithoutPersistence(context, movement);
        movementWithoutPersistence(context, movement);

        check(normalWeapon.getPosition() == Constant.ITEM_POS_ARME,
                "A normal weapon must still replace the removed incarnation");
        check(player.getMorphMode() && player.getMorphId() == 5,
                "Removing an incarnation must preserve a different active morph");
        check(player.unsetFullMorphCalls == 0,
                "Removing an unrelated incarnation must not call unsetFullMorph");
    }

    private static void droppingEquippedClassItemRemovesSpellBonus()
            throws ReflectiveOperationException {
        GameObject classHat = item(18, CLASS_HAT_TEMPLATE_ID,
                Constant.ITEM_POS_COIFFE);
        TestContext context = contextWithItems(classHat);
        context.player.refreshObjectsClass();
        check(!context.player.getObjectsClassSpell().isEmpty(),
                "The class item must grant its bonus before being dropped");

        drop(context, classHat);

        check(!context.player.hasItemGuid(classHat.getGuid()),
                "A completely dropped class item must leave the inventory");
        check(isDropped(context.player.getCurMap(), classHat),
                "The class item must be present on a neighbouring map cell");
        check(context.player.getObjectsClassSpell().isEmpty(),
                "Dropping an equipped class item must remove its spell bonus");
    }

    private static void invalidConditionalEquipmentCanBeRemoved()
            throws ReflectiveOperationException {
        GameObject classHat = item(19, CONDITIONAL_CLASS_HAT_TEMPLATE_ID,
                Constant.ITEM_POS_COIFFE);
        TestContext context = contextWithItems(classHat);
        context.player.refreshObjectsClass();
        check(!context.player.getObjectsClassSpell().isEmpty(),
                "The conditional class item must start with an active spell bonus");

        movementWithoutPersistence(context, "OM" + classHat.getGuid() + "|"
                + Constant.ITEM_POS_NO_EQUIPED + "|1");

        check(classHat.getPosition() == Constant.ITEM_POS_NO_EQUIPED,
                "An equipped item must remain removable after its conditions fail");
        check(context.player.getObjectsClassSpell().isEmpty(),
                "Removing invalid conditional equipment must clean its spell bonus");
        check(context.stub.writes().stream().noneMatch(packet ->
                        String.valueOf(packet).startsWith("Im119|44")),
                "Unequipping must not be rejected by the item's equip conditions");
    }

    private static void persistedIncarnationMorphWithoutMatchingWeaponIsReconciled()
            throws ReflectiveOperationException {
        TestContext context = contextWithItems();
        TestPlayer player = (TestPlayer) context.player;
        player.enterMorph(1, true);

        GameClient.reconcileIncarnationMorph(player);

        checkPlayerRestoredAfterIncarnation(player,
                "Loading an incarnation morph without its matching weapon");
    }

    private static void persistedIncarnationMorphWithMatchingWeaponIsPreserved()
            throws ReflectiveOperationException {
        GameObject incarnation = item(22, INCARNATION_TEMPLATE_ID,
                Constant.ITEM_POS_ARME);
        TestContext context = contextWithItems(incarnation);
        TestPlayer player = (TestPlayer) context.player;
        player.enterMorph(1, true);

        GameClient.reconcileIncarnationMorph(player);

        check(player.getMorphMode() && player.getMorphId() == 1,
                "Loading an incarnation morph with its matching weapon must preserve it");
        check(player.unsetFullMorphCalls == 0,
                "A consistent persisted incarnation must not be demorphed");
    }

    private static void persistedNonIncarnationMorphIsPreserved()
            throws ReflectiveOperationException {
        TestContext context = contextWithItems();
        TestPlayer player = (TestPlayer) context.player;
        player.enterMorph(42, false);

        GameClient.reconcileIncarnationMorph(player);

        check(player.getMorphMode() && player.getMorphId() == 42,
                "Login reconciliation must preserve non-incarnation morphs");
        check(player.unsetFullMorphCalls == 0,
                "A non-incarnation morph must not call unsetFullMorph at login");
    }

    private static void markAsIncarnation(Player player)
            throws ReflectiveOperationException {
        ((TestPlayer) player).enterIncarnation();
    }

    private static void checkPlayerRestoredAfterIncarnation(Player player,
                                                             String action) {
        TestPlayer testPlayer = (TestPlayer) player;
        check(!testPlayer.getMorphMode(),
                action + " must disable the full morph");
        check(testPlayer.unsetFullMorphCalls == 1,
                action + " must invoke incarnation cleanup exactly once");
        check(testPlayer.normalSpellsRestored
                        && !testPlayer.incarnationSpellsActive,
                action + " must restore normal spells and remove incarnation spells");
    }

    private static void drop(TestContext context, GameObject object)
            throws ReflectiveOperationException {
        Method dropObject = GameClient.class.getDeclaredMethod(
                "dropObject", String.class);
        dropObject.setAccessible(true);
        dropObject.invoke(context.client,
                "OD" + object.getGuid() + "|" + object.getQuantity());
    }

    private static void movementWithoutPersistence(TestContext context,
                                                   String packet) {
        TestPlayer player = (TestPlayer) context.player;
        player.abortBeforePersistence = true;
        player.persistenceBoundaryReached = false;
        PrintStream previousError = System.err;
        try {
            System.setErr(new PrintStream(new ByteArrayOutputStream()));
            context.client.movementObject(packet);
        } finally {
            System.setErr(previousError);
            player.abortBeforePersistence = false;
        }
        check(player.persistenceBoundaryReached,
                "The equipment flow must reach the test persistence boundary");
    }

    private static boolean isDropped(GameMap map, GameObject expected) {
        for (int cellId = 0; cellId < 41; cellId++) {
            GameCase cell = map.getCase(cellId);
            if (cell != null && cell.getDroppedItem(false) == expected)
                return true;
        }
        return false;
    }

    private static TestContext contextWithItems(GameObject... items)
            throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        Player player = playerWithItems(unsafe, items);
        GameMap map = testMap(5, 5);
        setObject(unsafe, player, Player.class, "curMap", map);
        setObject(unsafe, player, Player.class, "curCell", map.getCase(20));
        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        Account account = accountFor(unsafe, client);

        setObject(unsafe, player, Player.class, "_accID", TEST_ACCOUNT_ID);
        setObject(unsafe, client, GameClient.class, "account", account);
        setObject(unsafe, client, GameClient.class, "player", player);
        return new TestContext(player, stub, client);
    }

    private static Player playerWithItem(Unsafe unsafe, GameObject item)
            throws ReflectiveOperationException {
        return playerWithItems(unsafe, item);
    }

    private static Player playerWithItems(Unsafe unsafe, GameObject... items)
            throws ReflectiveOperationException {
        Player player = (Player) unsafe.allocateInstance(TestPlayer.class);
        setObject(unsafe, player, Player.class, "objects", new HashMap<Integer, GameObject>());
        setObject(unsafe, player, Player.class, "objectsClass", new ArrayList<Integer>());
        setObject(unsafe, player, Player.class, "objectsClassSpell", new HashMap<>());
        setObject(unsafe, player, Player.class, "stats", new Stats());
        setObject(unsafe, player, Player.class, "statsParcho", new Stats());
        setObject(unsafe, player, Player.class, "buffs", new HashMap<>());
        setObject(unsafe, player, Player.class, "_metiers", new HashMap<>());
        setObject(unsafe, player, Player.class, "_storeItems", new HashMap<>());
        setObject(unsafe, player, Player.class, "_sorts", new HashMap<>());
        setObject(unsafe, player, Player.class, "_sortsPlaces", new HashMap<>());
        setObject(unsafe, player, Player.class, "_saveSorts", new HashMap<>());
        setObject(unsafe, player, Player.class, "_saveSortsPlaces", new HashMap<>());
        setObject(unsafe, player, Player.class, "classe", 1);
        setObject(unsafe, player, Player.class, "gfxId", 10);
        setObject(unsafe, player, Player.class, "maxPdv", 50);
        setObject(unsafe, player, Player.class, "curPdv", 50);
        player.setLevel(1);
        for (GameObject item : items)
            player.getItems().put(item.getGuid(), item);
        return player;
    }

    private static GameMap testMap(int width, int height)
            throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        int cellCount = width * height + (width - 1) * (height - 1);
        byte[] rawCells = new byte[cellCount * 10];
        for (int cellId = 0; cellId < cellCount; cellId++) {
            rawCells[cellId * 10] = 0x20;
            rawCells[cellId * 10 + 2] = 0x10;
        }
        CellsDataProvider.RawCellsDataProvider raw =
                new CellsDataProvider.RawCellsDataProvider(rawCells);

        ScriptMapData data = (ScriptMapData) unsafe.allocateInstance(
                ScriptMapData.class);
        setObject(unsafe, data, MapData.class, "width", width);
        setObject(unsafe, data, MapData.class, "height", height);

        GameMap map = (GameMap) unsafe.allocateInstance(GameMap.class);
        setObject(unsafe, map, GameMap.class, "data", data);
        setObject(unsafe, map, GameMap.class, "cellsData",
                new CellsDataProvider.CellsDataOverride(raw));
        List<GameCase> cases = new ArrayList<>(cellCount);
        for (int cellId = 0; cellId < cellCount; cellId++)
            cases.add(new GameCase(map, cellId));
        setObject(unsafe, map, GameMap.class, "cases", cases);
        setObject(unsafe, map, GameMap.class, "actors", new ConcurrentHashMap<>());
        setObject(unsafe, map, GameMap.class, "droppedItems",
                new ConcurrentHashMap<>());
        setObject(unsafe, map, GameMap.class, "interactiveObjects",
                new HashMap<>());
        return map;
    }

    private static ExperienceTables testExperienceTables() {
        long[] values = {0, 100, 300};
        return new ExperienceTables(values, values, values, values,
                values, values, values, values);
    }

    private static Account accountFor(Unsafe unsafe, GameClient client)
            throws ReflectiveOperationException {
        Account account = (Account) unsafe.allocateInstance(Account.class);
        Field idField = Account.class.getDeclaredField("id");
        unsafe.putInt(account, unsafe.objectFieldOffset(idField), TEST_ACCOUNT_ID);
        account.setGameClient(client);
        World.world.addAccount(account);
        return account;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static void setObject(Unsafe unsafe, Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        if (field.getType() == int.class) {
            unsafe.putInt(target, unsafe.objectFieldOffset(field), (Integer) value);
        } else if (field.getType() == boolean.class) {
            unsafe.putBoolean(target, unsafe.objectFieldOffset(field), (Boolean) value);
        } else {
            unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
        }
    }

    private static GameObject item(int guid, int templateId, int position) {
        return new GameObject(guid, templateId, 1, position, "", 0);
    }

    private static final class TestContext {
        private final Player player;
        private final IoSessionStub stub;
        private final GameClient client;

        private TestContext(Player player, IoSessionStub stub, GameClient client) {
            this.player = player;
            this.stub = stub;
            this.client = client;
        }
    }

    /**
     * The production demorph routine also persists the player.  Equipment tests
     * use this recording seam so they can verify the movement/drop contract
     * without opening a database connection; Player.unsetFullMorph itself is
     * exercised by the live player flow.
     */
    private static final class TestPlayer extends Player {
        private boolean morphMode;
        private boolean incarnationSpellsActive;
        private boolean normalSpellsRestored;
        private int unsetFullMorphCalls;
        private int morphId;
        private boolean abortBeforePersistence;
        private boolean persistenceBoundaryReached;

        @SuppressWarnings("unused")
        private TestPlayer() {
            super(0, "", 0, 0, 1,
                    0, 0, 0, 0L, 0,
                    0, 0, 1, 0L, 0,
                    10, (byte) 0, 0, new HashMap<>(),
                    (byte) 0, (byte) 0, (byte) 0, "", (short) 0, 0,
                    "", "", 100, "", "", "", 0,
                    -1, 0, 0, 0, "", (byte) 0,
                    0, "", "", "", 0L, false,
                    "", 0L, false, "", (byte) 0, 0L);
        }

        private void enterIncarnation() {
            enterMorph(1, true);
        }

        private void enterMorph(int id, boolean hasIncarnationSpells) {
            morphMode = true;
            morphId = id;
            incarnationSpellsActive = hasIncarnationSpells;
            normalSpellsRestored = false;
            unsetFullMorphCalls = 0;
        }

        @Override
        public boolean getMorphMode() {
            return morphMode;
        }

        @Override
        public int getMorphId() {
            return morphId;
        }

        @Override
        public void unsetFullMorph() {
            unsetFullMorphCalls++;
            morphMode = false;
            morphId = 0;
            incarnationSpellsActive = false;
            normalSpellsRestored = true;
        }

        @Override
        public void verifEquiped() {
            if (abortBeforePersistence) {
                persistenceBoundaryReached = true;
                throw new TestPersistenceBoundaryException();
            }
            super.verifEquiped();
        }
    }

    private static final class TestPersistenceBoundaryException
            extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
