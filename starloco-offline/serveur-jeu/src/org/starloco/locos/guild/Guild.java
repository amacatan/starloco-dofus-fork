package org.starloco.locos.guild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.starloco.locos.entity.map.House;
import org.starloco.locos.client.Player;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.game.GuildMemberData;
import org.starloco.locos.database.data.game.HouseData;
import org.starloco.locos.database.data.login.GuildData;
import org.starloco.locos.entity.Collector;
import org.starloco.locos.fight.spells.Spell.SortStats;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;

public class Guild {

    private int id;
    private long xp;
    private final long date;
    private String name = "", emblem = "";
    private int lvl = 1, capital = 0, nbCollectors = 0;
    private String note = "", noteAuthor = "";
    private long noteDate = 0;
    private String informations = "", informationsAuthor = "";
    private long informationsDate = 0;
    private final Map<Integer, GuildMember> members = new TreeMap<>();
    private final Map<Integer, SortStats> spells = new TreeMap<>(); // <Id, Level>
    private final Map<Integer, Integer> stats = new HashMap<>(); // <Effect, Quantity>
    private final Map<Integer, String> rankNames = new TreeMap<>();

    public Guild(String name, String emblem) {
        this.name = name;
        this.emblem = emblem;
        this.lvl = 1;
        this.xp = 0;
        this.nbCollectors = 0;
        this.date = System.currentTimeMillis();
        this.decompileSpell("462;0|461;0|460;0|459;0|458;0|457;0|456;0|455;0|454;0|453;0|452;0|451;0|");
        this.decompileStats("176;100|158;1000|124;0|");
        ((GuildData) DatabaseManager.get(GuildData.class)).insert(this);
    }

    public Guild(int id, String name, String emblem, int lvl, long xp, int capital, int nbCollectors, String sorts, String stats, long date) {
        this(id, name, emblem, lvl, xp, capital, nbCollectors, sorts, stats, date,
                "", "", 0, "", "", 0, "");
    }

    public Guild(int id, String name, String emblem, int lvl, long xp, int capital, int nbCollectors,
                 String sorts, String stats, long date, String note, String noteAuthor, long noteDate,
                 String informations, String informationsAuthor, long informationsDate, String rankNames) {
        this.id = id;
        this.name = name;
        this.emblem = emblem;
        this.xp = xp;
        this.lvl = lvl;
        this.capital = capital;
        this.nbCollectors = nbCollectors;
        this.date = date;
        this.note = note == null ? "" : note;
        this.noteAuthor = noteAuthor == null ? "" : noteAuthor;
        this.noteDate = noteDate;
        this.informations = informations == null ? "" : informations;
        this.informationsAuthor = informationsAuthor == null ? "" : informationsAuthor;
        this.informationsDate = informationsDate;
        this.rankNames.putAll(GuildFeatureCodec.parseStoredRankNames(rankNames));
        this.decompileSpell(sorts);
        this.decompileStats(stats);
        if (this.nbCollectors < 0) {
            this.nbCollectors = 0;
        }
        int maxCapital = Math.max(0, (this.lvl - 1) * 5);
        if (this.capital > maxCapital) {
            this.capital = Math.max(0, maxCapital - this.calculateSpentCapital());
        }
    }

    public synchronized void addMember(int id, int r, byte pXp, long x, int ri,
                                       String lastCo, String name, int level, int gfx, int align) {
        GuildMember guildMember = new GuildMember(id, this, r, x, pXp, ri, lastCo,
                name, level, gfx, align);
        this.members.put(id, guildMember);
        if(guildMember.getPlayer() != null) {
            guildMember.getPlayer().setGuildMember(guildMember);
        }
    }

    public synchronized GuildMember addNewMember(Player player) {
        GuildMember guildMember = new GuildMember(player.getId(), this, 0, 0, (byte) 0, 0,
                player.getAccount().getLastConnectionDate(), player.getName(), player.getLevel(),
                player.getGfxId(), player.getAlignment());
        this.members.put(player.getId(), guildMember);
        player.setGuildMember(guildMember);
        return guildMember;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNbCollectors() {
        return Math.max(0, this.nbCollectors);
    }

    public void setNbCollectors(int nbr) {
        this.nbCollectors = Math.max(0, nbr);
    }

    public int resetNbCollectors() {
        int current = this.getNbCollectors();
        if (current <= 0) {
            this.setNbCollectors(0);
            return -1;
        }
        int pointsRefunded = current * 10;
        this.setNbCollectors(0);
        return pointsRefunded;
    }

    public int calculateSpentCapital() {
        int spent = 0;
        spent += Math.max(0, this.getStats(Constant.STATS_ADD_PROS) - 100);
        spent += Math.max(0, this.getStats(Constant.STATS_ADD_PODS) - 1000) / 20;
        spent += Math.max(0, this.getStats(Constant.STATS_ADD_SAGE));
        spent += Math.max(0, this.getNbCollectors()) * 10;
        for (SortStats ss : this.spells.values()) {
            if (ss != null && ss.getLevel() > 0) {
                spent += ss.getLevel() * 5;
            }
        }
        return spent;
    }

    public int getCapital() {
        return this.capital;
    }

    public void setCapital(int nbr) {
        this.capital = nbr;
    }

    public Map<Integer, SortStats> getSpells() {
        return this.spells;
    }

    public Map<Integer, Integer> getStats() {
        return stats;
    }

    public long getDate() {
        return date;
    }

    public synchronized String getNote() {
        return note;
    }

    public synchronized String getNoteAuthor() {
        return noteAuthor;
    }

    public synchronized long getNoteDate() {
        return noteDate;
    }

    public synchronized void updateNote(String note, String author, long timestamp) {
        this.note = note;
        this.noteAuthor = author;
        this.noteDate = timestamp;
    }

    public synchronized String getInformations() {
        return informations;
    }

    public synchronized String getInformationsAuthor() {
        return informationsAuthor;
    }

    public synchronized long getInformationsDate() {
        return informationsDate;
    }

    public synchronized void updateInformations(String informations, String author, long timestamp) {
        this.informations = informations;
        this.informationsAuthor = author;
        this.informationsDate = timestamp;
    }

    public synchronized String getRankNames() {
        return GuildFeatureCodec.serializeRankNames(this.rankNames);
    }

    public synchronized String getRankNamesForClient() {
        return GuildFeatureCodec.serializeRankNamesForClient(this.rankNames);
    }

    public synchronized void applyRankChanges(GuildFeatureCodec.RankChanges changes) {
        if (changes.isResetAll()) {
            this.rankNames.clear();
            return;
        }

        for (Map.Entry<Integer, String> change : changes.getChanges().entrySet()) {
            if ("0".equals(change.getValue())) {
                this.rankNames.remove(change.getKey());
            } else {
                this.rankNames.put(change.getKey(), change.getValue());
            }
        }
    }

    public synchronized void restoreRankNames(String storedRankNames) {
        this.rankNames.clear();
        this.rankNames.putAll(GuildFeatureCodec.parseStoredRankNames(storedRankNames));
    }

    public void boostSpell(int id) {
        SortStats SS = this.spells.get(id);
        if (SS != null && SS.getLevel() == 5)
            return;
        this.spells.put(id, ((SS == null) ? World.world.getSort(id).getStatsByLevel(1) : World.world.getSort(id).getStatsByLevel(SS.getLevel() + 1)));
    }

    public boolean unBoostSpell(int id) {
        SortStats SS = this.spells.get(id);
        if (SS != null && SS.getLevel() > 0) {
            this.capital += 5 * SS.getLevel();
            this.spells.put(id, null);
            return true;
        }
        return false;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmblem() {
        return this.emblem;
    }

    public long getXp() {
        return this.xp;
    }

    public int getLvl() {
        return this.lvl;
    }

    public synchronized boolean haveTenMembers() {
        return true;
    }

    public synchronized List<Player> getPlayers() {
        //return this.members.stream().filter(guildMember -> guildMember.getPlayer() != null).map(GuildMember::getPlayer).collect(Collectors.toList());
    	ArrayList<Player> a = new ArrayList<>();
		for (GuildMember GM : this.members.values())
			if (GM.getPlayer() != null)
				a.add(GM.getPlayer());
		return a;
    }

    public synchronized GuildMember getMember(int id) {
        return this.members.get(id);
    }

    public void removeMember(Player player) {
        House house = World.world.getHouseManager().getHouseByPerso(player);
        if (house != null)
            if (World.world.getHouseManager().houseOnGuild(this.id) > 0)
                ((HouseData) DatabaseManager.get(HouseData.class)).updateGuild(house, 0, 0);
        synchronized (this) {
            this.members.remove(player.getId());
        }
        ((GuildMemberData) DatabaseManager.get(GuildMemberData.class)).delete(player);
    }

    public void addXp(long xp) {
        this.xp += xp;
        while (this.xp >= World.world.getGuildXpMax(this.lvl) && this.lvl < 200) this.levelUp();
    }

    private void levelUp() {
        this.lvl++;
        this.capital += 5;
    }

    private void decompileSpell(String spells) {
        for (String split : spells.split("\\|"))
            this.spells.put(Integer.parseInt(split.split(";")[0]), World.world.getSort(Integer.parseInt(split.split(";")[0])).getStatsByLevel(Integer.parseInt(split.split(";")[1])));
    }

    public String compileSpell() {
        if (this.spells.isEmpty())
            return "";

        StringBuilder toReturn = new StringBuilder();
        boolean isFirst = true;

        for (Entry<Integer, SortStats> curSpell : this.spells.entrySet()) {
            if (!isFirst)
                toReturn.append("|");
            toReturn.append(curSpell.getKey()).append(";").append(((curSpell.getValue() == null) ? 0 : curSpell.getValue().getLevel()));
            isFirst = false;
        }

        return toReturn.toString();
    }

    private void decompileStats(String statsStr) {
        for (String split : statsStr.split("\\|")) {
            if (split.isEmpty()) continue;
            String[] data = split.split(";");
            if (data.length < 2) continue;
            this.stats.put(Integer.parseInt(data[0]), Integer.parseInt(data[1]));
        }
        if (this.stats.getOrDefault(Constant.STATS_ADD_PODS, 0) < 1000) {
            this.stats.put(Constant.STATS_ADD_PODS, 1000);
        }
        if (this.stats.getOrDefault(Constant.STATS_ADD_PROS, 0) < 100) {
            this.stats.put(Constant.STATS_ADD_PROS, 100);
        }
        if (this.stats.getOrDefault(Constant.STATS_ADD_SAGE, 0) < 0) {
            this.stats.put(Constant.STATS_ADD_SAGE, 0);
        }
    }

    public String compileStats() {
        if (this.stats.isEmpty())
            return "";

        StringBuilder toReturn = new StringBuilder();
        boolean isFirst = true;

        for (Entry<Integer, Integer> curStats : this.stats.entrySet()) {
            if (!isFirst)
                toReturn.append("|");

            toReturn.append(curStats.getKey()).append(";").append(curStats.getValue());

            isFirst = false;
        }

        return toReturn.toString();
    }

    public void upgradeStats(int id, int add) {
        this.stats.put(id, this.getStats(id) + add);
    }

    public int resetStats(int id) {
        switch (id) {
            case Constant.STATS_ADD_PODS: {
                int current = this.getStats(Constant.STATS_ADD_PODS);
                if (current <= 1000) {
                    this.stats.put(Constant.STATS_ADD_PODS, 1000);
                    return -1;
                }
                int pointsRefunded = (current - 1000) / 20;
                this.stats.put(Constant.STATS_ADD_PODS, 1000);
                return pointsRefunded;
            }
            case Constant.STATS_ADD_PROS: {
                int current = this.getStats(Constant.STATS_ADD_PROS);
                if (current <= 100) {
                    this.stats.put(Constant.STATS_ADD_PROS, 100);
                    return -1;
                }
                int pointsRefunded = current - 100;
                this.stats.put(Constant.STATS_ADD_PROS, 100);
                return pointsRefunded;
            }
            case Constant.STATS_ADD_SAGE: {
                int current = this.getStats(Constant.STATS_ADD_SAGE);
                if (current <= 0) {
                    this.stats.put(Constant.STATS_ADD_SAGE, 0);
                    return -1;
                }
                int pointsRefunded = current;
                this.stats.put(Constant.STATS_ADD_SAGE, 0);
                return pointsRefunded;
            }
            default:
                return -1;
        } 
    }

    public int getStats(int id) {
        switch (id) {
            case Constant.STATS_ADD_PODS:
                return Math.max(1000, stats.getOrDefault(id, 1000));
            case Constant.STATS_ADD_PROS:
                return Math.max(100, stats.getOrDefault(id, 100));
            case Constant.STATS_ADD_SAGE:
                return Math.max(0, stats.getOrDefault(id, 0));
            default:
                return stats.getOrDefault(id, 0);
        }
    }

    //region Parse packet
    public String parseCollectorToGuild() {
        return (100 * getLvl()) + "|" + getLvl() + "|" + getStats(Constant.STATS_ADD_PODS) + "|" + getStats(Constant.STATS_ADD_PROS) + "|" + getStats(Constant.STATS_ADD_SAGE) + "|" + getNbCollectors() + "|" + getCapital() + "|" + compileSpell();
    }

    public String encodeTaxCollectorDQ() {
        return "DQ1;" + String.join(",", getName(), String.valueOf(getStats(Constant.STATS_ADD_PODS)), String.valueOf(getStats(Constant.STATS_ADD_PROS)), String.valueOf(getStats(Constant.STATS_ADD_SAGE)), String.valueOf(getNbCollectors()));
    }

    public synchronized String parseMembersToGM() {
        StringBuilder str = new StringBuilder();
        for (GuildMember member : this.members.values()) {
            if (member == null) {
                continue;
            }

            Player player = member.getPlayer();
            if (str.length() != 0) {
                str.append("|");
            }

            str.append(member.getPlayerId()).append(";");
            str.append(member.getName()).append(";");
            str.append(member.getLvl()).append(";");
            str.append(member.getGfx()).append(";");
            str.append(member.getRank()).append(";");
            str.append(member.getXpGave()).append(";");
            str.append(member.getXpGive()).append(";");
            str.append(member.getRights()).append(";");
            str.append(player != null && player.isOnline() ? "1" : "0").append(";");
            str.append(member.getAlign()).append(";");
            str.append(member.getHoursFromLastCo());
        }
        return str.toString();
    }
    //endregion
}
