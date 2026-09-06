package org.starloco.locos.game;

import org.starloco.locos.area.map.GameCase;
import org.starloco.locos.area.map.GameMap;
import org.starloco.locos.area.map.MapData;
import org.starloco.locos.area.map.ScriptMapData;
import org.starloco.locos.client.Player;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.action.type.ScenarioActionData;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScenarioActionDataChecks {
    private static final String SCENARIO_DATE = "7001010000";

    private ScenarioActionDataChecks() {
    }

    public static void run() throws Exception {
        completionPacketsAreParsedStrictly();
        malformedPacketsAndInvalidActionsAreIgnored();
        clientCoordinatesCannotChooseTheDestination();
        completionCannotBeForgedBeforeTheScenarioEnds();
        theWholeActionListIdIsValidated();
        changingMapCancelsTheReward();
        chainedScenariosCannotBeCompletedByAnImmediateReplay();
        smithScenarioDefinitionsFailClosed();
    }

    private static void completionPacketsAreParsedStrictly() {
        check(Integer.valueOf(1).equals(
                        GameClient.parseTutorialCompletionActionListId("TV1")),
                "A tutorial completion without position must be accepted");
        check(Integer.valueOf(12).equals(
                        GameClient.parseTutorialCompletionActionListId(
                                "TV12|251|7")),
                "The complete action-list id must be parsed");

        String[] invalidPackets = {
                null, "T", "TV", "TVx", "TV01", "TV-1", "TV1|251",
                "TV1|cell|7", "TV1|251|8", "TV1|251|7|extra",
                "TV2147483648", "TV1|12345678901|7"
        };
        for (String packet : invalidPackets) {
            check(GameClient.parseTutorialCompletionActionListId(packet) == null,
                    "Malformed tutorial packet must be rejected: " + packet);
        }
    }

    private static void malformedPacketsAndInvalidActionsAreIgnored()
            throws Exception {
        Context context = context(2163, 237, 3);
        AtomicInteger callbacks = new AtomicInteger();
        ScenarioActionData scenario = scenario(context, 200, 0,
                (player, succeed) -> callbacks.incrementAndGet());
        ExchangeAction<?> active = activate(context.player, scenario);

        context.client.parsePacket("TV");
        context.client.parsePacket("TV1|251");
        context.client.parsePacket("TV1|251|9");
        check(callbacks.get() == 0 && context.player.getExchangeAction() == active,
                "Malformed packets must leave the active scenario untouched");

        ExchangeAction<String> invalid = new ExchangeAction<>(
                ExchangeAction.IN_SCENARIO, "invalid-data");
        context.player.setExchangeAction(invalid);
        context.client.parsePacket("TV1|251|7");
        check(context.player.getExchangeAction() == invalid,
                "An invalid scenario payload must not cause a class cast failure");
    }

    private static void clientCoordinatesCannotChooseTheDestination()
            throws Exception {
        Context context = context(2163, 237, 3);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicBoolean succeeded = new AtomicBoolean();
        ScenarioActionData scenario = scenario(context, 94, 4_000,
                (player, success) -> {
                    callbacks.incrementAndGet();
                    succeeded.set(success);
                });
        activate(context.player, scenario);

        context.client.parsePacket("TV1|399|2");

        check(callbacks.get() == 1 && succeeded.get(),
                "The expected terminal action must complete the scenario once");
        check(context.player.getCurCell() == context.map.getCase(251),
                "Scenario 94 must use its server-owned terminal cell");
        check(context.player.get_orientation() == 7,
                "Scenario 94 must use its server-owned terminal orientation");
        check(!context.map.getCase(237).getPlayers().contains(context.player)
                        && context.map.getCase(251).getPlayers().contains(context.player),
                "The map actor indexes must follow the authoritative destination");
        check(context.player.getExchangeAction() == null
                        && !context.player.getDoAction(),
                "A completed scenario must restore the previous action state");
    }

    private static void completionCannotBeForgedBeforeTheScenarioEnds()
            throws Exception {
        Context context = context(2163, 181, 3);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicBoolean succeeded = new AtomicBoolean(true);
        ScenarioActionData scenario = scenario(context, 95, 0,
                (player, success) -> {
                    callbacks.incrementAndGet();
                    succeeded.set(success);
                });
        ExchangeAction<?> active = activate(context.player, scenario);

        context.client.parsePacket("TV1|195|7");
        check(callbacks.get() == 0 && context.player.getExchangeAction() == active,
                "An immediate forged completion must be ignored");
        check(context.player.getCurCell() == context.map.getCase(181)
                        && context.player.get_orientation() == 3,
                "An early completion must not move the player");
        check(context.player.getDoAction(),
                "Movement must stay locked while the scenario is active");

        context.client.parsePacket("TV0|399|2");
        check(callbacks.get() == 1 && !succeeded.get(),
                "The cancellation action must finish the scenario as a failure");
        check(context.player.getCurCell() == context.map.getCase(181)
                        && context.player.get_orientation() == 3,
                "Cancelling must never apply client-provided coordinates");
    }

    private static void theWholeActionListIdIsValidated() throws Exception {
        Context context = context(2163, 237, 3);
        AtomicInteger callbacks = new AtomicInteger();
        ScenarioActionData scenario = scenario(context, 200, 0,
                (player, succeed) -> callbacks.incrementAndGet());
        ExchangeAction<?> active = activate(context.player, scenario);

        context.client.parsePacket("TV10|399|2");
        check(callbacks.get() == 0 && context.player.getExchangeAction() == active,
                "Action list 10 must not be mistaken for successful action list 1");

        context.client.parsePacket("TV1|399|2");
        check(callbacks.get() == 1 && context.player.getExchangeAction() == null,
                "Only the server-configured completion action must be accepted");
        check(context.player.getCurCell() == context.map.getCase(237)
                        && context.player.get_orientation() == 3,
                "Unknown scenarios must ignore all client position fields");
    }

    private static void changingMapCancelsTheReward() throws Exception {
        Context context = context(2163, 241, 3);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ScenarioActionData scenario = scenario(context, 96, 4_000,
                (player, succeed) -> {
                    if (succeed) successes.incrementAndGet();
                    else failures.incrementAndGet();
                });
        activate(context.player, scenario);

        GameMap otherMap = map(9999, 400);
        context.player.setCurMap(otherMap);
        context.player.setCurCell(otherMap.getCase(10));
        context.client.parsePacket("TV1|255|7");

        check(successes.get() == 0 && failures.get() == 1,
                "Leaving the scenario map must fail without granting its reward");
        check(context.player.getCurMap() == otherMap
                        && context.player.getCurCell() == otherMap.getCase(10),
                "A stale scenario must not move a player back across maps");
    }

    private static void chainedScenariosCannotBeCompletedByAnImmediateReplay()
            throws Exception {
        Context context = context(2163, 297, 3);
        AtomicInteger firstCallbacks = new AtomicInteger();
        AtomicInteger secondSuccesses = new AtomicInteger();
        AtomicInteger secondCallbacks = new AtomicInteger();
        ScenarioActionData first = scenario(context, 97, 4_000,
                (player, succeed) -> {
                    firstCallbacks.incrementAndGet();
                    ScenarioActionData second = ScenarioActionData.create(
                            player.getExchangeAction(), player, 101,
                            SCENARIO_DATE, (ignored, secondSucceeded) -> {
                                secondCallbacks.incrementAndGet();
                                if (secondSucceeded)
                                    secondSuccesses.incrementAndGet();
                            });
                    activate(player, second);
                });
        activate(context.player, first);

        context.client.parsePacket("TV1|311|7");
        context.client.parsePacket("TV1|311|7");

        check(firstCallbacks.get() == 1,
                "A scenario callback must be consumed exactly once");
        check(secondCallbacks.get() == 0 && secondSuccesses.get() == 0,
                "A replay of the first completion must not finish a fresh scenario");
        check(!first.onCompletion(context.player, 1),
                "A consumed scenario object must reject direct replay too");

        context.client.parsePacket("TV0");
        check(secondCallbacks.get() == 1 && secondSuccesses.get() == 0,
                "The fresh scenario must remain independently cancellable");
    }

    private static void smithScenarioDefinitionsFailClosed() throws Exception {
        Context wrongMap = context(9999, 10, 3);
        check(ScenarioActionData.create(null, wrongMap.player, 94,
                        SCENARIO_DATE, (player, succeed) -> { }) == null,
                "Smith scenarios must only start on their configured map");

        Context smithMap = context(2163, 237, 3);
        check(ScenarioActionData.create(null, smithMap.player, 94,
                        "unexpected-version", (player, succeed) -> { }) == null,
                "A mismatched Smith scenario resource must fail closed");
    }

    private static ScenarioActionData scenario(Context context, int scenarioId,
                                               long elapsedMillis,
                                               java.util.function.BiConsumer<Player, Boolean> callback)
            throws Exception {
        ScenarioActionData scenario = ScenarioActionData.create(null,
                context.player, scenarioId, SCENARIO_DATE, callback);
        check(scenario != null, "The test scenario must be valid");
        if (elapsedMillis > 0) {
            setLong(unsafe(), scenario, ScenarioActionData.class,
                    "startedAtNanos", System.nanoTime()
                            - TimeUnit.MILLISECONDS.toNanos(elapsedMillis));
        }
        return scenario;
    }

    private static ExchangeAction<?> activate(Player player,
                                              ScenarioActionData scenario) {
        check(scenario != null, "Cannot activate a missing scenario");
        ExchangeAction<ScenarioActionData> action = new ExchangeAction<>(
                ExchangeAction.IN_SCENARIO, scenario);
        player.setExchangeAction(action);
        return action;
    }

    private static Context context(int mapId, int cellId, int orientation)
            throws Exception {
        Unsafe unsafe = unsafe();
        GameMap map = map(mapId, 400);
        Player player = (Player) unsafe.allocateInstance(Player.class);
        player.setCurMap(map);
        player.setCurCell(map.getCase(cellId));
        player.set_orientation(orientation);
        map.getCase(cellId).addPlayer(player);

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        setObject(unsafe, client, GameClient.class, "player", player);
        return new Context(map, player, client);
    }

    private static GameMap map(int mapId, int cellCount) throws Exception {
        Unsafe unsafe = unsafe();
        ScriptMapData data = (ScriptMapData) unsafe.allocateInstance(
                ScriptMapData.class);
        setInt(unsafe, data, MapData.class, "id", mapId);

        GameMap map = (GameMap) unsafe.allocateInstance(GameMap.class);
        setObject(unsafe, map, GameMap.class, "data", data);
        setObject(unsafe, map, GameMap.class, "actors",
                new ConcurrentHashMap<Integer, java.util.Set<org.starloco.locos.area.map.Actor>>());

        List<GameCase> cases = new ArrayList<>(cellCount);
        for (int cellId = 0; cellId < cellCount; cellId++)
            cases.add(new GameCase(map, cellId));
        setObject(unsafe, map, GameMap.class, "cases", cases);
        return map;
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

    private static void setLong(Unsafe unsafe, Object target, Class<?> owner,
                                String fieldName, long value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putLong(target, unsafe.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class Context {
        private final GameMap map;
        private final Player player;
        private final GameClient client;

        private Context(GameMap map, Player player, GameClient client) {
            this.map = map;
            this.player = player;
            this.client = client;
        }
    }
}
