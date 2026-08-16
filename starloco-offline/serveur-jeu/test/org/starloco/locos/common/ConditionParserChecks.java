package org.starloco.locos.common;

import org.starloco.locos.client.Account;
import org.starloco.locos.client.Player;
import org.starloco.locos.game.world.World;
import org.starloco.locos.quest.QuestProgress;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class ConditionParserChecks {
    private static final int QUEST_ID = 996_701;
    private static final int ACCOUNT_ID = -996_701;
    private static final int PLAYER_ID = -996_702;

    private ConditionParserChecks() {
    }

    public static void run() throws Exception {
        equalityMeansQuestIsOngoing();
        inequalityMeansQuestIsNotOngoing();
        questDropsRequireAnOngoingQuest();
        malformedQuestConditionsAreRejected();
        nullPlayersAreRejected();
    }

    private static void equalityMeansQuestIsOngoing() throws Exception {
        ConditionParser parser = new ConditionParser();

        check(!parser.validConditions(playerWithQuest(QuestState.ABSENT),
                        "QE=" + QUEST_ID),
                "QE= must reject a quest that has not been started");
        check(parser.validConditions(playerWithQuest(QuestState.ONGOING),
                        "QE=" + QUEST_ID),
                "QE= must accept an ongoing quest");
        check(!parser.validConditions(playerWithQuest(QuestState.FINISHED),
                        "QE=" + QUEST_ID),
                "QE= must reject a finished quest");

        check(!parser.validConditions(playerWithQuest(QuestState.ABSENT),
                        "QE==" + QUEST_ID),
                "QE== must reject a quest that has not been started");
        check(parser.validConditions(playerWithQuest(QuestState.ONGOING),
                        "QE==" + QUEST_ID),
                "QE== must accept an ongoing quest");
        check(!parser.validConditions(playerWithQuest(QuestState.FINISHED),
                        "QE==" + QUEST_ID),
                "QE== must reject a finished quest");
    }

    private static void inequalityMeansQuestIsNotOngoing() throws Exception {
        ConditionParser parser = new ConditionParser();

        check(parser.validConditions(playerWithQuest(QuestState.ABSENT),
                        "QE!" + QUEST_ID),
                "QE! must accept a quest that has not been started");
        check(!parser.validConditions(playerWithQuest(QuestState.ONGOING),
                        "QE!" + QUEST_ID),
                "QE! must reject an ongoing quest");
        check(parser.validConditions(playerWithQuest(QuestState.FINISHED),
                        "QE!" + QUEST_ID),
                "QE! must accept a finished quest");

        check(parser.validConditions(playerWithQuest(QuestState.ABSENT),
                        "QE!=" + QUEST_ID),
                "QE!= must accept a quest that has not been started");
        check(!parser.validConditions(playerWithQuest(QuestState.ONGOING),
                        "QE!=" + QUEST_ID),
                "QE!= must reject an ongoing quest");
        check(parser.validConditions(playerWithQuest(QuestState.FINISHED),
                        "QE!=" + QUEST_ID),
                "QE!= must accept a finished quest");
    }

    private static void questDropsRequireAnOngoingQuest() throws Exception {
        ConditionParser parser = new ConditionParser();
        String fightDropCondition = "QE=" + QUEST_ID;

        check(!parser.validConditions(playerWithQuest(QuestState.ABSENT),
                        fightDropCondition),
                "A quest drop must not be available before its quest starts");
        check(parser.validConditions(playerWithQuest(QuestState.ONGOING),
                        fightDropCondition),
                "A quest drop must be available while its quest is ongoing");
        check(!parser.validConditions(playerWithQuest(QuestState.FINISHED),
                        fightDropCondition),
                "A quest drop must not remain available after its quest finishes");
    }

    private static void malformedQuestConditionsAreRejected() throws Exception {
        ConditionParser parser = new ConditionParser();
        Player player = playerWithQuest(QuestState.ONGOING);

        check(!parser.validConditions(player, "QE!!" + QUEST_ID),
                "QE!! must be rejected");
        check(!parser.validConditions(player, "QE=!" + QUEST_ID),
                "QE=! must be rejected");
        check(!parser.validConditions(player, "QE===" + QUEST_ID),
                "QE=== must be rejected");
        check(!parser.validConditions(player, "QE="),
                "QE without an id must be rejected");
        check(!parser.validConditions(player, "QE=not-a-number"),
                "QE with a non-numeric id must be rejected");
    }

    private static void nullPlayersAreRejected() {
        ConditionParser parser = new ConditionParser();
        check(!parser.validConditions(null, "QE=" + QUEST_ID),
                "QE must reject a null player");
    }

    private static Player playerWithQuest(QuestState state)
            throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        Account account = (Account) unsafe.allocateInstance(Account.class);
        setInt(unsafe, account, Account.class, "id", ACCOUNT_ID);
        setObject(unsafe, account, Account.class, "questsProgression",
                new HashMap<Integer, Map<Integer, QuestProgress>>());
        World.world.addAccount(account);

        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(unsafe, player, Player.class, "id", PLAYER_ID);
        setInt(unsafe, player, Player.class, "_accID", ACCOUNT_ID);

        if (state != QuestState.ABSENT) {
            QuestProgress progress = new QuestProgress(ACCOUNT_ID, PLAYER_ID,
                    QUEST_ID, 1);
            if (state == QuestState.FINISHED)
                progress.markFinished();
            account.addQuestProgression(progress);
        }
        return player;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setObject(Unsafe unsafe, Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static void setInt(Unsafe unsafe, Object target, Class<?> owner,
                               String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putInt(target, unsafe.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private enum QuestState {
        ABSENT,
        ONGOING,
        FINISHED
    }
}
