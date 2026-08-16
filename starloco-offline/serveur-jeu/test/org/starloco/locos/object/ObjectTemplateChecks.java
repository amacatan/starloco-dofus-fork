package org.starloco.locos.object;

import org.starloco.locos.client.other.Stats;
import org.starloco.locos.entity.pet.Pet;
import org.starloco.locos.entity.pet.PetEntry;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.game.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class ObjectTemplateChecks {
    private ObjectTemplateChecks() {
    }

    public static void run() {
        ObjectTemplate axe = new ObjectTemplate(993_100,
                "32c#a#1#0#0", "Ethereal test axe",
                Constant.ITEM_TYPE_HACHE, 1, 1, 1, 0,
                "", "", 0, 0, 0, 0);

        Map<Integer, String> durability = axe.createInitialDurabilityStats();
        check("a".equals(durability.get(Constant.STATS_RESIST)),
                "Ethereal axes must receive their initial durability text stat");

        World.world.addObjTemplate(axe);
        GameObject persistedWithoutDurability = new GameObject(993_101,
                axe.getId(), 1, Constant.ITEM_POS_NO_EQUIPED, "", 0);
        check("a".equals(persistedWithoutDurability.getTxtStat()
                        .get(Constant.STATS_RESIST)),
                "Persisted ethereal axes missing the old type-19 initialization "
                        + "must be repaired lazily when loaded");

        persistedWithoutDurability.getTxtStat().put(Constant.STATS_RESIST, "5");
        GameObject reloaded = new GameObject(993_102, axe.getId(), 1,
                Constant.ITEM_POS_NO_EQUIPED,
                persistedWithoutDurability.parseToSave(), 0);
        check("5".equals(reloaded.getTxtStat().get(Constant.STATS_RESIST)),
                "Repaired durability must survive the database stats round trip");

        cloneMetadataIsIndependent(axe);
        petEpoMarkerSurvivesObjectPersistence();
        chanilRestorationUsesTheEpoMarker();
        chanilCopiesSoulStatsIndependently();
    }

    private static void chanilRestorationUsesTheEpoMarker() {
        Map<Integer, String> improved = new HashMap<>();
        improved.put(Constant.STATS_PETS_EPO, "1");
        check(ObjectTemplate.hasPetEpo(improved),
                "A chanil certificate carrying stat 3ac must restore an EPO pet");
        check(!ObjectTemplate.hasPetEpo(new HashMap<>()),
                "A certificate without stat 3ac must restore an ordinary pet");
        check(!ObjectTemplate.hasPetEpo(null),
                "Missing certificate stats must not invent an EPO improvement");
    }

    private static void chanilCopiesSoulStatsIndependently() {
        ObjectTemplate petTemplate = new ObjectTemplate(997_400, "",
                "Soul-eater pet test", Constant.ITEM_TYPE_FAMILIER,
                1, 1, 0, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(petTemplate);
        GameObject pet = new GameObject(997_401, petTemplate.getId(), 1,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        pet.getSoulStat().put(42, 3);

        Map<Integer, Integer> copied = ObjectTemplate.copyPetSoulStats(pet);
        check(copied.size() == 1 && copied.get(42) == 3,
                "Chanil conversion must preserve soul-eater counters");
        copied.put(42, 4);
        check(pet.getSoulStat().get(42) == 3,
                "A converted pet must not share its soul-stat map with the source");
        pet.getSoulStat().put(43, 1);
        check(!copied.containsKey(43),
                "Later source mutations must not leak into the converted object");
        check(ObjectTemplate.copyPetSoulStats(null).isEmpty(),
                "Missing source objects must produce empty soul stats");
    }

    private static void petEpoMarkerSurvivesObjectPersistence() {
        int atouinTemplateId = 7_714;
        int atouinCertificateId = 8_708;
        int atouinEpoPotionId = 10_750;
        ObjectTemplate atouin = new ObjectTemplate(atouinTemplateId, "",
                "Atouin", Constant.ITEM_TYPE_FAMILIER, 1, 1, 0, 0,
                "", "", 0, 0, 0, 0);
        ObjectTemplate certificate = new ObjectTemplate(atouinCertificateId,
                "", "Certificat de Mise en Chanil : Atouin",
                Constant.ITEM_TYPE_CERTIFICAT_CHANIL, 1, 1, 0, 0,
                "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(atouin);
        World.world.addObjTemplate(certificate);

        Pet definition = new Pet(atouinTemplateId, 3, "5,72",
                "b2|41#49#62;70|63#64", 80, 10, 8_020,
                atouinEpoPotionId, "10");
        World.world.addPets(definition);
        check(definition.getEpo() == atouinEpoPotionId,
                "Local Atouin data must associate potion 10750 with pet 7714");

        int liveGuid = 997_200;
        GameObject livePet = new GameObject(liveGuid, atouinTemplateId, 1,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        livePet.getTxtStat().put(Constant.STATS_PETS_EPO, "1");
        livePet.getTxtStat().put(Constant.STATS_PETS_REPAS, "2");
        World.world.addGameObject(livePet);
        World.world.addPetsEntry(new PetEntry(liveGuid, atouinTemplateId,
                1_728_888_777_666L, 0, 10, 0, true));

        check(livePet.encodeStats().contains("3ac#1##1"),
                "The live item packet must expose the active EPO marker");
        String liveSave = livePet.parseToSave();
        GameObject reloadedPet = new GameObject(997_201, atouinTemplateId, 1,
                Constant.ITEM_POS_NO_EQUIPED, liveSave, 0);
        check("1".equals(reloadedPet.getTxtStat()
                        .get(Constant.STATS_PETS_EPO)),
                "The EPO marker must keep its value across pet object persistence");
        check("2".equals(reloadedPet.getTxtStat()
                        .get(Constant.STATS_PETS_REPAS)),
                "The last-meal stat must survive alongside EPO on a live pet");

        Map<Integer, String> certificateStats = new HashMap<>();
        certificateStats.put(Constant.STATS_PETS_EPO, "1");
        certificateStats.put(Constant.STATS_PETS_REPAS, "2");
        GameObject chanilCertificate = new GameObject(997_202,
                atouinCertificateId, 1, Constant.ITEM_POS_NO_EQUIPED,
                new Stats(), new ArrayList<>(), new HashMap<>(),
                certificateStats, 0);
        String certificateSave = chanilCertificate.parseToSave();
        GameObject reloadedCertificate = new GameObject(997_203,
                atouinCertificateId, 1, Constant.ITEM_POS_NO_EQUIPED,
                certificateSave, 0);
        check("1".equals(reloadedCertificate.getTxtStat()
                        .get(Constant.STATS_PETS_EPO)),
                "The EPO marker must survive chanil certificate persistence");
        check("2".equals(reloadedCertificate.getTxtStat()
                        .get(Constant.STATS_PETS_REPAS)),
                "The last-meal stat must survive alongside EPO on a certificate");
    }

    private static void cloneMetadataIsIndependent(ObjectTemplate template) {
        GameObject source = new GameObject(993_103, template.getId(), 2,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        source.getTxtStat().put(Constant.STATS_SIGNATURE, "source");
        source.getSoulStat().put(42, 1);
        source.getSpellStats().add("119#1#0#1");

        GameObject clone = source.getClone(1, false);
        check(clone != null, "A detached clone must be created without persistence");
        clone.getTxtStat().put(Constant.STATS_SIGNATURE, "clone");
        clone.getSoulStat().put(42, 2);
        clone.getSpellStats().add("120#2#0#2");

        check("source".equals(source.getTxtStat().get(Constant.STATS_SIGNATURE)),
                "Changing clone text stats must not mutate the source stack");
        check(source.getSoulStat().get(42) == 1,
                "Changing clone soul stats must not mutate the source stack");
        check(source.getSpellStats().size() == 1
                        && source.getSpellStats().contains("119#1#0#1"),
                "Changing clone spell stats must not mutate the source stack");

        source.getTxtStat().put(Constant.STATS_SIGNATURE, "source-updated");
        source.getSoulStat().put(42, 3);
        source.getSpellStats().clear();
        check("clone".equals(clone.getTxtStat().get(Constant.STATS_SIGNATURE)),
                "Changing source text stats must not mutate its clone");
        check(clone.getSoulStat().get(42) == 2,
                "Changing source soul stats must not mutate its clone");
        check(clone.getSpellStats().size() == 2
                        && clone.getSpellStats().contains("119#1#0#1")
                        && clone.getSpellStats().contains("120#2#0#2"),
                "Changing source spell stats must not mutate its clone");
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
