package org.starloco.locos.command;

import org.starloco.locos.command.transfer.ItemTransferCategory;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;

public final class TransferCommandChecks {
    private TransferCommandChecks() {
    }

    public static void run() {
        categoryParsingSupportsAllAliasesAndRequiresExplicitCategory();
        equippedAndBoundAndQuestItemsAreNeverMatched();
        categoryClassificationMatchesCorrectTypes();
    }

    private static void categoryParsingSupportsAllAliasesAndRequiresExplicitCategory() {
        check(ItemTransferCategory.parse("res") == ItemTransferCategory.RESOURCE, "res must resolve to RESOURCE");
        check(ItemTransferCategory.parse("ress") == ItemTransferCategory.RESOURCE, "ress must resolve to RESOURCE");
        check(ItemTransferCategory.parse("ressource") == ItemTransferCategory.RESOURCE, "ressource must resolve to RESOURCE");
        check(ItemTransferCategory.parse("ressourcen") == ItemTransferCategory.RESOURCE, "ressourcen must resolve to RESOURCE");
        check(ItemTransferCategory.parse("r") == ItemTransferCategory.RESOURCE, "r must resolve to RESOURCE");

        check(ItemTransferCategory.parse("equip") == ItemTransferCategory.EQUIPMENT, "equip must resolve to EQUIPMENT");
        check(ItemTransferCategory.parse("equipment") == ItemTransferCategory.EQUIPMENT, "equipment must resolve to EQUIPMENT");
        check(ItemTransferCategory.parse("ausruestung") == ItemTransferCategory.EQUIPMENT, "ausruestung must resolve to EQUIPMENT");
        check(ItemTransferCategory.parse("ausrüstung") == ItemTransferCategory.EQUIPMENT, "ausrüstung must resolve to EQUIPMENT");
        check(ItemTransferCategory.parse("stuff") == ItemTransferCategory.EQUIPMENT, "stuff must resolve to EQUIPMENT");
        check(ItemTransferCategory.parse("gear") == ItemTransferCategory.EQUIPMENT, "gear must resolve to EQUIPMENT");
        check(ItemTransferCategory.parse("e") == ItemTransferCategory.EQUIPMENT, "e must resolve to EQUIPMENT");

        check(ItemTransferCategory.parse("use") == ItemTransferCategory.CONSUMABLE, "use must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("nutz") == ItemTransferCategory.CONSUMABLE, "nutz must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("nutzgegenstand") == ItemTransferCategory.CONSUMABLE, "nutzgegenstand must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("con") == ItemTransferCategory.CONSUMABLE, "con must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("conso") == ItemTransferCategory.CONSUMABLE, "conso must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("consumable") == ItemTransferCategory.CONSUMABLE, "consumable must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("verbrauch") == ItemTransferCategory.CONSUMABLE, "verbrauch must resolve to CONSUMABLE");
        check(ItemTransferCategory.parse("u") == ItemTransferCategory.CONSUMABLE, "u must resolve to CONSUMABLE");

        check(ItemTransferCategory.parse("all") == ItemTransferCategory.ALL, "all must resolve to ALL");
        check(ItemTransferCategory.parse("alle") == ItemTransferCategory.ALL, "alle must resolve to ALL");
        check(ItemTransferCategory.parse("tout") == ItemTransferCategory.ALL, "tout must resolve to ALL");
        check(ItemTransferCategory.parse("alles") == ItemTransferCategory.ALL, "alles must resolve to ALL");
        check(ItemTransferCategory.parse("a") == ItemTransferCategory.ALL, "a must resolve to ALL");

        // Strict category requirement: null or whitespace must return null!
        check(ItemTransferCategory.parse(null) == null, "null category must return null");
        check(ItemTransferCategory.parse("") == null, "empty category must return null");
        check(ItemTransferCategory.parse("   ") == null, "whitespace category must return null");
        check(ItemTransferCategory.parse("invalid_xyz") == null, "invalid category must return null");
    }

    private static void equippedAndBoundAndQuestItemsAreNeverMatched() {
        ObjectTemplate swordTemplate = new ObjectTemplate(991_001, "", "Test Sword",
                Constant.ITEM_TYPE_EPEE, 1, 1, 0, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(swordTemplate);

        // Equipped item (e.g. position 1 = amulet or position 0 = weapon)
        GameObject equippedSword = new GameObject(991_002, swordTemplate.getId(), 1, 0, "", 0);
        check(!ItemTransferCategory.matches(equippedSword, ItemTransferCategory.EQUIPMENT),
                "Equipped item must never match EQUIPMENT");
        check(!ItemTransferCategory.matches(equippedSword, ItemTransferCategory.ALL),
                "Equipped item must never match ALL");

        // Quest item
        ObjectTemplate questTemplate = new ObjectTemplate(991_003, "", "Test Quest Item",
                Constant.ITEM_TYPE_QUETES, 1, 1, 0, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(questTemplate);
        GameObject questItem = new GameObject(991_004, questTemplate.getId(), 1, Constant.ITEM_POS_NO_EQUIPED, "", 0);
        check(!ItemTransferCategory.matches(questItem, ItemTransferCategory.RESOURCE),
                "Quest item must never match RESOURCE");
        check(!ItemTransferCategory.matches(questItem, ItemTransferCategory.ALL),
                "Quest item must never match ALL");
    }

    private static void categoryClassificationMatchesCorrectTypes() {
        ObjectTemplate swordTemplate = new ObjectTemplate(991_010, "", "Unequipped Sword",
                Constant.ITEM_TYPE_EPEE, 1, 1, 0, 0, "", "", 0, 0, 0, 0);
        ObjectTemplate potionTemplate = new ObjectTemplate(991_011, "", "Health Potion",
                Constant.ITEM_TYPE_POTION, 1, 1, 0, 0, "", "", 0, 0, 0, 0);
        ObjectTemplate woodTemplate = new ObjectTemplate(991_012, "", "Oak Wood",
                Constant.ITEM_TYPE_BOIS, 1, 1, 0, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(swordTemplate);
        World.world.addObjTemplate(potionTemplate);
        World.world.addObjTemplate(woodTemplate);

        GameObject sword = new GameObject(991_020, swordTemplate.getId(), 1, Constant.ITEM_POS_NO_EQUIPED, "", 0);
        GameObject potion = new GameObject(991_021, potionTemplate.getId(), 10, Constant.ITEM_POS_NO_EQUIPED, "", 0);
        GameObject wood = new GameObject(991_022, woodTemplate.getId(), 100, Constant.ITEM_POS_NO_EQUIPED, "", 0);

        // Sword checks
        check(ItemTransferCategory.matches(sword, ItemTransferCategory.EQUIPMENT), "Sword must match EQUIPMENT");
        check(ItemTransferCategory.matches(sword, ItemTransferCategory.ALL), "Sword must match ALL");
        check(!ItemTransferCategory.matches(sword, ItemTransferCategory.RESOURCE), "Sword must not match RESOURCE");
        check(!ItemTransferCategory.matches(sword, ItemTransferCategory.CONSUMABLE), "Sword must not match CONSUMABLE");

        // Potion checks
        check(ItemTransferCategory.matches(potion, ItemTransferCategory.CONSUMABLE), "Potion must match CONSUMABLE");
        check(ItemTransferCategory.matches(potion, ItemTransferCategory.ALL), "Potion must match ALL");
        check(!ItemTransferCategory.matches(potion, ItemTransferCategory.RESOURCE), "Potion must not match RESOURCE");
        check(!ItemTransferCategory.matches(potion, ItemTransferCategory.EQUIPMENT), "Potion must not match EQUIPMENT");

        // Wood checks
        check(ItemTransferCategory.matches(wood, ItemTransferCategory.RESOURCE), "Wood must match RESOURCE");
        check(ItemTransferCategory.matches(wood, ItemTransferCategory.ALL), "Wood must match ALL");
        check(!ItemTransferCategory.matches(wood, ItemTransferCategory.CONSUMABLE), "Wood must not match CONSUMABLE");
        check(!ItemTransferCategory.matches(wood, ItemTransferCategory.EQUIPMENT), "Wood must not match EQUIPMENT");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
