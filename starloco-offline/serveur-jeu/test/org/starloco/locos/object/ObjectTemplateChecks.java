package org.starloco.locos.object;

import org.starloco.locos.kernel.Constant;
import org.starloco.locos.game.world.World;

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
