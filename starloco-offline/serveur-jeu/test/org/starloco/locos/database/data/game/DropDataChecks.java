package org.starloco.locos.database.data.game;

public final class DropDataChecks {
    private DropDataChecks() {
    }

    public static void run() {
        check(DropData.isLoadableDrop(true, true, 180),
                "A normal drop with an existing item and monster must be loaded");
        check(DropData.isLoadableDrop(true, false, 0),
                "A global drop with an existing item must be loaded");
        check(!DropData.isLoadableDrop(false, true, 180),
                "The orphan item 11009 must not be loaded for monster 180");
        check(!DropData.isLoadableDrop(false, true, 2403),
                "The orphan item 12878 must not be loaded for monster 2403");
        check(!DropData.isLoadableDrop(false, false, 0),
                "A global drop without an item template must not be spread to every monster");
        check(!DropData.isLoadableDrop(true, false, 180),
                "A normal drop without an existing monster must not be loaded");
        check("-1".equals(DropData.normalizeAction("1", 0)),
                "The current data set's action 1 level 0 rows must be regular drops");
        check("1".equals(DropData.normalizeAction("1", 1)),
                "Hunter meat with a positive required level must remain hunter-only");
        check("-2".equals(DropData.normalizeAction("-2", -1)),
                "Special drop actions must not be rewritten");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
