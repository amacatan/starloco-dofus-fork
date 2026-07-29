package org.starloco.locos.database.data.game;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang.NotImplementedException;
import org.starloco.locos.database.data.FunctionDAO;
import org.starloco.locos.entity.monster.Monster;
import org.starloco.locos.game.world.World;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

public class DropData extends FunctionDAO<World.Drop> {
    public DropData(HikariDataSource dataSource) {
        super(dataSource, "drops");
    }

    @Override
    public void loadFully() {
        try {
            World.world.getMonstres().forEach(monster -> monster.getDrops().clear());
            getData("SELECT * FROM " + getTableName() + ";", result -> {
                while (result.next()) {
                    int monsterId = result.getInt("monsterId");
                    int objectId = result.getInt("objectId");
                    Monster MT = World.world.getMonstre(monsterId);
                    String action = result.getString("action");
                    int level = result.getInt("level");
                    String condition = "";
                    ArrayList<Double> percents = getPercents(result);

                    if (!action.equals("-1") && !action.equals("1") && action.contains(":")) {
                        condition = action.split(":")[1];
                        action = action.split(":")[0];
                    }
                    action = normalizeAction(action, level);

                    if (!isLoadableDrop(World.world.getObjTemplate(objectId) != null, MT != null, monsterId)) {
                        continue;
                    }

                    World.Drop drop = new World.Drop(objectId, percents, result.getInt("ceil"), Integer.parseInt(action), level, condition);
                    if (MT != null) {
                        MT.addDrop(drop);
                    } else {
                        World.world.getMonstres().stream().filter(Objects::nonNull).forEach(monster -> monster.addDrop(drop));
                    }
                }
            });
        } catch (SQLException e) {
            super.sendError(e);
        }
    }

    static boolean isLoadableDrop(boolean itemTemplateExists, boolean monsterExists, int monsterId) {
        return itemTemplateExists && (monsterExists || monsterId == 0);
    }

    /**
     * The May 2023 data set marks every regular drop with action {@code 1}
     * and level {@code 0}. Older data uses action {@code 1} only for hunter
     * meat and gives it a positive required job level.
     */
    static String normalizeAction(String action, int level) {
        return "1".equals(action) && level <= 0 ? "-1" : action;
    }

    private ArrayList<Double> getPercents(ResultSet result) throws SQLException {
        ArrayList<Double> percents = new ArrayList<>();
        percents.add(result.getDouble("percentGrade1"));
        percents.add(result.getDouble("percentGrade2"));
        percents.add(result.getDouble("percentGrade3"));
        percents.add(result.getDouble("percentGrade4"));
        percents.add(result.getDouble("percentGrade5"));
        return percents;
    }

    @Override
    public World.Drop load(int id) {
        throw new NotImplementedException();
    }

    @Override
    public boolean insert(World.Drop entity) {
        throw new NotImplementedException();
    }

    @Override
    public void delete(World.Drop entity) {
        throw new NotImplementedException();
    }

    @Override
    public void update(World.Drop entity) {
        throw new NotImplementedException();
    }

    @Override
    public Class<?> getReferencedClass() {
        return DropData.class;
    }
}
