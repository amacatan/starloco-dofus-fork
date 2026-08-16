package org.starloco.locos.database.data.login;

import com.zaxxer.hikari.HikariDataSource;
import org.starloco.locos.entity.pet.PetEntry;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

public final class PetDataChecks {
    private PetDataChecks() {
    }

    public static void run() {
        insertPersistsTheCompletePetState();
    }

    private static void insertPersistsTheCompletePetState() {
        int templateId = 997_100;
        int objectId = 997_101;
        long lastEatDate = 1_728_888_777_666L;
        ObjectTemplate template = new ObjectTemplate(templateId, "",
                "Pet persistence test", Constant.ITEM_TYPE_FAMILIER,
                1, 1, 0, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);
        World.world.addGameObject(new GameObject(objectId, templateId, 1,
                Constant.ITEM_POS_NO_EQUIPED, "", 0));

        PetEntry entry = new PetEntry(objectId, templateId, lastEatDate,
                3, 7, -2, true);
        JdbcCapture capture = new JdbcCapture();
        PetData data = new PetData(new StubDataSource(capture.connection()));

        check(data.insert(entry), "Pet insertion must report success");
        check(capture.executeCount == 1,
                "Pet insertion must execute exactly one SQL statement");
        check(Integer.valueOf(objectId).equals(capture.parameters.get(1)),
                "Pet insertion must persist the object id");
        check(Integer.valueOf(templateId).equals(capture.parameters.get(2)),
                "Pet insertion must persist the template id");
        check(Long.valueOf(lastEatDate).equals(capture.parameters.get(3)),
                "Pet insertion must persist the last meal date");
        check(Integer.valueOf(3).equals(capture.parameters.get(4)),
                "Pet insertion must preserve the meal counter");
        check(Integer.valueOf(7).equals(capture.parameters.get(5)),
                "Pet insertion must preserve the current health");
        check(Integer.valueOf(-2).equals(capture.parameters.get(6)),
                "Pet insertion must preserve corpulence");
        check(Integer.valueOf(1).equals(capture.parameters.get(7)),
                "Pet insertion must preserve the EPO flag");
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

    private static final class JdbcCapture implements InvocationHandler {
        private final Map<Integer, Object> parameters = new HashMap<>();
        private int executeCount;

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("prepareStatement".equals(name))
                return statement((Connection) proxy, (String) args[0]);
            if ("isClosed".equals(name))
                return false;
            if ("close".equals(name))
                return null;
            return defaultValue(method.getReturnType());
        }

        private PreparedStatement statement(Connection connection, String sql) {
            check(sql.contains("INSERT INTO `world_pets`"),
                    "Pet insertion must target world_pets");
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("setInt".equals(name) || "setLong".equals(name)) {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeUpdate".equals(name)) {
                    executeCount++;
                    return 1;
                }
                if ("isClosed".equals(name))
                    return false;
                if ("getConnection".equals(name))
                    return connection;
                if ("clearParameters".equals(name) || "close".equals(name))
                    return null;
                return defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, handler);
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
}
