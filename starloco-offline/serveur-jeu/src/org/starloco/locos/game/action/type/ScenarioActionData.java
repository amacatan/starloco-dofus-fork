package org.starloco.locos.game.action.type;

import org.starloco.locos.area.map.GameCase;
import org.starloco.locos.area.map.GameMap;
import org.starloco.locos.client.Player;
import org.starloco.locos.game.action.ExchangeAction;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class ScenarioActionData implements ActionDataInterface {
    private static final int CANCELLED_ACTION_LIST_ID = 0;
    private static final int DEFAULT_COMPLETION_ACTION_LIST_ID = 1;
    private static final int SMITH_MAP_ID = 2163;
    private static final String SMITH_SCENARIO_DATE = "7001010000";

    private final ExchangeAction<?> source;
    private final BiConsumer<Player,Boolean> onCompleted;
    private final int completionActionListId;
    private final long minimumDurationNanos;
    private final long startedAtNanos;
    private final GameMap expectedMap;
    private final GameCase terminalCell;
    private final int terminalOrientation;
    private boolean completed;

    public ScenarioActionData(ExchangeAction<?> source, BiConsumer<Player,Boolean> onCompleted) {
        this(source, onCompleted, DEFAULT_COMPLETION_ACTION_LIST_ID, 0,
                null, null, -1, System.nanoTime());
    }

    ScenarioActionData(ExchangeAction<?> source,
                       BiConsumer<Player, Boolean> onCompleted,
                       int completionActionListId, long minimumDurationMillis,
                       GameMap expectedMap, GameCase terminalCell,
                       int terminalOrientation, long startedAtNanos) {
        if (completionActionListId <= CANCELLED_ACTION_LIST_ID)
            throw new IllegalArgumentException("A scenario completion action must be positive");
        if (minimumDurationMillis < 0)
            throw new IllegalArgumentException("A scenario duration cannot be negative");
        if ((terminalCell == null) != (terminalOrientation < 0))
            throw new IllegalArgumentException("A scenario destination needs both a cell and an orientation");
        if (terminalOrientation > 7)
            throw new IllegalArgumentException("A scenario orientation must be between 0 and 7");

        this.source = source;
        this.onCompleted = onCompleted;
        this.completionActionListId = completionActionListId;
        this.minimumDurationNanos = TimeUnit.MILLISECONDS.toNanos(minimumDurationMillis);
        this.startedAtNanos = startedAtNanos;
        this.expectedMap = expectedMap;
        this.terminalCell = terminalCell;
        this.terminalOrientation = terminalOrientation;
    }

    public static ScenarioActionData create(ExchangeAction<?> source,
                                            Player player, int scenarioId,
                                            String date,
                                            BiConsumer<Player, Boolean> onCompleted) {
        return create(source, player, scenarioId, date, onCompleted,
                System.nanoTime());
    }

    static ScenarioActionData create(ExchangeAction<?> source, Player player,
                                     int scenarioId, String date,
                                     BiConsumer<Player, Boolean> onCompleted,
                                     long startedAtNanos) {
        ScenarioPolicy policy = ScenarioPolicy.forScenario(scenarioId, date);
        if (policy == null || player == null || player.getCurMap() == null)
            return null;

        GameMap map = player.getCurMap();
        if (policy.requiredMapId >= 0 && map.getId() != policy.requiredMapId)
            return null;

        GameCase destination = null;
        if (policy.terminalCellId >= 0) {
            destination = map.getCase(policy.terminalCellId);
            if (destination == null)
                return null;
        }

        return new ScenarioActionData(source, onCompleted,
                policy.completionActionListId, policy.minimumDurationMillis,
                map, destination, policy.terminalOrientation,
                startedAtNanos);
    }

    public boolean onCompletion(Player player, int actionListId) {
        if (player == null)
            return false;

        final boolean succeed;
        synchronized (player) {
            ExchangeAction<?> activeAction = player.getExchangeAction();
            if (completed || activeAction == null
                    || activeAction.getType() != ExchangeAction.IN_SCENARIO
                    || activeAction.getValue() != this)
                return false;

            if (actionListId == CANCELLED_ACTION_LIST_ID) {
                succeed = false;
            } else {
                if (actionListId != completionActionListId)
                    return false;

                if (expectedMap != null && player.getCurMap() != expectedMap) {
                    succeed = false;
                } else {
                    long elapsedNanos = System.nanoTime() - startedAtNanos;
                    if (elapsedNanos < minimumDurationNanos)
                        return false;
                    succeed = true;
                }
            }

            completed = true;
            if (succeed && terminalCell != null) {
                GameCase currentCell = player.getCurCell();
                if (currentCell != null && currentCell != terminalCell)
                    currentCell.removePlayer(player);
                terminalCell.addPlayer(player);
                player.setCurCell(terminalCell);
                player.set_orientation(terminalOrientation);
            }
            player.setExchangeAction(source);
        }

        if (onCompleted != null)
            onCompleted.accept(player, succeed);
        return true;
    }

    private static final class ScenarioPolicy {
        private final int requiredMapId;
        private final int completionActionListId;
        private final long minimumDurationMillis;
        private final int terminalCellId;
        private final int terminalOrientation;

        private ScenarioPolicy(int requiredMapId, long minimumDurationMillis,
                               int terminalCellId, int terminalOrientation) {
            this.requiredMapId = requiredMapId;
            this.completionActionListId = DEFAULT_COMPLETION_ACTION_LIST_ID;
            this.minimumDurationMillis = minimumDurationMillis;
            this.terminalCellId = terminalCellId;
            this.terminalOrientation = terminalOrientation;
        }

        private static ScenarioPolicy forScenario(int scenarioId, String date) {
            switch (scenarioId) {
                case 94:
                    return smithPolicy(date, 3_100, 251, 7);
                case 95:
                    return smithPolicy(date, 3_100, 195, 7);
                case 96:
                    return smithPolicy(date, 3_100, 255, 7);
                case 97:
                    return smithPolicy(date, 3_100, 311, 7);
                case 98:
                case 99:
                case 100:
                case 101:
                    return smithPolicy(date, 2_800, -1, -1);
                default:
                    // Unknown server-authored scenarios keep their position and
                    // only accept the conventional END [1] action.
                    return new ScenarioPolicy(-1, 0, -1, -1);
            }
        }

        private static ScenarioPolicy smithPolicy(String date,
                                                  long minimumDurationMillis,
                                                  int terminalCellId,
                                                  int terminalOrientation) {
            if (!SMITH_SCENARIO_DATE.equals(date))
                return null;
            return new ScenarioPolicy(SMITH_MAP_ID, minimumDurationMillis,
                    terminalCellId, terminalOrientation);
        }
    }
}
