package org.starloco.locos.database.data.game;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang.NotImplementedException;
import org.starloco.locos.database.data.FunctionDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DailyQuestData extends FunctionDAO<Object> {
    public DailyQuestData(HikariDataSource dataSource) {
        super(dataSource, "daily_quest_override");
    }

    public int getOverrideMonsterId() {
        return getOverrideMonsterId(org.starloco.locos.quest.DailyDungeonQuest.getCurrentQuestDate().toString());
    }

    public int getOverrideMonsterId(String questDate) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT monster_id FROM " + getTableName() + " WHERE quest_date = ?")) {
            stmt.setString(1, questDate);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("monster_id");
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    @Override
    public void loadFully() {}

    @Override
    public Object load(int id) {
        throw new NotImplementedException();
    }

    @Override
    public boolean insert(Object entity) {
        throw new NotImplementedException();
    }

    @Override
    public void delete(Object entity) {
        throw new NotImplementedException();
    }

    @Override
    public void update(Object entity) {
        throw new NotImplementedException();
    }

    @Override
    public Class<?> getReferencedClass() {
        return DailyQuestData.class;
    }
}
