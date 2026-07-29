package org.starloco.locos.database.data.login;

import com.zaxxer.hikari.HikariDataSource;
import org.starloco.locos.game.world.World;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class AccountDataChecks {
    private AccountDataChecks() {
    }

    public static void run() {
        check(AccountData.minimumRequiredPoints(-30) == 30, "A debit must require the debited balance");
        check(AccountData.minimumRequiredPoints(30) == 0, "A credit must not require an existing balance");

        boolean overflowRejected = false;
        try {
            AccountData.minimumRequiredPoints(Long.MIN_VALUE);
        } catch (ArithmeticException expected) {
            overflowRejected = true;
        }
        check(overflowRejected, "Long.MIN_VALUE must not overflow the debit guard");

        JdbcScenario success = new JdbcScenario(1, 70);
        AccountData successDao = new AccountData(new StubDataSource(success.connection()));
        World.Couple<Long, Boolean> successResult = successDao.modPoints(42, -30);
        check(successResult.second && successResult.first == 70,
                "A successful points update must return the new balance");
        check(success.commitCount == 1 && success.rollbackCount == 0,
                "A successful points update must commit exactly once");
        check(Long.valueOf(-30).equals(success.updateParameters.get(1)),
                "The delta must be bound as parameter 1");
        check(Integer.valueOf(42).equals(success.updateParameters.get(2)),
                "The account id must be bound as parameter 2");
        check(Long.valueOf(30).equals(success.updateParameters.get(3)),
                "The minimum balance must be bound as parameter 3");
        check(Integer.valueOf(42).equals(success.selectParameters.get(1)),
                "The balance query must target the same account");

        JdbcScenario insufficient = new JdbcScenario(0, 0);
        AccountData insufficientDao = new AccountData(new StubDataSource(insufficient.connection()));
        World.Couple<Long, Boolean> insufficientResult = insufficientDao.modPoints(42, -100);
        check(!insufficientResult.second, "An insufficient balance must be rejected");
        check(insufficient.commitCount == 0 && insufficient.rollbackCount == 1,
                "An insufficient balance must roll back");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class StubDataSource extends HikariDataSource {
        private final Connection connection;

        private StubDataSource(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return connection;
        }
    }

    private static final class JdbcScenario implements InvocationHandler {
        private final int updateCount;
        private final long selectedPoints;
        private final Map<Integer, Object> updateParameters = new HashMap<>();
        private final Map<Integer, Object> selectParameters = new HashMap<>();
        private int commitCount;
        private int rollbackCount;
        private boolean resultRead;

        private JdbcScenario(int updateCount, long selectedPoints) {
            this.updateCount = updateCount;
            this.selectedPoints = selectedPoints;
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("prepareStatement".equals(name)) {
                String sql = (String) args[0];
                if (AccountData.UPDATE_POINTS_SQL.equals(sql)) {
                    return statement(true);
                }
                if (AccountData.SELECT_POINTS_SQL.equals(sql)) {
                    return statement(false);
                }
                throw new AssertionError("Unexpected SQL: " + sql);
            }
            if ("setAutoCommit".equals(name) || "close".equals(name)) {
                return null;
            }
            if ("commit".equals(name)) {
                commitCount++;
                return null;
            }
            if ("rollback".equals(name)) {
                rollbackCount++;
                return null;
            }
            if ("isClosed".equals(name)) {
                return false;
            }
            if ("toString".equals(name)) {
                return "JdbcScenarioConnection";
            }
            return defaultValue(method.getReturnType());
        }

        private PreparedStatement statement(final boolean update) {
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("setLong".equals(name) || "setInt".equals(name)) {
                    (update ? updateParameters : selectParameters).put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeUpdate".equals(name)) {
                    return updateCount;
                }
                if ("executeQuery".equals(name)) {
                    return resultSet();
                }
                if ("close".equals(name)) {
                    return null;
                }
                if ("toString".equals(name)) {
                    return update ? "UpdatePointsStatement" : "SelectPointsStatement";
                }
                return defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    handler
            );
        }

        private ResultSet resultSet() {
            resultRead = false;
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("next".equals(name)) {
                    if (resultRead) {
                        return false;
                    }
                    resultRead = true;
                    return true;
                }
                if ("getLong".equals(name)) {
                    check(Integer.valueOf(1).equals(args[0]), "The JDBC result column index must start at 1");
                    return selectedPoints;
                }
                if ("close".equals(name)) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    handler
            );
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            return null;
        }
    }
}
