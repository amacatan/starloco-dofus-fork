package org.starloco.locos.database.data.game;

public final class ExtraMonsterDataChecks {
    private ExtraMonsterDataChecks() {
    }

    public static void run() {
        check(!ExtraMonsterData.hasUsablePlacement("", "-1"),
                "The no-placement sentinel must be ignored");
        check(!ExtraMonsterData.hasUsablePlacement(null, null),
                "Missing placement columns must be ignored");
        check(!ExtraMonsterData.hasUsablePlacement("invalid", " , "),
                "Malformed placement columns must be ignored");
        check(ExtraMonsterData.hasUsablePlacement("", "200"),
                "A valid sub-area must be loaded");
        check(ExtraMonsterData.hasUsablePlacement("5,-1", ""),
                "A valid super-area amongst sentinels must be loaded");
        check(ExtraMonsterData.hasUsablePlacement("", "8,71,"),
                "A trailing comma must not invalidate otherwise usable sub-areas");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
