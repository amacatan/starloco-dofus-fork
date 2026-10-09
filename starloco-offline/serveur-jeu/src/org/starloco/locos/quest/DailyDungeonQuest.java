package org.starloco.locos.quest;

import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Main;
import org.starloco.locos.client.Account;
import org.starloco.locos.client.Player;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.game.DailyQuestData;
import org.starloco.locos.fight.Fighter;
import org.starloco.locos.lang.LangEnum;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DailyDungeonQuest {

    public static final int DEFAULT_REWARD_POINTS = 25;

    public static class DailyBoss {
        private final int monsterId;
        private final String name;
        private final String dungeon;
        private final String location;
        private final int level;

        public DailyBoss(int monsterId, String name, String dungeon, String location, int level) {
            this.monsterId = monsterId;
            this.name = name;
            this.dungeon = dungeon;
            this.location = location;
            this.level = level;
        }

        public int getMonsterId() { return monsterId; }
        public String getName() { return name; }
        public String getDungeon() { return dungeon; }
        public String getLocation() { return location; }
        public int getLevel() { return level; }
    }

    private static final List<DailyBoss> BOSS_POOL;

    static {
        List<DailyBoss> list = new ArrayList<>();
        list.add(new DailyBoss(147, "Bouftou Royal", "Donjon des Bouftous", "Tainéla [-2,-31]", 35));
        list.add(new DailyBoss(799, "Tournesol Affamé", "Donjon des Champs", "Champs d'Astrub [7,-25]", 40));
        list.add(new DailyBoss(928, "Mob l'Éponge", "Donjon Ensablé", "Calanques d'Astrub [13,-28]", 30));
        list.add(new DailyBoss(797, "Scarabosse Doré", "Donjon des Scarafeuilles", "Plaine des Scarafeuilles [1,26]", 70));
        list.add(new DailyBoss(800, "Batofu", "Donjon des Tofus", "Plaine des Scarafeuilles [5,6]", 60));
        list.add(new DailyBoss(792, "Bworkette", "Donjon des Bworks", "Campement des Bworks [-5,10]", 90));
        list.add(new DailyBoss(180, "Wa Wabbit", "Château Wa Wabbit", "Île des Wabbits [24,-6]", 60));
        list.add(new DailyBoss(669, "Craqueleur Légendaire", "Donjon des Craqueleurs", "Montagne des Craqueleurs [-3,-7]", 120));
        list.add(new DailyBoss(939, "Rat Noir", "Donjon du Rat Noir", "Égouts de Brâkmar [-29,31]", 100));
        list.add(new DailyBoss(940, "Rat Blanc", "Donjon du Rat Blanc", "Égouts de Bonta [-35,-59]", 100));
        list.add(new DailyBoss(113, "Dragon Cochon", "Antre du Dragon Cochon", "Labyrinthe du Dragon Cochon [-1,33]", 100));
        list.add(new DailyBoss(232, "Meulou", "Donjon des Canidés", "Forêt des Canidés [-24,1]", 158));
        list.add(new DailyBoss(289, "Maître Corbac", "Donjon du Maître Corbac", "Péninsule des Gelées [-20,-62]", 120));
        list.add(new DailyBoss(121, "Minotoror", "Labyrinthe du Minotoror", "Île du Minotoror [-42,-17]", 125));
        list.add(new DailyBoss(257, "Chêne Mou", "Donjon du Chêne Mou", "Orée des Abraknydes [-9,-14]", 170));
        list.add(new DailyBoss(670, "Koulosse", "Caverne du Koulosse", "Montagne des Koalaks [-17,8]", 140));
        list.add(new DailyBoss(612, "Maître Pandore", "Donjon Pandikaze", "Pandala Air [21,-31]", 130));
        list.add(new DailyBoss(780, "Skeunk", "Repaire de Skeunk", "Montagne des Koalaks [-20,10]", 120));
        list.add(new DailyBoss(226, "Moon", "Arbre Moon", "Île de Moon [35,5]", 100));
        list.add(new DailyBoss(1045, "Kimbo", "Canopée du Kimbo", "Arbre Hakam [-54,16]", 160));
        list.add(new DailyBoss(478, "Bworker", "Donjon Bworker", "Montagne des Koalaks [-21,12]", 180));
        BOSS_POOL = Collections.unmodifiableList(list);
    }

    public static List<DailyBoss> getBossPool() {
        return BOSS_POOL;
    }

    public static int getRewardPoints() {
        return Config.dailyQuestReward;
    }

    public static boolean isDailyQuestEnabled() {
        return Config.dailyQuestEnabled;
    }

    public static DailyBoss getCurrentBoss() {
        if (Config.isRunning) {
            try {
                DailyQuestData dqd = DatabaseManager.get(DailyQuestData.class);
                if (dqd != null) {
                    int overrideMonsterId = dqd.getOverrideMonsterId();
                    if (overrideMonsterId > 0) {
                        for (DailyBoss b : BOSS_POOL) {
                            if (b.getMonsterId() == overrideMonsterId) {
                                return b;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        long epochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        int index = (int) Math.floorMod(epochDay, BOSS_POOL.size());
        return BOSS_POOL.get(index);
    }

    public static boolean isTodayBoss(int monsterId) {
        return getCurrentBoss().getMonsterId() == monsterId;
    }

    public static String getRemainingTimeString() {
        return getRemainingTimeString(LangEnum.GERMAN);
    }

    public static String getRemainingTimeString(LangEnum lang) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        Duration d = Duration.between(now, midnight);
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        if (lang == LangEnum.FRENCH) {
            return hours + " h " + minutes + " min";
        } else if (lang == LangEnum.SPANISH) {
            return hours + " h " + minutes + " min";
        } else if (lang == LangEnum.ENGLISH) {
            return hours + " hrs " + minutes + " mins";
        }
        return hours + " Std. " + minutes + " Min.";
    }

    public static void showDailyStatus(Player player) {
        if (!isDailyQuestEnabled()) {
            LangEnum lang = (player != null) ? player.getLang() : LangEnum.GERMAN;
            if (lang == LangEnum.FRENCH) {
                player.sendMessage("La quête quotidienne des donjons est actuellement <b>désactivée</b> par l'administration.");
            } else if (lang == LangEnum.ENGLISH) {
                player.sendMessage("The daily dungeon quest is currently <b>disabled</b> by administration.");
            } else {
                player.sendMessage("Die tägliche Dungeon-Quest ist aktuell von der Administration <b>deaktiviert</b>.");
            }
            return;
        }

        DailyBoss boss = getCurrentBoss();
        Account account = player.getAccount();
        boolean completed = account != null && account.hasCompletedDailyBossToday();
        int points = getRewardPoints();
        LangEnum lang = (player != null) ? player.getLang() : LangEnum.GERMAN;

        if (lang == LangEnum.FRENCH) {
            player.sendMessage("================ <b>Quête de Donjon Quotidienne</b> ================");
            player.sendMessage("• <b>Boss Cible :</b> " + boss.getName() + " (Niveau " + boss.getLevel() + ")");
            player.sendMessage("• <b>Donjon :</b> " + boss.getDungeon());
            player.sendMessage("• <b>Position de la carte :</b> " + boss.getLocation());
            player.sendMessage("• <b>Récompense :</b> <b>" + points + " Points VIP</b>");
            if (completed) {
                player.sendMessage("• <b>Statut :</b> <font color='#00aa00'><b>Déjà accomplie aujourd'hui !</b></font>");
                player.sendMessage("  <i>Prochain boss disponible dans : " + getRemainingTimeString(lang) + "</i>");
            } else {
                player.sendMessage("• <b>Statut :</b> <font color='#e67e22'><b>En attente</b></font>");
                player.sendMessage("  <i>Battez " + boss.getName() + " dans son donjon aujourd'hui pour recevoir votre récompense !</i>");
            }
            player.sendMessage("======================================================");
        } else if (lang == LangEnum.SPANISH) {
            player.sendMessage("================ <b>Misión de Mazmorra Diaria</b> ================");
            player.sendMessage("• <b>Jefe Objetivo:</b> " + boss.getName() + " (Nivel " + boss.getLevel() + ")");
            player.sendMessage("• <b>Mazmorra:</b> " + boss.getDungeon());
            player.sendMessage("• <b>Posición del mapa:</b> " + boss.getLocation());
            player.sendMessage("• <b>Recompensa:</b> <b>" + points + " Puntos VIP</b>");
            if (completed) {
                player.sendMessage("• <b>Estado:</b> <font color='#00aa00'><b>¡Ya completada hoy!</b></font>");
                player.sendMessage("  <i>Próximo jefe disponible en: " + getRemainingTimeString(lang) + "</i>");
            } else {
                player.sendMessage("• <b>Estado:</b> <font color='#e67e22'><b>Pendiente</b></font>");
                player.sendMessage("  <i>¡Derrota a " + boss.getName() + " en su mazmorra hoy para recibir tu recompensa!</i>");
            }
            player.sendMessage("======================================================");
        } else if (lang == LangEnum.ENGLISH) {
            player.sendMessage("================ <b>Daily Dungeon Quest</b> ================");
            player.sendMessage("• <b>Target Boss:</b> " + boss.getName() + " (Level " + boss.getLevel() + ")");
            player.sendMessage("• <b>Dungeon:</b> " + boss.getDungeon());
            player.sendMessage("• <b>Map Position:</b> " + boss.getLocation());
            player.sendMessage("• <b>Reward:</b> <b>" + points + " VIP Points</b>");
            if (completed) {
                player.sendMessage("• <b>Status:</b> <font color='#00aa00'><b>Already completed today!</b></font>");
                player.sendMessage("  <i>Next boss quest available in: " + getRemainingTimeString(lang) + "</i>");
            } else {
                player.sendMessage("• <b>Status:</b> <font color='#e67e22'><b>Open</b></font>");
                player.sendMessage("  <i>Defeat " + boss.getName() + " in their dungeon today to earn your reward!</i>");
            }
            player.sendMessage("======================================================");
        } else {
            // German (default)
            player.sendMessage("================ <b>Tägliche Dungeon-Quest</b> ================");
            player.sendMessage("• <b>Ziel-Boss:</b> " + boss.getName() + " (Stufe " + boss.getLevel() + ")");
            player.sendMessage("• <b>Dungeon:</b> " + boss.getDungeon());
            player.sendMessage("• <b>Karten-Position:</b> " + boss.getLocation());
            player.sendMessage("• <b>Belohnung:</b> <b>" + points + " VIP-Punkte</b>");
            if (completed) {
                player.sendMessage("• <b>Status:</b> <font color='#00aa00'><b>Bereits heute abgeschlossen!</b></font>");
                player.sendMessage("  <i>Nächster Boss-Auftrag verfügbar in: " + getRemainingTimeString(lang) + "</i>");
            } else {
                player.sendMessage("• <b>Status:</b> <font color='#e67e22'><b>Noch offen</b></font>");
                player.sendMessage("  <i>Besiege " + boss.getName() + " heute im Dungeon, um deine Belohnung zu erhalten!</i>");
            }
            player.sendMessage("======================================================");
        }
    }

    public static void checkFightReward(Player player, List<Fighter> looseTeam) {
        if (player == null || looseTeam == null) return;
        if (!isDailyQuestEnabled()) return;
        Account account = player.getAccount();
        if (account == null) return;
        if (account.hasCompletedDailyBossToday()) return;

        DailyBoss currentBoss = getCurrentBoss();
        boolean bossFound = false;
        for (Fighter f : looseTeam) {
            if (f.isInvocation()) continue;
            if (f.getMob() != null && f.getMob().getTemplate() != null) {
                if (f.getMob().getTemplate().getId() == currentBoss.getMonsterId()) {
                    bossFound = true;
                    break;
                }
            }
        }

        if (bossFound) {
            int points = getRewardPoints();
            long now = System.currentTimeMillis();
            account.recordDailyBossCompletion(now, points);
            SocketManager.GAME_SEND_STATS_PACKET(player);
            LangEnum lang = player.getLang();

            player.sendMessage(" ");
            if (lang == LangEnum.FRENCH) {
                player.sendMessage("<b>[Quête Quotidienne]</b> <font color='#00aa00'>Félicitations !</font> Vous avez vaincu l'objectif du jour <b>"
                        + currentBoss.getName() + "</b> !");
                player.sendMessage("Vous avez reçu <b>" + points + " Points VIP</b> ! (Solde actuel : <b>"
                        + account.getPoints() + " Points</b>)");
            } else if (lang == LangEnum.SPANISH) {
                player.sendMessage("<b>[Misión Diaria]</b> <font color='#00aa00'>¡Felicidades!</font> ¡Has derrotado al jefe de hoy <b>"
                        + currentBoss.getName() + "</b>!");
                player.sendMessage("¡Has recibido <b>" + points + " Puntos VIP</b>! (Puntos actuales: <b>"
                        + account.getPoints() + " Puntos</b>)");
            } else if (lang == LangEnum.ENGLISH) {
                player.sendMessage("<b>[Daily Quest]</b> <font color='#00aa00'>Congratulations!</font> You defeated today's target <b>"
                        + currentBoss.getName() + "</b>!");
                player.sendMessage("You received <b>" + points + " VIP Points</b>! (Current balance: <b>"
                        + account.getPoints() + " Points</b>)");
            } else {
                player.sendMessage("<b>[Tägliche Quest]</b> <font color='#00aa00'>Glückwunsch!</font> Du hast das Tagesziel <b>"
                        + currentBoss.getName() + "</b> besiegt!");
                player.sendMessage("Du hast <b>" + points + " VIP-Punkte</b> erhalten! (Aktueller Punktestand: <b>"
                        + account.getPoints() + " Punkte</b>)");
            }
            player.sendMessage(" ");
        }
    }
}
