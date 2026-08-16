package org.starloco.locos.fight.spells;

public final class SpellEffectChecks {

    private SpellEffectChecks() {
    }

    public static void run() {
        percentagesMultiplyBeforeDividing();
        healingIsCappedIndependentlyForEachTarget();
    }

    private static void percentagesMultiplyBeforeDividing() {
        check(SpellEffect.percentageOf(150, 10) == 15,
                "Ten percent of 150 hit points must be 15");
        check(SpellEffect.percentageOf(99, 10) == 9,
                "Percentage damage must keep only the final integer remainder");
        check(SpellEffect.percentageOf(Integer.MAX_VALUE, 100) == Integer.MAX_VALUE,
                "Percentage calculation must not overflow during multiplication");
        check(SpellEffect.percentageOf(Integer.MAX_VALUE, 200) == Integer.MAX_VALUE,
                "Percentage calculation must saturate positive integer overflow");
        check(SpellEffect.percentageOf(Integer.MIN_VALUE, 200) == Integer.MIN_VALUE,
                "Percentage calculation must saturate negative integer overflow");
    }

    private static void healingIsCappedIndependentlyForEachTarget() {
        int requestedHealing = SpellEffect.percentageOf(150, 10);

        int firstTargetHealing = SpellEffect.cappedHealing(requestedHealing, 149, 150);
        int secondTargetHealing = SpellEffect.cappedHealing(requestedHealing, 100, 150);

        check(firstTargetHealing == 1,
                "A nearly full target must only receive its missing hit point");
        check(secondTargetHealing == 15,
                "The first target's cap must not reduce healing for following targets");
        check(SpellEffect.cappedHealing(requestedHealing, 151, 150) == 0,
                "Healing must not become damage when a target is already over maximum life");
        check(SpellEffect.cappedHealing(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE)
                        == Integer.MAX_VALUE,
                "Missing hit points must not overflow while healing is capped");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
