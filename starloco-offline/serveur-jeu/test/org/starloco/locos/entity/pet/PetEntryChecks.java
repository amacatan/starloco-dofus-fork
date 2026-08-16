package org.starloco.locos.entity.pet;

import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;

public final class PetEntryChecks {
    private PetEntryChecks() {
    }

    public static void run() {
        epoApplicationIsAtomicAndIdempotent();
    }

    private static void epoApplicationIsAtomicAndIdempotent() {
        int templateId = 997_300;
        int objectId = 997_301;
        ObjectTemplate template = new ObjectTemplate(templateId, "",
                "EPO application test", Constant.ITEM_TYPE_FAMILIER,
                1, 1, 0, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);
        World.world.addPets(new Pet(templateId, 0, "", "", 80, 1,
                0, 10_750, ""));
        GameObject pet = new GameObject(objectId, templateId, 1,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        World.world.addGameObject(pet);
        PetEntry entry = new PetEntry(objectId, templateId,
                1_728_888_777_666L, 0, 10, 0, false);

        check(entry.getMaxStat() == 80,
                "An ordinary pet must retain its configured stat cap");
        check(entry.applyEpo(pet),
                "The first matching EPO application must be accepted");
        check(entry.getIsEupeoh(),
                "EPO application must update the authoritative PetEntry state");
        check(entry.getMaxStat() == 88,
                "EPO must raise an Atouin-like cap from 80 to exactly 88");
        check("1".equals(pet.getTxtStat().get(Constant.STATS_PETS_EPO)),
                "EPO application must update the pet object's display marker");

        check(!entry.applyEpo(pet),
                "An already improved pet must reject a second EPO application");
        check("1".equals(pet.getTxtStat().get(Constant.STATS_PETS_EPO)),
                "A rejected duplicate must not corrupt the EPO marker");

        GameObject unrelated = new GameObject(997_302, templateId, 1,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        check(!entry.applyEpo(unrelated),
                "A PetEntry must reject an EPO application to another object");
        check(!unrelated.getTxtStat().containsKey(Constant.STATS_PETS_EPO),
                "A rejected mismatched application must not mutate the object");

        int historicalId = 997_303;
        GameObject historical = new GameObject(historicalId, templateId, 1,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        historical.getTxtStat().put(Constant.STATS_PETS_EPO, "1");
        World.world.addGameObject(historical);
        PetEntry historicalEntry = new PetEntry(historicalId, templateId,
                1_728_888_777_666L, 0, 10, 0, false);
        check(historicalEntry.applyEpo(historical),
                "A historical marker with a false SQL flag must be repaired");
        check(historicalEntry.getIsEupeoh()
                        && historicalEntry.getMaxStat() == 88,
                "Repairing historical EPO data must restore its gameplay effect");
        check(!historicalEntry.applyEpo(historical),
                "A repaired historical pet must then reject duplicate EPO");
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
