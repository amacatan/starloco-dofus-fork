package org.starloco.locos.database.data.login;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang.NotImplementedException;
import org.starloco.locos.client.Account;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.FunctionDAO;
import org.starloco.locos.database.data.game.BankData;
import org.starloco.locos.database.data.game.QuestProgressData;
import org.starloco.locos.game.world.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountData extends FunctionDAO<Account> {
    static final String UPDATE_POINTS_SQL =
            "UPDATE `world_accounts` SET `points` = `points` + ? WHERE `guid` = ? AND `points` >= ?";
    static final String SELECT_POINTS_SQL =
            "SELECT `points` FROM `world_accounts` WHERE `guid` = ?";

    public AccountData(HikariDataSource source) {
        super(source, "world_accounts");
    }
    
    @Override
    public void loadFully() {
        throw new NotImplementedException("It's not allowed to load all accounts");
    }

    @Override
    public Account load(int id) {
        try {
            return getData("SELECT * FROM " + getTableName() + " WHERE guid = " + id, result -> {
                if(!result.next()) return null;
                Account acc = new Account(result.getInt("guid"), result.getString("account").toLowerCase(), result.getString("pseudo"), result.getString("reponse"), (result.getInt("banned") == 1), result.getString("lastIP"), result.getString("lastConnectionDate"), result.getString("friends"), result.getString("enemy"), result.getInt("points"), result.getLong("subscribe"), result.getLong("muteTime"), result.getString("mutePseudo"), result.getString("lastVoteIP"), result.getString("heurevote"));
                try {
                    acc.setVip(result.getInt("vip"));
                } catch(Exception ignored) {}
                World.world.addAccount(acc);

                // Load account specific data
                DatabaseManager.get(BankData.class).load(acc.getId());
                DatabaseManager.get(QuestProgressData.class).load(acc.getId());

                // Ensure players are loaded too
                DatabaseManager.get(PlayerData.class).loadByAccountId(acc.getId());

                return acc;
            });
        } catch (Exception e) {
            super.sendError(e);
            return null;
        }
    }

    @Override
    public boolean insert(Account entity) {
        throw new NotImplementedException("It's not allowed to insert account directly server-side");
    }

    @Override
    public void delete(Account entity) {
        throw new NotImplementedException("It's not allowed to delete account directly server-side");
    }

    //TODO: Account update all data
    @Override
    public void update(Account entity) {
        PreparedStatement statement = null;
        try {
            statement = getPreparedStatement("UPDATE " + getTableName() + " SET banned = '"
                    + (entity.isBanned() ? 1 : 0) + "', friends = '"
                    + entity.parseFriendListToDB() + "', enemy = '"
                    + entity.parseEnemyListToDB() + "', muteTime = '"
                    + entity.getMuteTime() + "', mutePseudo = '"
                    + entity.getMutePseudo() + "' WHERE guid = '" + entity.getId()
                    + "'");
            execute(statement);
        } catch (Exception e) {
            super.sendError(e);
        } finally {
            close(statement);
        }
    }

    @Override
    public Class<?> getReferencedClass() {
        return AccountData.class;
    }

    public long getSubscribe(int id) {
        try {
            return getData("SELECT guid, subscribe FROM " + getTableName() + " WHERE guid = " + id, result ->
                    result.next() ? result.getLong("subscribe") : 0);
        } catch (Exception e) {
            super.sendError(e);
        }
        return 0;
    }

    public void updateVoteAll() {
        try {
            getData("SELECT guid, heurevote, lastVoteIP FROM " + getTableName() + ";", result -> {
                while (result.next()) {
                    Account a = World.world.ensureAccountLoaded(result.getInt("guid"));
                    if (a != null) {
                        a.updateVote(result.getString("heurevote"), result.getString("lastVoteIP"));
                    }
                }
            });
        } catch (SQLException e) {
            super.sendError(e);
        }
    }

    public void updateLastConnection(Account compte) {
        PreparedStatement p = null;
        try {
            p = getPreparedStatement("UPDATE " + getTableName() + " SET `lastIP` = ?, `lastConnectionDate` = ? WHERE `guid` = ?");
            p.setString(1, compte.getCurrentIp());
            p.setString(2, compte.getLastConnectionDate());
            p.setInt(3, compte.getId());
            execute(p);
        } catch (SQLException e) {
            super.sendError(e);
        } finally {
            close(p);
        }
    }

    public void setLogged(int id, int logged) {
        PreparedStatement p = null;
        try {
            p = getPreparedStatement("UPDATE " + getTableName() + " SET `logged` = ? WHERE `guid` = ?;");
            if(p != null) {
                p.setInt(1, logged);
                p.setInt(2, id);
                execute(p);
            }
        } catch (SQLException e) {
            super.sendError(e);
        } finally {
            close(p);
        }
    }

    public boolean needsReload(int id) {
        try {
            return getData(
                    "SELECT `reload_needed` FROM " + getTableName() + " WHERE `guid` = " + id + " LIMIT 1",
                    result -> result.next() && result.getInt("reload_needed") == 1
            );
        } catch (SQLException e) {
            super.sendError(e);
            return false;
        }
    }

    public void clearReloadNeeded(int id) {
        PreparedStatement statement = null;
        try {
            statement = getPreparedStatement(
                    "UPDATE " + getTableName() + " SET `reload_needed` = 0 WHERE `guid` = ? AND `reload_needed` = 1"
            );
            statement.setInt(1, id);
            execute(statement);
        } catch (SQLException e) {
            super.sendError(e);
        } finally {
            close(statement);
        }
    }

    public void updateBannedTime(Account acc, long time) {
        PreparedStatement statement = null;
        try {
            statement = getPreparedStatement("UPDATE " + getTableName() + " SET banned = '" + (acc.isBanned() ? 1 : 0) + "', bannedTime = '" + time + "' WHERE guid = '" + acc.getId() + "'");
            execute(statement);
        } catch (Exception e) {
            super.sendError(e);
        } finally {
            close(statement);
        }
    }

    public int loadPoints(String user) {
        return this.loadPointsWithoutUsersDb(user);
    }

    public int loadPointsWithoutUsersDb(String user) {
        try {
            return getData("SELECT * FROM " + getTableName() + " WHERE `account` LIKE '" + user + "'", result ->
                    result.next() ? result.getInt("points") : 0);
        } catch (SQLException e) {
            super.sendError(e);
        }
        return 0;
    }

    public World.Couple<Long, Boolean> modPoints(int id, long points) {
        final long minimumPoints;
        try {
            minimumPoints = minimumRequiredPoints(points);
        } catch (ArithmeticException e) {
            return new World.Couple<>(0L, false);
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement(UPDATE_POINTS_SQL)) {
                    update.setLong(1, points);
                    update.setInt(2, id);
                    update.setLong(3, minimumPoints);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return new World.Couple<>(0L, false);
                    }
                }

                long newValue;
                try (PreparedStatement select = connection.prepareStatement(SELECT_POINTS_SQL)) {
                    select.setInt(1, id);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return new World.Couple<>(0L, false);
                        }
                        newValue = result.getLong(1);
                    }
                }

                connection.commit();
                return new World.Couple<>(newValue, true);
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        } catch (SQLException e) {
            super.sendError(e);
            return new World.Couple<>(0L, false);
        }
    }

    static long minimumRequiredPoints(long points) {
        return points < 0 ? Math.negateExact(points) : 0;
    }
}
