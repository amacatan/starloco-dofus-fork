package org.starloco.locos.script;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.classdump.luna.Table;
import org.classdump.luna.impl.DefaultTable;
import org.starloco.locos.client.Player;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

public final class EventHandlersChecks {
    private EventHandlersChecks() {
    }

    public static void run() throws Exception {
        missingAndInvalidPlayerHandlersAreIgnored();
    }

    private static void missingAndInvalidPlayerHandlersAreIgnored() throws Exception {
        EventHandlers handlers = new EventHandlers(null);
        Player player = (Player) unsafe().allocateInstance(Player.class);
        Logger logger = (Logger) ScriptVM.logger;
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handlers.onDialog(player, 1, 0);
            handlers.onMapEnter(player);
            handlers.onSkillUse(player, 2, 3);
            handlers.onFightEnd(
                    player, 0, true, new DefaultTable(), new DefaultTable());
            check(handlers.questInfo(player, 4, 5) == null,
                    "A missing quest handler must return no quest metadata");
            handlers.onDocQuestHref(player, 6, 7);

            Table players = (Table) handlers.rawget("players");
            players.rawset("onDialog", true);
            handlers.onDialog(player, 1, 0);

            List<String> expectedHandlers = List.of(
                    "onDialog", "onMapEnter", "onSkillUse", "onFightEnd",
                    "onQuestStatusRequest", "onDocQuestHref");
            for (String name : expectedHandlers) {
                check(appender.list.stream().anyMatch(event ->
                                event.getFormattedMessage().contains(
                                        "Handlers.players." + name)),
                        "A missing or invalid handler must be logged with its name: " + name);
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
