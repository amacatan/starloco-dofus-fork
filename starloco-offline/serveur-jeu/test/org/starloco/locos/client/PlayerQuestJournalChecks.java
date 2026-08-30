package org.starloco.locos.client;

import org.starloco.locos.game.GameClient;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.quest.QuestInfo;
import org.starloco.locos.quest.QuestProgress;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerQuestJournalChecks {
    private static final int KNOWN_QUEST_ID = 997_801;
    private static final int ORPHAN_QUEST_ID = 997_802;
    private static final int SECOND_ORPHAN_QUEST_ID = 997_803;

    private PlayerQuestJournalChecks() {
    }

    public static void run() throws Exception {
        questListSkipsUnknownDefinitions();
        questStatusIgnoresUnknownRequests();
        malformedQuestPacketsAreIgnored();
    }

    private static void questListSkipsUnknownDefinitions() throws Exception {
        Fixture fixture = fixture(1);
        fixture.account.addQuestProgression(new QuestProgress(
                fixture.accountId, fixture.playerId, KNOWN_QUEST_ID, 321));
        fixture.account.addQuestProgression(new QuestProgress(
                fixture.accountId, fixture.playerId, ORPHAN_QUEST_ID, 1));
        QuestProgress accountOrphan = new QuestProgress(
                fixture.accountId, QuestProgress.NO_PLAYER_ID,
                SECOND_ORPHAN_QUEST_ID, 0);
        accountOrphan.markFinished();
        fixture.account.addQuestProgression(accountOrphan);

        String encoded = fixture.player.encodeQuestList(progress ->
                progress.questId == KNOWN_QUEST_ID ? questInfo() : null);

        check(("QL+" + KNOWN_QUEST_ID + ";0;;1;0").equals(encoded),
                "The quest list must retain valid quests and skip player/account orphans");

        Fixture onlyOrphans = fixture(2);
        onlyOrphans.account.addQuestProgression(new QuestProgress(
                onlyOrphans.accountId, onlyOrphans.playerId,
                ORPHAN_QUEST_ID, 1));
        check("QL+".equals(onlyOrphans.player.encodeQuestList(progress -> null)),
                "A journal containing only unknown quests must still be well formed");
    }

    private static void questStatusIgnoresUnknownRequests() throws Exception {
        Fixture fixture = fixture(3);
        QuestProgress known = new QuestProgress(
                fixture.accountId, fixture.playerId, KNOWN_QUEST_ID, 321);
        known.completeObjective(501);
        fixture.account.addQuestProgression(known);
        fixture.account.addQuestProgression(new QuestProgress(
                fixture.accountId, fixture.playerId, ORPHAN_QUEST_ID, 1));

        int initialWrites = fixture.session.writeCount();
        fixture.player.sendQuestStatus(SECOND_ORPHAN_QUEST_ID, progress -> {
            throw new AssertionError("A missing progression must not resolve quest metadata");
        });
        fixture.player.sendQuestStatus(ORPHAN_QUEST_ID, progress -> null);
        check(fixture.session.writeCount() == initialWrites,
                "Unknown quest status requests must not emit a packet");

        fixture.player.sendQuestStatus(KNOWN_QUEST_ID, progress -> questInfo());
        check(fixture.session.writeCount() == initialWrites + 1,
                "A valid quest status request must still emit one packet");
        String expected = "QS" + KNOWN_QUEST_ID
                + ";1;0|321|501,1;502,0|320|322|1234";
        check(expected.equals(fixture.session.writes().get(initialWrites)),
                "Valid QS metadata must preserve the existing protocol format");
    }

    private static void malformedQuestPacketsAreIgnored() throws Exception {
        Config.encryption = false;
        Config.debug = false;

        Fixture fixture = fixture(4);
        setObject(fixture.client, GameClient.class, "player", fixture.player);
        int initialWrites = fixture.session.writeCount();

        fixture.client.parsePacket("QS");
        fixture.client.parsePacket("QSabc");
        fixture.client.parsePacket("QS999999999999999999999");
        fixture.client.parsePacket("QS0");
        fixture.client.parsePacket("QS-1");
        fixture.client.parsePacket("QS" + ORPHAN_QUEST_ID);

        check(fixture.session.writeCount() == initialWrites,
                "Malformed and unknown QS packets must be ignored silently");

        IoSessionStub anonymousSession = new IoSessionStub();
        GameClient anonymousClient = new GameClient(anonymousSession.session());
        anonymousClient.parsePacket("QL");
        anonymousClient.parsePacket("QS" + KNOWN_QUEST_ID);
        check(anonymousSession.writeCount() == 1,
                "Quest packets received before player selection must be ignored");
    }

    private static QuestInfo questInfo() {
        return new QuestInfo(List.of(501, 502), 320, 322, 1234,
                true, false);
    }

    private static Fixture fixture(int offset) throws ReflectiveOperationException {
        int accountId = -997_810 - offset;
        int playerId = -997_820 - offset;
        Unsafe unsafe = unsafe();

        Account account = (Account) unsafe.allocateInstance(Account.class);
        setInt(unsafe, account, Account.class, "id", accountId);
        setObject(unsafe, account, Account.class, "questsProgression",
                new HashMap<Integer, Map<Integer, QuestProgress>>());
        World.world.addAccount(account);

        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(unsafe, player, Player.class, "id", playerId);
        setInt(unsafe, player, Player.class, "_accID", accountId);

        IoSessionStub session = new IoSessionStub();
        GameClient client = new GameClient(session.session());
        account.setGameClient(client);
        return new Fixture(accountId, playerId, account, player, client, session);
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

    private static void setObject(Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setInt(Unsafe unsafe, Object target, Class<?> owner,
                               String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putInt(target, unsafe.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        private final int accountId;
        private final int playerId;
        private final Account account;
        private final Player player;
        private final GameClient client;
        private final IoSessionStub session;

        private Fixture(int accountId, int playerId, Account account,
                        Player player, GameClient client, IoSessionStub session) {
            this.accountId = accountId;
            this.playerId = playerId;
            this.account = account;
            this.player = player;
            this.client = client;
            this.session = session;
        }
    }
}
