package org.starloco.locos.quest;

import org.starloco.locos.client.Account;
import org.starloco.locos.lang.LangEnum;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class DailyDungeonQuestChecks {

    private DailyDungeonQuestChecks() {}

    public static void run() {
        bossPoolIsValidAndComplete();
        multilingualNamesAreConfigured();
        currentBossResolutionMatchesToday();
        remainingTimeStringIsFormatted();
        accountDailyCompletionCheckWorks();
    }

    private static void bossPoolIsValidAndComplete() {
        List<DailyDungeonQuest.DailyBoss> pool = DailyDungeonQuest.getBossPool();
        check(pool != null && pool.size() >= 20, "Boss pool must have at least 20 bosses");

        for (DailyDungeonQuest.DailyBoss boss : pool) {
            check(boss.getMonsterId() > 0, "Boss ID must be positive: " + boss.getName());
            check(boss.getName() != null && !boss.getName().isEmpty(), "Boss name must not be empty");
            check(boss.getDungeon() != null && !boss.getDungeon().isEmpty(), "Boss dungeon must not be empty: " + boss.getName());
            check(boss.getLocation() != null && !boss.getLocation().isEmpty(), "Boss location must not be empty: " + boss.getName());
            check(boss.getLevel() > 0, "Boss level must be positive: " + boss.getName());
        }
    }

    private static void multilingualNamesAreConfigured() {
        List<DailyDungeonQuest.DailyBoss> pool = DailyDungeonQuest.getBossPool();
        LangEnum[] langs = {LangEnum.GERMAN, LangEnum.FRENCH, LangEnum.ENGLISH};
        boolean foundSponge = false;

        for (DailyDungeonQuest.DailyBoss boss : pool) {
            for (LangEnum lang : langs) {
                check(boss.getName(lang) != null && !boss.getName(lang).isEmpty(), "Localized name must not be empty for " + lang);
                check(boss.getDungeon(lang) != null && !boss.getDungeon(lang).isEmpty(), "Localized dungeon must not be empty for " + lang);
                check(boss.getLocation(lang) != null && !boss.getLocation(lang).isEmpty(), "Localized location must not be empty for " + lang);
            }
            if (boss.getMonsterId() == 928) {
                foundSponge = true;
                check("Spongemob Schwammtopf".equals(boss.getName(LangEnum.GERMAN)), "Boss 928 DE name mismatch");
                check("Versandetes Dungeon".equals(boss.getDungeon(LangEnum.GERMAN)), "Boss 928 DE dungeon mismatch");
                check("Mob l'Éponge".equals(boss.getName(LangEnum.FRENCH)), "Boss 928 FR name mismatch");
                check("Donjon Ensablé".equals(boss.getDungeon(LangEnum.FRENCH)), "Boss 928 FR dungeon mismatch");
                check("Sponge Mob".equals(boss.getName(LangEnum.ENGLISH)), "Boss 928 EN name mismatch");
                check("Sand Dungeon".equals(boss.getDungeon(LangEnum.ENGLISH)), "Boss 928 EN dungeon mismatch");
            }
        }
        check(foundSponge, "Boss 928 must exist in pool");
    }

    private static void currentBossResolutionMatchesToday() {
        DailyDungeonQuest.DailyBoss boss = DailyDungeonQuest.getCurrentBoss();
        check(boss != null, "Current daily boss must not be null");
        check(DailyDungeonQuest.isTodayBoss(boss.getMonsterId()), "isTodayBoss must return true for current boss");

        long epochDay = DailyDungeonQuest.getCurrentQuestDate().toEpochDay();
        int expectedIndex = DailyDungeonQuest.getShuffledIndex(epochDay, DailyDungeonQuest.getBossPool().size());
        DailyDungeonQuest.DailyBoss expectedBoss = DailyDungeonQuest.getBossPool().get(expectedIndex);
        check(boss.getMonsterId() == expectedBoss.getMonsterId(), "Boss ID must match calculated rotation");
    }

    private static void remainingTimeStringIsFormatted() {
        String remaining = DailyDungeonQuest.getRemainingTimeString();
        check(remaining != null && remaining.contains("Std.") && remaining.contains("Min."),
                "Remaining time must contain Std. and Min. but was: " + remaining);
    }

    private static void accountDailyCompletionCheckWorks() {
        Account acc = new Account(9999, "testdaily", "testpseudo", "ans", false,
                "127.0.0.1", "date", "", "", 100, 0, 0, "", "", "0");

        check(!acc.hasCompletedDailyBossToday(), "Fresh account must not have completed daily boss today");

        // Set to today's date
        String today = DailyDungeonQuest.getCurrentQuestDate().toString();
        acc.setDailyQuestDate(today);
        check(acc.hasCompletedDailyBossToday(), "Account with today's date must be completed");

        // Set to yesterday's date
        String yesterday = DailyDungeonQuest.getCurrentQuestDate().minusDays(2).toString();
        acc.setDailyQuestDate(yesterday);
        check(!acc.hasCompletedDailyBossToday(), "Account with 2-day-old date must not be completed today");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
