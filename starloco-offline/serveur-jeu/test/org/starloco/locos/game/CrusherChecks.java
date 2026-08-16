package org.starloco.locos.game;

import com.zaxxer.hikari.HikariDataSource;
import org.starloco.locos.client.Player;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.game.world.World;
import org.starloco.locos.game.world.World.Couple;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.job.maging.BreakingObject;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import org.starloco.locos.object.entity.Fragment;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CrusherChecks {
    private static final int EQUIPMENT_TEMPLATE = 996_101;
    private static final int RESOURCE_TEMPLATE = 996_102;
    private static final int SHIELD_TEMPLATE = 996_103;
    private static final int PET_TEMPLATE = 996_104;
    private static final int DOFUS_TEMPLATE = 996_105;
    private static final int ACTION_RESOURCE_TEMPLATE = 996_106;
    private static final int FRAGMENT_TEMPLATE = 8378;

    private CrusherChecks() {
    }

    public static void run() {
        installTemplate(EQUIPMENT_TEMPLATE, Constant.ITEM_TYPE_ANNEAU);
        installTemplate(RESOURCE_TEMPLATE, Constant.ITEM_TYPE_RESSOURCE);
        installTemplate(SHIELD_TEMPLATE, Constant.ITEM_TYPE_BOUCLIER);
        installTemplate(PET_TEMPLATE, Constant.ITEM_TYPE_FAMILIER);
        installTemplate(DOFUS_TEMPLATE, Constant.ITEM_TYPE_DOFUS);
        installTemplate(ACTION_RESOURCE_TEMPLATE, Constant.ITEM_TYPE_RESSOURCE,
                "4;1;1;50;50;0;0");
        installTemplate(FRAGMENT_TEMPLATE, Constant.ITEM_TYPE_RESSOURCE);

        onlyInventoryEquipmentCanEnterTheCrusher();
        validationRejectsTheWholeSelectionWhenOneLineIsStale();
        crusherSelectionSnapshotsCannotMutateInternalState();
        crusherRepetitionsAreBounded();
        crusherPersistenceUsesTheSharedSaveMonitor();
        crusherSqlFailureRollsBackWithoutMemoryMutation();
    }

    private static void onlyInventoryEquipmentCanEnterTheCrusher() {
        GameObject ring = item(996_201, EQUIPMENT_TEMPLATE, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject shield = item(996_202, SHIELD_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject resource = item(996_203, RESOURCE_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject equipped = item(996_204, EQUIPMENT_TEMPLATE, 1,
                Constant.ITEM_POS_ANNEAU1);
        GameObject attached = item(996_205, EQUIPMENT_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        attached.getTxtStat().put(Constant.STATS_OWNER_1, "crusher-check");
        GameObject living = item(996_206, EQUIPMENT_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        // This is the state immediately after loading from persistence: the
        // marker exists, but obvijevanPos/look have not been lazily populated
        // by stats encoding yet.
        living.getStats().addOneStat(973, 1);
        GameObject livingRuntime = item(996_210, EQUIPMENT_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        livingRuntime.setObvijevanPos(1);
        livingRuntime.setObvijevanLook(1);
        GameObject pet = item(996_207, PET_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject dofus = item(996_208, DOFUS_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject actionResource = item(996_209, ACTION_RESOURCE_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);

        check(GameClient.isBreakableCrusherObject(ring),
                "An unequipped equipment item must be breakable");
        check(GameClient.isBreakableCrusherObject(shield),
                "The historical shield type must remain breakable");
        check(!GameClient.isBreakableCrusherObject(resource),
                "Resources must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(equipped),
                "Equipped objects must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(attached),
                "Attached objects must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(living),
                "Persisted living-object stats must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(livingRuntime),
                "Runtime living-object metadata must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(pet),
                "Pets must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(dofus),
                "Dofus must not be accepted by the crusher");
        check(!GameClient.isBreakableCrusherObject(actionResource),
                "A PA cost must not turn a non-equipment resource into crusher input");
    }

    private static void validationRejectsTheWholeSelectionWhenOneLineIsStale() {
        GameObject first = item(996_211, EQUIPMENT_TEMPLATE, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject second = item(996_212, EQUIPMENT_TEMPLATE, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        Map<Integer, GameObject> inventory = new HashMap<>();
        inventory.put(first.getGuid(), first);
        inventory.put(second.getGuid(), second);

        ArrayList<Couple<Integer, Integer>> selection = new ArrayList<>();
        selection.add(new Couple<>(first.getGuid(), 1));
        selection.add(new Couple<>(second.getGuid(), 2));

        check(!GameClient.isValidCrusherSelection(inventory, selection),
                "A stale line must reject the complete crusher selection");
        check(first.getQuantity() == 2 && second.getQuantity() == 1,
                "Validation must happen before any selected object is consumed");

        selection.set(1, new Couple<>(second.getGuid(), 1));
        check(GameClient.isValidCrusherSelection(inventory, selection),
                "A complete available selection must validate");

        inventory.remove(second.getGuid());
        check(!GameClient.isValidCrusherSelection(inventory, selection),
                "A repeated crusher run must stop when a selected GUID vanished");
    }

    private static void crusherSelectionSnapshotsCannotMutateInternalState() {
        BreakingObject selection = new BreakingObject();
        selection.addObject(996_220, 2);

        selection.getObjects().clear();
        check(selection.size() == 1,
                "Crusher callers must not receive its mutable internal selection");
        check(selection.removeObject(996_220, 3) == 0 && selection.size() == 0,
                "Removing more than selected must report a completely removed line");
    }

    private static void crusherRepetitionsAreBounded() {
        check(GameClient.isValidBreakingRepeatCount(1)
                        && GameClient.isValidBreakingRepeatCount(10_000),
                "Crusher repetition must accept the documented safe range");
        check(!GameClient.isValidBreakingRepeatCount(0)
                        && !GameClient.isValidBreakingRepeatCount(-1)
                        && !GameClient.isValidBreakingRepeatCount(10_001)
                        && !GameClient.isValidBreakingRepeatCount(Integer.MAX_VALUE),
                "A forged crusher repeat count must not schedule an unbounded task");
    }

    private static void crusherPersistenceUsesTheSharedSaveMonitor() {
        try {
            Method transaction = ObjectData.class.getDeclaredMethod(
                    "persistCrusherExchangeAtomically", Player.class,
                    GameObject.class, List.class);
            check(Modifier.isSynchronized(transaction.getModifiers()),
                    "Crusher persistence must share ObjectData's autosave monitor");
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "Crusher persistence must expose one atomic DAO operation",
                    exception);
        }
    }

    private static void crusherSqlFailureRollsBackWithoutMemoryMutation() {
        try {
            GameObject partial = item(996_230, EQUIPMENT_TEMPLATE, 3,
                    Constant.ITEM_POS_NO_EQUIPED);
            GameObject complete = item(996_231, SHIELD_TEMPLATE, 1,
                    Constant.ITEM_POS_NO_EQUIPED);
            Player player = playerWithInventory(996_232, partial, complete);
            Fragment fragment = new Fragment(-1, "");
            ArrayList<ObjectData.CrusherDebit> debits = new ArrayList<>();
            debits.add(new ObjectData.CrusherDebit(partial, 2));
            debits.add(new ObjectData.CrusherDebit(complete, 1));

            CrusherJdbcScenario scenario = new CrusherJdbcScenario(
                    player.getId(), partial, complete);
            ObjectData data = new ObjectData(
                    new StubDataSource(scenario.connection()));
            ObjectData.CrusherBatch batch =
                    data.persistCrusherExchangeAtomically(
                            player, fragment, debits);

            check(batch == null,
                    "A failed source debit must reject the whole crusher batch");
            check(scenario.commitCount == 0 && scenario.rollbackCount == 1,
                    "A failed source debit must roll back exactly once");
            check(partial.getQuantity() == 3
                            && complete.getQuantity() == 1
                            && player.getItems().get(partial.getGuid()) == partial
                            && player.getItems().get(complete.getGuid()) == complete,
                    "A rolled-back crusher batch must not mutate live inventory");
            check(fragment.getGuid() == -1
                            && !player.getItems().containsValue(fragment),
                    "A rolled-back fragment must remain detached");
            check(!scenario.inventoryUpdateExecuted,
                    "Ownership must not be persisted after a failed source debit");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Unable to build the crusher transaction test player",
                    exception);
        }
    }

    private static void installTemplate(int id, int type) {
        installTemplate(id, type, "");
    }

    private static void installTemplate(int id, int type, String weaponInfo) {
        World.world.addObjTemplate(new ObjectTemplate(id, "", "Crusher " + id,
                type, 1, 1, 1, 0, "", weaponInfo, 0, 0, 0, 0));
    }

    private static GameObject item(int guid, int templateId, int quantity,
                                   int position) {
        return new GameObject(guid, templateId, quantity, position, "", 0);
    }

    private static Player playerWithInventory(int id, GameObject... items)
            throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);

        Field idField = Player.class.getDeclaredField("id");
        unsafe.putInt(player, unsafe.objectFieldOffset(idField), id);
        Map<Integer, GameObject> inventory = new HashMap<>();
        for (GameObject item : items)
            inventory.put(item.getGuid(), item);
        Field objectsField = Player.class.getDeclaredField("objects");
        unsafe.putObject(player, unsafe.objectFieldOffset(objectsField),
                inventory);
        return player;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive())
            return null;
        if (returnType == boolean.class)
            return false;
        if (returnType == char.class)
            return '\0';
        if (returnType == byte.class)
            return (byte) 0;
        if (returnType == short.class)
            return (short) 0;
        if (returnType == int.class)
            return 0;
        if (returnType == long.class)
            return 0L;
        if (returnType == float.class)
            return 0F;
        if (returnType == double.class)
            return 0D;
        return null;
    }

    private static final class StubDataSource extends HikariDataSource {
        private final Connection connection;

        private StubDataSource(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }
    }

    /**
     * Failure injection: the fragment INSERT and first partial debit succeed,
     * then the full-stack DELETE affects zero rows.  The DAO must roll back
     * before touching ownership or memory.
     */
    private static final class CrusherJdbcScenario
            implements InvocationHandler {
        private final int playerId;
        private final GameObject partial;
        private final GameObject complete;
        private int commitCount;
        private int rollbackCount;
        private boolean inventoryUpdateExecuted;

        private CrusherJdbcScenario(int playerId, GameObject partial,
                                    GameObject complete) {
            this.playerId = playerId;
            this.partial = partial;
            this.complete = complete;
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("prepareStatement".equals(name)) {
                String sql = (String) args[0];
                return statement(sql, args.length > 1);
            }
            if ("setAutoCommit".equals(name) || "close".equals(name))
                return null;
            if ("commit".equals(name)) {
                commitCount++;
                return null;
            }
            if ("rollback".equals(name)) {
                rollbackCount++;
                return null;
            }
            if ("isClosed".equals(name))
                return false;
            return defaultValue(method.getReturnType());
        }

        private PreparedStatement statement(String sql,
                                            boolean generatedKeys) {
            Map<Integer, Object> parameters = new HashMap<>();
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set")) {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeQuery".equals(name)) {
                    if (sql.contains("FROM `world_players`"))
                        return rows(new int[][]{{playerId, 0, 0, 0}});
                    if (sql.contains("FROM `world_objects`"))
                        return rows(new int[][]{
                                {partial.getGuid(), partial.getTemplate().getId(),
                                        partial.getQuantity(), partial.getPosition()},
                                {complete.getGuid(), complete.getTemplate().getId(),
                                        complete.getQuantity(), complete.getPosition()}
                        });
                    throw new AssertionError("Unexpected crusher SELECT: " + sql);
                }
                if ("executeUpdate".equals(name)) {
                    if (sql.startsWith("INSERT INTO `world_objects`"))
                        return 1;
                    if (sql.startsWith("UPDATE `world_objects`"))
                        return 1;
                    if (sql.startsWith("DELETE FROM `world_objects`"))
                        return 0;
                    if (sql.startsWith("UPDATE `world_players`")) {
                        inventoryUpdateExecuted = true;
                        return 1;
                    }
                    throw new AssertionError("Unexpected crusher write: " + sql);
                }
                if ("getGeneratedKeys".equals(name))
                    return generatedKey(996_299);
                if ("close".equals(name) || "clearParameters".equals(name))
                    return null;
                if ("getConnection".equals(name))
                    return connection();
                return defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, handler);
        }

        private ResultSet rows(int[][] rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int index = -1;

                @Override
                public Object invoke(Object proxy, Method method,
                                     Object[] args) {
                    String name = method.getName();
                    if ("next".equals(name))
                        return ++index < rows.length;
                    if ("getInt".equals(name)) {
                        String column = String.valueOf(args[0]);
                        if ("id".equals(column)) return rows[index][0];
                        if ("template".equals(column)) return rows[index][1];
                        if ("quantity".equals(column)) return rows[index][2];
                        if ("position".equals(column)) return rows[index][3];
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, handler);
        }

        private ResultSet generatedKey(int guid) {
            InvocationHandler handler = new InvocationHandler() {
                private boolean read;

                @Override
                public Object invoke(Object proxy, Method method,
                                     Object[] args) {
                    if ("next".equals(method.getName())) {
                        if (read) return false;
                        read = true;
                        return true;
                    }
                    if ("getInt".equals(method.getName())) return guid;
                    if ("close".equals(method.getName())) return null;
                    return defaultValue(method.getReturnType());
                }
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, handler);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
