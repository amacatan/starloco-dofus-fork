package org.starloco.locos.database.data.login;

import com.zaxxer.hikari.HikariDataSource;
import org.starloco.locos.client.Player;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ObjectDataSecurePaymentChecks {
    private ObjectDataSecurePaymentChecks() {
    }

    public static void run() throws Exception {
        transactionSharesTheObjectPersistenceMonitor();
        inventoryEncodingIsDeterministic();
        sourceLocksAreDeterministic();
        committedBatchMutatesMemoryOnlyAfterCommit();
        nthSourceDebitFailureRollsBackTheCompleteBatch();
        nthOwnershipWriteFailureRollsBackTheCompleteBatch();
    }

    private static void transactionSharesTheObjectPersistenceMonitor()
            throws NoSuchMethodException {
        Method method = ObjectData.class.getDeclaredMethod(
                "transferSecureCraftPaymentsAtomically",
                Player.class, Player.class, Map.class);
        check(Modifier.isSynchronized(method.getModifiers()),
                "Secure payment SQL must share the ObjectData persistence monitor");

        String[] legacyMutations = {"insert", "delete", "deleteSafely",
                "update", "updateSafely"};
        for (String name : legacyMutations) {
            Method mutation = ObjectData.class.getDeclaredMethod(
                    name, GameObject.class);
            check(Modifier.isSynchronized(mutation.getModifiers()),
                    "Object persistence mutation must share the secure-payment monitor: "
                            + name);
        }
    }

    private static void inventoryEncodingIsDeterministic() {
        Set<Integer> guids = new TreeSet<>(
                Arrays.asList(9, 2, -1, 5));
        check("2|5|9|".equals(ObjectData.encodeInventory(guids)),
                "Durable secure-payment ownership must use the player inventory format");
    }

    private static void sourceLocksAreDeterministic() {
        check(("SELECT `id`, `template`, `quantity`, `position`"
                        + " FROM `world_objects` WHERE `id` IN (?, ?, ?)"
                        + " ORDER BY `id` FOR UPDATE").equals(
                        ObjectData.securePaymentLockObjectsSql(3)),
                "Every secure payment source must be locked in deterministic GUID order");
    }

    private static void committedBatchMutatesMemoryOnlyAfterCommit()
            throws Exception {
        int templateId = 995_100;
        ObjectTemplate template = addTemplate(templateId);
        GameObject full = object(995_101, template, 3);
        GameObject split = object(995_102, template, 5);
        Player payer = player(995_103, full, split);
        Player crafter = player(995_104);

        JdbcScenario scenario = new JdbcScenario(payer.getId(), crafter.getId());
        scenario.addSource(full);
        scenario.addSource(split);
        ObjectData data = new ObjectData(
                new StubDataSource(scenario.connection()));

        Map<Integer, Integer> payments = new LinkedHashMap<>();
        payments.put(full.getGuid(), 3);
        payments.put(split.getGuid(), 2);
        ObjectData.SecurePaymentBatch batch =
                data.transferSecureCraftPaymentsAtomically(
                        payer, crafter, payments);

        check(batch != null && scenario.commitCount == 1
                        && scenario.rollbackCount == 0,
                "A valid multi-object payment must commit exactly once");
        check(payer.getItems().get(full.getGuid()) == full
                        && payer.getItems().get(split.getGuid()) == split
                        && split.getQuantity() == 5
                        && crafter.getItems().isEmpty(),
                "SQL commit must precede every live inventory mutation");
        check("995102|".equals(scenario.inventoryByPlayer.get(payer.getId()))
                        && "995101|996001|".equals(
                        scenario.inventoryByPlayer.get(crafter.getId())),
                "Full and split stacks must have coherent durable ownership after one commit");

        batch.applyToMemory();
        check(!payer.getItems().containsKey(full.getGuid())
                        && payer.getItems().get(split.getGuid()) == split
                        && split.getQuantity() == 3
                        && crafter.getItems().get(full.getGuid()) == full,
                "Applying a committed batch must move the full GUID and debit the split source");

        GameObject clone = null;
        for (ObjectData.SecurePaymentTransfer transfer : batch.getTransfers()) {
            if (!transfer.isCompleteStack())
                clone = transfer.getTransferred();
        }
        check(clone != null && clone.getGuid() == 996_001
                        && clone.getQuantity() == 2
                        && crafter.getItems().get(clone.getGuid()) == clone
                        && World.world.getGameObject(clone.getGuid()) == clone,
                "A split clone must enter memory and World only after SQL commit");
    }

    private static void nthSourceDebitFailureRollsBackTheCompleteBatch()
            throws Exception {
        int templateId = 995_200;
        ObjectTemplate template = addTemplate(templateId);
        GameObject first = object(995_201, template, 5);
        GameObject second = object(995_202, template, 6);
        Player payer = player(995_203, first, second);
        Player crafter = player(995_204);

        JdbcScenario scenario = new JdbcScenario(payer.getId(), crafter.getId());
        scenario.addSource(first);
        scenario.addSource(second);
        scenario.failDebitAt = 2;
        ObjectData data = new ObjectData(
                new StubDataSource(scenario.connection()));

        Map<Integer, Integer> payments = new LinkedHashMap<>();
        payments.put(first.getGuid(), 2);
        payments.put(second.getGuid(), 3);
        ObjectData.SecurePaymentBatch batch =
                data.transferSecureCraftPaymentsAtomically(
                        payer, crafter, payments);

        check(batch == null && scenario.commitCount == 0
                        && scenario.rollbackCount == 1,
                "Failure on the Nth source debit must roll back the whole payment transaction");
        check(scenario.insertCalls == 2 && scenario.debitCalls == 2,
                "The rollback check must inject failure after earlier batch writes");
        check(payer.getItems().get(first.getGuid()) == first
                        && payer.getItems().get(second.getGuid()) == second
                        && first.getQuantity() == 5
                        && second.getQuantity() == 6
                        && crafter.getItems().isEmpty(),
                "A rolled-back Nth payment failure must expose no partial memory transfer");
        check(scenario.inventoryByPlayer.isEmpty(),
                "Ownership lists must not be written after a failed source debit");
    }

    private static void nthOwnershipWriteFailureRollsBackTheCompleteBatch()
            throws Exception {
        int templateId = 995_300;
        ObjectTemplate template = addTemplate(templateId);
        GameObject full = object(995_301, template, 2);
        GameObject split = object(995_302, template, 4);
        Player payer = player(995_303, full, split);
        Player crafter = player(995_304);

        JdbcScenario scenario = new JdbcScenario(payer.getId(), crafter.getId());
        scenario.addSource(full);
        scenario.addSource(split);
        scenario.failOwnerAt = 2;
        ObjectData data = new ObjectData(
                new StubDataSource(scenario.connection()));

        Map<Integer, Integer> payments = new LinkedHashMap<>();
        payments.put(full.getGuid(), 2);
        payments.put(split.getGuid(), 1);
        ObjectData.SecurePaymentBatch batch =
                data.transferSecureCraftPaymentsAtomically(
                        payer, crafter, payments);

        check(batch == null && scenario.commitCount == 0
                        && scenario.rollbackCount == 1
                        && scenario.ownerCalls == 2,
                "Failure on the Nth ownership write must roll back full and split payments");
        check(payer.getItems().get(full.getGuid()) == full
                        && payer.getItems().get(split.getGuid()) == split
                        && split.getQuantity() == 4
                        && crafter.getItems().isEmpty(),
                "A rolled-back ownership failure must expose no live transfer");
        check(scenario.inventoryByPlayer.isEmpty(),
                "A rolled-back ownership failure must persist no partial player list");
    }

    private static ObjectTemplate addTemplate(int id) {
        ObjectTemplate template = new ObjectTemplate(id, "",
                "Secure SQL payment test", 1, 1, 1, 0, 0,
                "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);
        return template;
    }

    private static GameObject object(int guid, ObjectTemplate template,
                                     int quantity) {
        return new GameObject(guid, template.getId(), quantity,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
    }

    private static Player player(int id, GameObject... items)
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
        unsafe.putObject(player, unsafe.objectFieldOffset(objectsField), inventory);
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
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

    private static final class JdbcScenario implements InvocationHandler {
        private final int payerId;
        private final int crafterId;
        private final Map<Integer, Map<String, Integer>> sources =
                new LinkedHashMap<>();
        private final Map<Integer, String> inventoryByPlayer =
                new HashMap<>();
        private final Map<Integer, String> pendingInventoryByPlayer =
                new HashMap<>();
        private int failDebitAt = -1;
        private int failOwnerAt = -1;
        private int insertCalls;
        private int debitCalls;
        private int ownerCalls;
        private int commitCount;
        private int rollbackCount;
        private int generatedGuid = 996_000;

        private JdbcScenario(int payerId, int crafterId) {
            this.payerId = payerId;
            this.crafterId = crafterId;
        }

        private void addSource(GameObject object) {
            Map<String, Integer> row = new HashMap<>();
            row.put("id", object.getGuid());
            row.put("template", object.getTemplate().getId());
            row.put("quantity", object.getQuantity());
            row.put("position", object.getPosition());
            sources.put(object.getGuid(), row);
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
                if (ObjectData.SECURE_PAYMENT_LOCK_PLAYERS_SQL.equals(sql))
                    return statement(Kind.PLAYERS);
                if (sql.startsWith("SELECT `id`, `template`, `quantity`, `position`"
                        + " FROM `world_objects`")
                        && sql.endsWith("ORDER BY `id` FOR UPDATE"))
                    return statement(Kind.OBJECTS);
                if (ObjectData.SECURE_PAYMENT_INSERT_OBJECT_SQL.equals(sql))
                    return statement(Kind.INSERT);
                if (ObjectData.SECURE_PAYMENT_UPDATE_SOURCE_SQL.equals(sql))
                    return statement(Kind.DEBIT);
                if (ObjectData.SECURE_PAYMENT_UPDATE_INVENTORY_SQL.equals(sql))
                    return statement(Kind.OWNER);
                throw new AssertionError("Unexpected SQL: " + sql);
            }
            if ("setAutoCommit".equals(name) || "close".equals(name))
                return null;
            if ("commit".equals(name)) {
                commitCount++;
                inventoryByPlayer.putAll(pendingInventoryByPlayer);
                pendingInventoryByPlayer.clear();
                return null;
            }
            if ("rollback".equals(name)) {
                rollbackCount++;
                pendingInventoryByPlayer.clear();
                return null;
            }
            if ("isClosed".equals(name))
                return false;
            if ("toString".equals(name))
                return "SecurePaymentJdbcScenario";
            return defaultValue(method.getReturnType());
        }

        private PreparedStatement statement(Kind kind) {
            Map<Integer, Object> parameters = new HashMap<>();
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("setInt".equals(name) || "setString".equals(name)) {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeQuery".equals(name)) {
                    if (kind == Kind.PLAYERS) {
                        return resultSet(Arrays.asList(
                                row("id", payerId), row("id", crafterId)));
                    }
                    if (kind == Kind.OBJECTS)
                        return resultSet(new ArrayList<>(sources.values()));
                    throw new AssertionError("Unexpected query kind: " + kind);
                }
                if ("executeUpdate".equals(name)) {
                    if (kind == Kind.INSERT) {
                        insertCalls++;
                        generatedGuid++;
                        return 1;
                    }
                    if (kind == Kind.DEBIT) {
                        debitCalls++;
                        return debitCalls == failDebitAt ? 0 : 1;
                    }
                    if (kind == Kind.OWNER) {
                        ownerCalls++;
                        if (ownerCalls == failOwnerAt)
                            return 0;
                        pendingInventoryByPlayer.put((Integer) parameters.get(2),
                                (String) parameters.get(1));
                        return 1;
                    }
                    throw new AssertionError("Unexpected update kind: " + kind);
                }
                if ("getGeneratedKeys".equals(name)) {
                    return resultSet(Arrays.asList(row("1", generatedGuid)));
                }
                if ("close".equals(name))
                    return null;
                if ("toString".equals(name))
                    return "SecurePaymentStatement(" + kind + ")";
                return defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, handler);
        }

        private ResultSet resultSet(List<Map<String, Integer>> rows) {
            final int[] cursor = {-1};
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("next".equals(name))
                    return ++cursor[0] < rows.size();
                if ("getInt".equals(name)) {
                    String key = args[0] instanceof Integer
                            ? String.valueOf(args[0]) : (String) args[0];
                    return rows.get(cursor[0]).get(key);
                }
                if ("close".equals(name))
                    return null;
                return defaultValue(method.getReturnType());
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, handler);
        }

        private static Map<String, Integer> row(String key, int value) {
            Map<String, Integer> row = new HashMap<>();
            row.put(key, value);
            return row;
        }

        private enum Kind {
            PLAYERS, OBJECTS, INSERT, DEBIT, OWNER
        }
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
}
