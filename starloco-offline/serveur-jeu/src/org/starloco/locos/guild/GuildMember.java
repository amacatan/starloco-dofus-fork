package org.starloco.locos.guild;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.starloco.locos.client.Player;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.game.GuildMemberData;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;

/**
 * Created by Locos on 31/01/2017
 **/
public class GuildMember {

    private final int playerId;
    private final Guild guild;
    private volatile int rank = 0;
    private volatile byte xpGive = 0;
    private long xpGave = 0;
    private volatile int rights = 0;
    private String lastCo;

    // Offline-Daten aus der Datenbank
    private String name;
    private int lvl;
    private int gfx;
    private int align;

    // Konstruktor mit 11 Parametern (Aufruf aus Guild.java)
    public GuildMember(int playerId, Guild guild, int rank, long xpGave, byte xpGive, int rights, String lastCo, String name, int lvl, int gfx, int align) {
        this.playerId = playerId;
        this.guild = guild;
        this.rank = rank;
        this.xpGave = xpGave;
        this.xpGive = xpGive;
        this.rights = rights;
        this.lastCo = lastCo;
        this.name = name;
        this.lvl = lvl;
        this.gfx = gfx;
        this.align = align;
    }

    // Konstruktor mit 7 Parametern (für Abwärtskompatibilität)
    public GuildMember(int playerId, Guild guild, int rank, long xpGave, byte xpGive, int rights, String lastCo) {
        this(playerId, guild, rank, xpGave, xpGive, rights, lastCo, "", 1, 0, 0);
    }

    public Player getPlayer() {
        return World.world.getPlayer(playerId);
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getName() {
        Player p = getPlayer();
        if (p != null) return p.getName();
        return name;
    }

    public int getAlign() {
        Player p = getPlayer();
        if (p != null) return p.getAlignment();
        return align;
    }

    public int getGfx() {
        Player p = getPlayer();
        if (p != null) return p.getGfxId();
        return gfx;
    }

    public int getLvl() {
        Player p = getPlayer();
        if (p != null) return p.getLevel();
        return lvl;
    }

    public Guild getGuild() {
        return guild;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int i) {
        this.rank = i;
    }

    public long getXpGave() {
        return xpGave;
    }

    public int getXpGive() {
        return xpGive;
    }

    public void giveXpToGuild(long xp) {
        this.xpGave += xp;
        this.guild.addXp(xp);
    }

    public String parseRights() {
        return Integer.toString(this.rights, 36);
    }

    public int getRights() {
        return rights;
    }

    public String getLastCo() {
        return lastCo;
    }

    public void setLastCo(String lastCo) {
        this.lastCo = lastCo;
    }

    int getHoursFromLastCo() {
        if (this.lastCo == null || this.lastCo.isEmpty()) return 0;
        String[] split = this.lastCo.split("~");
        if (split.length < 3) return 0;
        try {
            LocalDate localDate = LocalDate.of(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            return (int) (ChronoUnit.DAYS.between(localDate, LocalDate.now()) * 24);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean canDo(int rightValue) {
        int currentRights = this.rights;
        if (currentRights == 1) {
            return true;
        }
        return currentRights >= 0 && rightValue > 0
                && (rightValue & ~Constant.G_ALL_RIGHTS) == 0
                && (currentRights & rightValue) == rightValue;
    }

    public synchronized void setAllRights(int rank, byte xp, int right) {
        if (rank == -1) rank = this.rank;
        if (xp < 0) xp = this.xpGive;
        if (xp > 90) xp = 90;
        if (right == -1) right = this.rights;
        if (right != 1 && (right < 0 || (right & ~Constant.G_ALL_RIGHTS) != 0)) {
            right = this.rights;
        }

        this.rank = rank;
        this.xpGive = xp;
        this.rights = right;

        ((GuildMemberData) DatabaseManager.get(GuildMemberData.class)).updateMembership(this);
    }
}