package org.starloco.locos.game.world;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WorldExtraMonsterChecks {
    private WorldExtraMonsterChecks() {
    }

    public static void run() {
        placementIdsAreParsedSafely();
        missingPlacementMapsAreSkippedWithoutAnError();
    }

    private static void placementIdsAreParsedSafely() {
        check(World.parseExtraMonsterAreaIds(null).isEmpty(),
                "A null placement must produce no area id");
        check(World.parseExtraMonsterAreaIds(" , \t,").isEmpty(),
                "Blank placement tokens must be ignored");
        check(
                World.parseExtraMonsterAreaIds(" 200,invalid,-1, 33,200,2147483648,0 ")
                        .equals(Arrays.asList(200, 33, 0)),
                "Placement ids must be trimmed, validated and de-duplicated"
        );
    }

    private static void missingPlacementMapsAreSkippedWithoutAnError() {
        World isolatedWorld = new World();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        isolatedWorld.logger.addAppender(appender);

        try {
            List<?> maps = isolatedWorld.findEligibleExtraMonsterMaps("invalid,-1", " 200,missing ");
            check(maps.equals(Collections.emptyList()),
                    "Unknown area and sub-area ids must not produce a candidate map");

            isolatedWorld.addExtraMonster(2432, "invalid,-1", " 200,missing ", -1);
            isolatedWorld.loadExtraMonster();

            check(!isolatedWorld.getExtraMonsterOnMap().containsKey(2432),
                    "Monster 2432 must remain unplaced when sub-area 200 has no eligible map");
            check(appender.list.stream().noneMatch(event -> event.getLevel().equals(Level.ERROR)),
                    "Missing placement maps must not be logged as an error");
            check(appender.list.stream().anyMatch(event ->
                            event.getLevel().equals(Level.WARN)
                                    && event.getFormattedMessage().contains("no eligible map")),
                    "Missing placement maps must be explained by a warning");
        } finally {
            isolatedWorld.logger.detachAppender(appender);
            appender.stop();
            isolatedWorld.scheduler.shutdownNow();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
