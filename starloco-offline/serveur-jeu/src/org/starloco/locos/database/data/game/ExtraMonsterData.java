package org.starloco.locos.database.data.game;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang.NotImplementedException;
import org.starloco.locos.database.data.FunctionDAO;
import org.starloco.locos.game.world.World;

import java.sql.SQLException;

public class ExtraMonsterData extends FunctionDAO<Object> {
    public ExtraMonsterData(HikariDataSource dataSource) {
        super(dataSource, "extra_monster");
    }

    @Override
    public void loadFully() {
        try {
            getData("SELECT * from extra_monster", result -> {
                while (result.next()) {
                    String superArea = result.getString("superArea");
                    String subArea = result.getString("subArea");
                    if (hasUsablePlacement(superArea, subArea)) {
                        World.world.addExtraMonster(
                                result.getInt("idMob"),
                                superArea,
                                subArea,
                                result.getInt("chances")
                        );
                    }
                }
            });
        } catch (SQLException e) {
            super.sendError(e);
        }
    }

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
        return ExtraMonsterData.class;
    }

    static boolean hasUsablePlacement(String superArea, String subArea) {
        return containsAreaId(superArea) || containsAreaId(subArea);
    }

    private static boolean containsAreaId(String areas) {
        if (areas == null) {
            return false;
        }
        for (String area : areas.split(",", -1)) {
            try {
                if (!area.trim().isEmpty() && Integer.parseInt(area.trim()) >= 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid tokens; another placement token may still be usable.
            }
        }
        return false;
    }
}
