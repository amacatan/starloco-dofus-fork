package org.starloco.locos.command;

import org.starloco.locos.quest.DailyDungeonQuest;
import org.starloco.locos.lang.LangEnum;
import org.starloco.locos.command.transfer.ItemTransferCategory;
import org.starloco.locos.entity.exchange.PlayerExchange;
import org.starloco.locos.auction.AuctionManager;
import org.starloco.locos.client.Account;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.login.AccountData;
import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Party;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.event.EventManager;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.kernel.Logging;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.util.TimerWaiter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class CommandPlayer {

    public final static String canal = "Général";
    static boolean canalMute = false;

    public static boolean analyse(Player player, String msg) {
        msg = msg.replace("|", "");
        if (msg.charAt(0) == '.') {
            if(command(msg, "help")) {
                return commandHelp(player, msg);
            } else if (command(msg, "daily") || command(msg, "bounty") || command(msg, "quest") || command(msg, "boss")) {
                DailyDungeonQuest.showDailyStatus(player);
                return true;
            } else if (command(msg, "vote")) {
                return commandVote(player, msg);
            } else if (command(msg, "all") && msg.length() > 5) {
                return commandAll(player, msg);
            } else if (command(msg, "noall")) {
                return commandNoAll(player, msg);
            } else if (command(msg, "staff") || command(msg, "admin")) {
                return commandStaff(player, msg);
            } else if (command(msg, "deblo")) {
                return commandDeblo(player, msg);
            } else if (command(msg, "infos")) {
                return commandInfos(player, msg);
            } else if (command(msg, "master") || command(msg, "maitre") || command(msg, "maître") || command(msg, "maestro")) {
                return commandMaster(player, msg);
            } else if (command(msg, "pass")) {
                return commandPass(player, msg);
            } else if (command(msg, "interval")) {
                return commandInterval(player, msg);
            } else if (command(msg, "start") || command(msg, "astrub")) {
                return commandAstrub(player, msg);
            } else if (command(msg, "walkfast")) {
                player.walkFast = !player.walkFast;
                return true;
            } else if (command(msg, "skiploot") || command(msg, "fastfight") || command(msg, "nofightwindow") || command(msg, "lootpanel")) {
                return commandSkipLoot(player, msg);
            } else if (command(msg, "101") || command(msg, "scroll") || command(msg, "scrolls") || command(msg, "scrools")) {
                return commandScroll101(player, msg);
            } else if (command(msg, "vie") || command(msg, "life")) {
                return commandVie(player, msg);
            } else if (command(msg, "vip")) {
                return commandVip(player, msg);
            } else if (command(msg, "savepos")) {
                return commandStart(player, msg);
            } else if (command(msg, "crush") || command(msg, "briser") || command(msg, "runen") || command(msg, "concasseur")) {
                return commandCrush(player, msg);
            } else if (command(msg, "transfert") || command(msg, "transfer") || command(msg, "trade") || command(msg, "tausch")) {
                return commandTransfert(player, msg);
            } else if (command(msg, "banque") || command(msg, "bank")) {
                if (!player.isVip()) {
                    player.sendMessage(player.getLang().trans("command.commandplayer.life.nosubscribe"));
                    return true;
                }
                if (player.isInPrison() || player.getFight() != null || player.getExchangeAction() != null)
                    return true;
                player.openBank();
                return true;
            } else if (command(msg, "groupe") || command(msg, "group")) {
                if (player.isInPrison() || player.getFight() != null)
                    return true;
                final byte[] count = {0};
                World.world.getOnlinePlayers().stream().filter(p -> !p.equals(player) && p.getParty() == null && p.getAccount().getCurrentIp().equals(player.getAccount().getCurrentIp()) && p.getFight() == null && !p.isInPrison()).forEach(p -> {
                    if(count[0] <= 8) {
                        if (player.getParty() == null) {
                            Party party = new Party(player, p);
                            SocketManager.GAME_SEND_GROUP_CREATE(player.getGameClient(), party);
                            SocketManager.GAME_SEND_PL_PACKET(player.getGameClient(), party);
                            SocketManager.GAME_SEND_GROUP_CREATE(p.getGameClient(), party);
                            SocketManager.GAME_SEND_PL_PACKET(p.getGameClient(), party);
                            player.setParty(party);
                            p.setParty(party);
                            SocketManager.GAME_SEND_ALL_PM_ADD_PACKET(player.getGameClient(), party);
                            SocketManager.GAME_SEND_ALL_PM_ADD_PACKET(p.getGameClient(), party);
                        } else {
                            SocketManager.GAME_SEND_GROUP_CREATE(p.getGameClient(), player.getParty());
                            SocketManager.GAME_SEND_PL_PACKET(p.getGameClient(), player.getParty());
                            SocketManager.GAME_SEND_PM_ADD_PACKET_TO_GROUP(player.getParty(), p);
                            player.getParty().addPlayer(p);
                            p.setParty(player.getParty());
                            SocketManager.GAME_SEND_ALL_PM_ADD_PACKET(p.getGameClient(), player.getParty());
                            SocketManager.GAME_SEND_PR_PACKET(p);
                        }
                    }
                    count[0] = (byte) (count[0] + 1);
                });

                return true;
            } else if(command(msg, "event")) {
                return player.cantTP() || EventManager.getInstance().subscribe(player) == 1;
            } else if(command(msg, "auction")) {
                if(player.cantTP() || player.isDead() != 0 || player.isGhost() || player.isAway() || player.getFight() != null)
                    return true;
                AuctionManager.getInstance().onPlayerCommand(player, msg.split(" "));
                return true;
            } else {
                player.sendMessage(player.getLang().trans("command.commandplayer.default"));
                return true;
            }
        }
        return false;
    }

    private static boolean commandPass(Player player, String msg) {
        if(player.getParty() != null && player.getParty().getMaster() != null) {
            Party.MasterOption option = player.getParty().getOptionByPlayer(player);
            if(option != null) {
                option.togglePass();
                player.sendMessage(player.getLang().trans("command.commandplayer.pass"));
                return true;
            }
        } else {
            player.sendMessage(player.getLang().trans("command.commandplayer.pass.error"));
        }
        return false;
    }

    private static boolean commandInterval(Player player, String msg) {
        if(player.getParty() != null && player.getParty().getMaster() != null) {
            Party.MasterOption option = player.getParty().getOptionByPlayer(player);
            if(option != null) {
                try {
                    byte second = Byte.parseByte(msg.split(" ")[1]);
                    for(Party.MasterOption opt : player.getParty().getOptions()) {
                        if (opt != null && opt.getSecond() == second) {
                            player.sendMessage(player.getLang().trans("command.commandplayer.interval.error"));
                            return true;
                        }
                    }

                    option.setSecond(second);
                } catch(Exception ignored) {
                    player.sendMessage(player.getLang().trans("command.commandplayer.interval.error"));
                }

                if(option.getSecond() < 1) option.setSecond((byte)1);
                else if(option.getSecond() > 29) option.setSecond((byte)29);

                player.sendMessage(player.getLang().trans("command.commandplayer.interval"));
                return true;
            }
        } else {
            player.sendMessage(player.getLang().trans("command.commandplayer.interval.master"));
        }
        return false;
    }

    private static List<Integer> bannedItemJob = Arrays.asList(491, 493, 494, 495, 496);

    private static boolean commandCrush(Player player, String msg) {
        if (player.isInPrison() || player.getFight() != null)
            return true;

        if (player.getGameClient() == null)
            return true;

        if (!player.isVip()) {
            player.sendMessage("Dieser Befehl ist für <b>VIP-Spieler</b> reserviert. Tippe <b>.vip</b> für mehr Informationen.");
            return true;
        }

        String[] parts = msg.split(" ");
        if (parts.length < 2) {
            player.sendMessage("Bitte gib einen Parameter an: <b>.crush [all | level]</b> (z. B. <b>.crush all</b> oder <b>.crush 50</b>).");
            player.sendMessage("Tippe <b>.crush help</b> für weitere Informationen.");
            return true;
        }

        if (parts[1].equalsIgnoreCase("help") || parts[1].equalsIgnoreCase("?")) {
            player.sendMessage("<b>Schnell-Crusher Hilfe (.crush / .briser / .runen):</b>");
            player.sendMessage("• <b>.crush all</b>: Zerkleinert alle nicht angelegten Ausrüstungen sofort zu Runen (Fragment Magique).");
            player.sendMessage("• <b>.crush [level]</b>: Zerkleinert alle Ausrüstungen bis zum angegebenen Level (z. B. <b>.crush 50</b>).");
            player.sendMessage("• <b>Im Zerkleinerer-Fenster:</b> Du kannst auch direkt <b>.transfer equip</b> eingeben.");
            return true;
        }

        java.util.function.Predicate<GameObject> filter = null;
        int maxLvl = -1;
        if (!parts[1].equalsIgnoreCase("all") && !parts[1].equalsIgnoreCase("equip")) {
            try {
                maxLvl = Integer.parseInt(parts[1]);
                if (maxLvl <= 0) {
                    player.sendMessage("Level muss größer als 0 sein.");
                    return true;
                }
                final int limit = maxLvl;
                filter = obj -> obj.getTemplate() != null && obj.getTemplate().getLevel() <= limit;
            } catch (NumberFormatException e) {
                player.sendMessage("Ungültige Eingabe. Syntax: <b>.crush all</b> oder <b>.crush [maxLevel]</b> (z. B. <b>.crush 50</b>).");
                return true;
            }
        }

        int count = player.getGameClient().crushItems(filter);
        if (count > 0) {
            String filterMsg = maxLvl > 0 ? " (bis Level " + maxLvl + ")" : "";
            player.sendMessage("Crusher: <b>" + count + "</b> Ausrüstungs-Stapel" + filterMsg + " erfolgreich zerkleinert! Das Magische Fragment befindet sich in deinem Inventar.");
        } else if (count == 0) {
            player.sendMessage("Crusher: Keine passenden, unangelegten Ausrüstungsgegenstände zum Zerkleinern gefunden.");
        } else {
            player.sendMessage("Crusher: Fehler beim Zerkleinern der Gegenstände aufgetreten.");
        }
        return true;
    }

    private static boolean commandTransfert(Player player, String msg) {
        if (player.isInPrison() || player.getFight() != null)
            return true;

        if (!player.isVip()) {
            player.sendMessage("Dieser Befehl ist für <b>VIP-Spieler</b> reserviert. Tippe <b>.vip</b> für mehr Informationen.");
            return true;
        }

        String[] info = msg.split(" ");
        if (info.length >= 2 && (info[1].equalsIgnoreCase("help") || info[1].equalsIgnoreCase("?"))) {
            player.sendMessage("<b>Schnell-Transfer Hilfe:</b>");
            player.sendMessage("Syntax: <b>.transfer [kategorie]</b> (Aliase: .trade, .tausch)");
            player.sendMessage("Kategorien: <b>res</b> (Ressourcen), <b>equip</b> (Ausrüstung), <b>use</b> (Nutzgegenstände), <b>all</b> (Alles)");
            player.sendMessage("• <b>Im Tauschfenster:</b> Legt passende Gegenstände direkt in den Tausch.");
            player.sendMessage("• <b>Im Zerkleinerer (Concasseur):</b> .transfer equip zerkleinert alle Ausrüstungen auf einmal!");
            player.sendMessage("• <b>In der Gruppe (.master):</b> Überträgt Gegenstände aller Follower an den Master.");
            player.sendMessage("• <b>In der Bank:</b> .transfer bank [kategorie].");
            return true;
        }

        if (info.length < 2) {
            player.sendMessage("Bitte gib eine Kategorie an: <b>.transfer [kategorie]</b>");
            player.sendMessage("Verfügbare Kategorien: <b>res</b> (Ressourcen), <b>equip</b> (Ausrüstung), <b>use</b> (Nutzgegenstände), <b>all</b> (Alles).");
            return true;
        }

        ItemTransferCategory category = null;
        if (info[1].equalsIgnoreCase("bank")) {
            if (info.length < 3) {
                player.sendMessage("Bitte gib eine Kategorie für die Bank an: <b>.transfer bank [kategorie]</b>");
                player.sendMessage("Verfügbare Kategorien: <b>res</b>, <b>equip</b>, <b>use</b>, <b>all</b>.");
                return true;
            }
            category = ItemTransferCategory.parse(info[2]);
        } else {
            category = ItemTransferCategory.parse(info[1]);
        }

        if (category == null) {
            player.sendMessage("Unbekannte Kategorie. Verfügbar: <b>res</b>, <b>equip</b>, <b>use</b>, <b>all</b>.");
            return true;
        }

        // 1. Kontext: Im Zerkleinerer (Concasseur)
        if (player.getExchangeAction() != null && player.getExchangeAction().getType() == ExchangeAction.BREAKING_OBJECTS) {
            if (category == ItemTransferCategory.EQUIPMENT || category == ItemTransferCategory.ALL) {
                int count = player.getGameClient() != null ? player.getGameClient().crushItems(null) : 0;
                if (count > 0) {
                    player.sendMessage("Crusher: <b>" + count + "</b> Ausrüstungs-Stapel erfolgreich zerkleinert! Das Magische Fragment befindet sich in deinem Inventar.");
                } else if (count == 0) {
                    player.sendMessage("Crusher: Keine passenden, unangelegten Ausrüstungen zum Zerkleinern im Inventar.");
                } else {
                    player.sendMessage("Crusher: Fehler beim Zerkleinern.");
                }
                return true;
            } else {
                player.sendMessage("Crusher: Im Zerkleinerer können nur Ausrüstungen zerkleinert werden (z. B. <b>.transfer equip</b> oder <b>.crush all</b>).");
                return true;
            }
        }

        // 2. Kontext: Im aktiven Spieler-Tauschfenster
        if (player.getExchangeAction() != null && player.getExchangeAction().getType() == ExchangeAction.TRADING_WITH_PLAYER) {
            return commandTransfertInTrade(player, category);
        }

        // 3. Kontext: Als Party-Master in Multi-Account-Gruppe
        if (player.getParty() != null && player.getParty().getMaster() != null && player.getParty().getMaster().getId() == player.getId()) {
            return commandTransfertWithMaster(player, category);
        }

        // 4. Kontext: In der Bank
        if (player.getExchangeAction() != null && player.getExchangeAction().getType() == ExchangeAction.IN_BANK) {
            return commandTransfertInBank(player, info, category);
        }

        player.sendMessage("Transfert: Kein Tauschfenster, Zerkleinerer, Bank oder Gruppe mit .master aktiv.");
        player.sendMessage("Syntax: <b>.transfer [res | equip | use | all]</b> (Aliase: .trade, .tausch)");
        return true;
    }

    private static boolean commandTransfertInTrade(Player player, ItemTransferCategory category) {
        if (player.getExchangeAction() == null || player.getExchangeAction().getType() != ExchangeAction.TRADING_WITH_PLAYER) {
            return false;
        }
        Object exchangeVal = player.getExchangeAction().getValue();
        if (!(exchangeVal instanceof PlayerExchange)) {
            player.sendMessage("Transfert: Nur im Spieler-Handel verfügbar.");
            return true;
        }
        PlayerExchange exchange = (PlayerExchange) exchangeVal;

        int addedCount = 0;
        int totalQty = 0;

        for (GameObject obj : new ArrayList<>(player.getItems().values())) {
            if (obj == null) continue;
            if (bannedItemJob.contains(obj.getTemplate().getId())) continue;
            if (!ItemTransferCategory.matches(obj, category)) continue;

            int quaInExch = exchange.getQuaItem(obj.getGuid(), player.getId());
            int remainingQty = obj.getQuantity() - quaInExch;
            if (remainingQty <= 0) continue;

            exchange.addItem(obj.getGuid(), remainingQty, player.getId());
            addedCount++;
            totalQty += remainingQty;
        }

        if (addedCount > 0) {
            player.sendMessage("Transfert: <b>" + addedCount + "</b> Stapel (" + totalQty + " Stk. " + category.getLabel() + ") in das Tauschfenster gelegt.");
        } else {
            player.sendMessage("Transfert: Keine passenden (" + category.getLabel() + ") Gegenstände im Inventar gefunden.");
        }
        return true;
    }

    private static boolean commandTransfertWithMaster(Player player, ItemTransferCategory category) {
        if (player.getParty() != null && player.getParty().getMaster() != null && player.getParty().getMaster().getId() == player.getId()) {
            final List<GameObject> objects = new ArrayList<>();
            player.getParty().getPlayers().stream()
                    .filter(follower -> follower != player && follower.getFight() == null && follower.getGameClient() != null && player.getParty().isWithTheMaster(follower, false, false))
                    .forEach(follower -> {
                        follower.getGameClient().clearAllPanels(null);
                        for (GameObject object : new ArrayList<>(follower.getItems().values())) {
                            if (object != null) {
                                if (bannedItemJob.contains(object.getTemplate().getId()))
                                    continue;
                                if (!ItemTransferCategory.matches(object, category))
                                    continue;
                                follower.removeItem(object.getGuid(), object.getQuantity(), true, false);
                                objects.add(object);
                            }
                        }
                    });
            TimerWaiter.addNext(() -> {
                for (GameObject object : objects) {
                    if (!player.addItem(object, true, false))
                        World.world.removeGameObject(object.getGuid());
                }
                player.sendTypeMessage("Transfert", objects.size() + " (" + category.getLabel() + ") Gegenstände von Gruppenmitgliedern übernommen.");
            }, 1000);
            return true;
        }
        return false;
    }

    private static boolean commandTransfertInBank(Player player, String[] info, ItemTransferCategory category) {
        int map = player.getCurMap().getId();
        SocketManager.GAME_SEND_EV_PACKET(player.getGameClient());
        player.sendTypeMessage("Bank", player.getLang().trans("command.commandplayer.transfer.waitting"));
        int count = 0;

        boolean bank = info.length >= 2 && info[1].equalsIgnoreCase("bank");

        for (GameObject object : new ArrayList<>(bank ? player.getAccount().getBank() : player.getItems().values())) {
            if (object == null || object.getTemplate() == null)
                continue;
            if (bannedItemJob.contains(object.getTemplate().getId()))
                continue;
            if (!ItemTransferCategory.matches(object, category))
                continue;

            count++;
            if (!bank) {
                player.addInBank(object.getGuid(), object.getQuantity(), false);
            } else {
                player.removeFromBank(object.getGuid(), object.getQuantity());
            }
        }

        player.sendTypeMessage("Bank", player.getLang().trans("command.commandplayer.transfer.good", count));
        player.setExchangeAction(null);
        if (player.getCurMap().getId() == map)
            player.openBank();
        return true;
    }

    private static boolean commandStart(Player player, String msg) {
        int mapId = player.getCurMap().getId();
        if (player.isInPrison() || player.cantTP() || player.getFight() != null)
            return true;
        player.warpToSavePos();

        final Party party = player.getParty();
        if(party != null && party.getMaster() != null && party.getMaster().getName().equals(player.getName())) {
            for (Player slave : player.getParty().getPlayers()) {
                if (slave.getCurMap().getId() == mapId) {
                    if (!player.isInPrison())
                        if (!player.cantTP())
                            if (player.getFight() == null)
                                slave.warpToSavePos();
                }
            }
        }
        return true;
    }

    private static boolean commandAstrub(Player player, String msg) {
        int mapId = player.getCurMap().getId();
        if (player.isInPrison() || player.cantTP() || player.getFight() != null)
            return true;
        player.teleport((short) 7411, 385);

        final Party party = player.getParty();
        if(party != null && party.getMaster() != null && party.getMaster().getName().equals(player.getName())) {
            player.getParty().getPlayers().stream().filter(p -> party.isWithTheMaster(p, false, true)).forEach(slave -> {
                if (slave.getCurMap().getId() == mapId) {
                    if (!player.isInPrison() && !player.cantTP())
                        if (player.getFight() == null)
                            slave.teleport((short) 7411, 385);
                }
            });
        }
        return true;
    }

    private static boolean commandVie(Player player, String msg) {
        if(player.getCurPdv() == player.getMaxPdv()){
            player.sendMessage(player.getLang().trans("command.commandplayer.vie.error"));
            return true;
        }
        player.setPdv(player.getMaxPdv());
        if (player.isOnline())
            SocketManager.GAME_SEND_STATS_PACKET(player);
        player.sendMessage(player.getLang().trans("command.commandplayer.vie.success"));
        return true;
    }

    private static boolean commandScroll101(Player player, String msg) {
        if (!player.isVip()) {
            player.sendMessage(player.getLang().trans("command.commandplayer.scroll.novip"));
            return true;
        }
        if (player.isInPrison() || player.getFight() != null) {
            player.sendMessage(player.getLang().trans("command.commandplayer.scroll.cant"));
            return true;
        }
        long kamaCost = 1000000L;
        if (player.getKamas() < kamaCost) {
            player.sendMessage(player.getLang().trans("command.commandplayer.scroll.nokamas", kamaCost));
            return true;
        }

        // 1. Kamas abziehen
        player.setKamas(player.getKamas() - kamaCost);

        // 2. Investierte Kapitalpunkte zurückerstatten & Stats auf 0 setzen
        player.resetStats(false);

        // Direkt die Basis-Stats auf 101 setzen
        player.getStats().addOneStat(Constant.STATS_ADD_FORC, 101); // Stärke
        player.getStats().addOneStat(Constant.STATS_ADD_AGIL, 101); // Beweglichkeit
        player.getStats().addOneStat(Constant.STATS_ADD_CHAN, 101); // Glück
        player.getStats().addOneStat(Constant.STATS_ADD_SAGE, 101); // Weisheit
        player.getStats().addOneStat(Constant.STATS_ADD_VITA, 101); // Vitalität
        player.getStats().addOneStat(Constant.STATS_ADD_INTE, 101); // Intelligenz

        // Stats neu an Client senden
        SocketManager.GAME_SEND_STATS_PACKET(player);

        player.sendMessage(player.getLang().trans("command.commandplayer.scroll.success"));
        return true;
    }

    private static boolean commandVip(Player player, String msg) {
        Account account = player.getAccount();
        if (account == null) return true;

        String[] parts = msg.split(" ");
        if (parts.length >= 2) {
            String sub = parts[1].toLowerCase();
            if (sub.equals("buy")) {
                player.sendMessage("<b>VIP-Status aktivieren:</b>");
                player.sendMessage("VIP-Abonnements k?nnen bequem ?ber die Website erworben werden:");
                player.sendMessage("?? <b>http://10.0.60.110/new/?page=shop#vip</b>");
                player.sendMessage("Aktuelles Guthaben: <b>" + account.getPoints() + " VIP-Punkte</b>.");
                return true;
            } else if (sub.equals("kamas") || sub.equals("exchange")) {
                if (parts.length < 3) {
                    player.sendMessage("Syntax: <b>.vip kamas [anzahl_punkte]</b>");
                    player.sendMessage("Wechselkurs: <b>1.000 Kamas = 1 VIP-Punkt</b> (z. B. .vip kamas 50 für 50.000 Kamas).");
                    player.sendMessage("Aktuelle Kamas: <b>" + player.getKamas() + "</b> | VIP-Punkte: <b>" + account.getPoints() + "</b>.");
                    return true;
                }
                try {
                    int pointsToBuy = Integer.parseInt(parts[2]);
                    if (pointsToBuy <= 0) {
                        player.sendMessage("Bitte gib eine Zahl größer als 0 an.");
                        return true;
                    }
                    long costKamas = (long) pointsToBuy * 1000L;
                    if (player.getKamas() < costKamas) {
                        player.sendMessage("Du hast nicht genügend Kamas! Benötigt: <b>" + costKamas + " Kamas</b>, du hast: <b>" + player.getKamas() + " Kamas</b>.");
                        return true;
                    }

                    player.addKamas(-costKamas);
                    account.modPoints(pointsToBuy);
                    SocketManager.GAME_SEND_STATS_PACKET(player);
                    player.sendMessage("Erfolgreich <b>" + pointsToBuy + " VIP-Punkte</b> für " + costKamas + " Kamas gekauft! Neuer Punktestand: <b>" + account.getPoints() + " Punkte</b>.");
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("Ungültige Zahl. Syntax: <b>.vip kamas [punkte]</b>");
                    return true;
                }
            } else if (sub.equals("daily") || sub.equals("quest") || sub.equals("bounty") || sub.equals("boss")) {
                DailyDungeonQuest.showDailyStatus(player);
                return true;
            } else if (sub.equals("vote")) {
                return commandVote(player, msg);
            }
        }

        // Standard: Status & Übersicht
        player.sendMessage("================ <b>VIP-System</b> ================");
        if (account.getVip() > 0) {
            player.sendMessage("• <b>VIP-Status:</b> <font color='#00FF00'>AKTIV (Dauerhaft)</font>");
        } else if (account.isSubscribeWithoutCondition()) {
            long remainingHours = (account.getSubscriber() - System.currentTimeMillis()) / (3600L * 1000L);
            long days = remainingHours / 24L;
            long hours = remainingHours % 24L;
            player.sendMessage("• <b>VIP-Status:</b> <font color='#00FF00'>AKTIV (Noch " + days + " Tage, " + hours + " Std.)</font>");
        } else {
            player.sendMessage("• <b>VIP-Status:</b> <font color='#FF0000'>INAKTIV</font>");
        }
        player.sendMessage("• <b>Dein Guthaben:</b> <b>" + account.getPoints() + " VIP-Punkte</b>");
        player.sendMessage(" ");
        player.sendMessage("<b>Möglichkeiten, VIP-Punkte zu erhalten:</b>");
        player.sendMessage("• <b>.daily</b> - Tägliche Dungeon-Boss-Quest für " + DailyDungeonQuest.getRewardPoints() + " VIP-Punkte abschließen.");
        player.sendMessage("• <b>.vote</b> - Alle 2 Stunden für 50 VIP-Punkte voten.");
        player.sendMessage("• <b>.vip kamas [punkte]</b> - Kamas in VIP-Punkte umtauschen (1.000 Kamas = 1 Punkt).");
        player.sendMessage("• VIP-Pakete bequem im Webshop aktivieren: <b>" + Config.serverWebUrl + "?page=shop</b>");
        player.sendMessage(" ");
        player.sendMessage("<b>Deine VIP-Vorteile:</b>");
        player.sendMessage("• <b>.bank</b> - Bankfach überall und jederzeit öffnen.");
        player.sendMessage("• <b>.vie</b> / <b>.life</b> - Volle Lebenspunkte sofort wiederherstellen.");
        player.sendMessage("• <b>.transfer [kategorie]</b> - Schneller Item-Transfer im Handel, Master & Bank.");
        player.sendMessage("• <b>.crush [all | level]</b> - Schneller Runen-Crusher ohne 8er-Beschränkung.");
        player.sendMessage("==================================================");
        return true;
    }

    private static boolean commandVote(Player player, String msg) {
        Account account = player.getAccount();
        if (account == null) return true;
        long now = System.currentTimeMillis();
        long lastVote = account.getHeureVote();
        long cooldown = Config.voteCooldown * 1000L;
        long elapsed = now - lastVote;
        LangEnum lang = player.getLang();

        if (lastVote > 0 && elapsed < cooldown) {
            long remainingMin = Math.max(1, (cooldown - elapsed) / (60L * 1000L));
            if (lang == LangEnum.FRENCH) {
                player.sendMessage("Vous avez déjà voté ! Prochain vote disponible dans : <b>" + remainingMin + " minute(s)</b>.");
            } else if (lang == LangEnum.SPANISH) {
                player.sendMessage("¡Ya has votado! Próximo voto disponible en: <b>" + remainingMin + " minuto(s)</b>.");
            } else if (lang == LangEnum.ENGLISH) {
                player.sendMessage("You have already voted! Next vote available in: <b>" + remainingMin + " minute(s)</b>.");
            } else {
                player.sendMessage("Du hast bereits gevotet! Nächster Vote möglich in: <b>" + remainingMin + " Minute(n)</b>.");
            }
            return true;
        }

        int reward = Config.voteReward;
        DatabaseManager.get(AccountData.class).recordVote(account, now, reward);
        SocketManager.GAME_SEND_STATS_PACKET(player);

        if (lang == LangEnum.FRENCH) {
            player.sendMessage("Merci pour votre vote ! Vous avez reçu <b>" + reward + " Points VIP</b>. (Nouveau solde : <b>" + account.getPoints() + " Points</b>)");
        } else if (lang == LangEnum.SPANISH) {
            player.sendMessage("¡Gracias por tu voto! Has recibido <b>" + reward + " Puntos VIP</b>. (Nuevo saldo: <b>" + account.getPoints() + " Puntos</b>)");
        } else if (lang == LangEnum.ENGLISH) {
            player.sendMessage("Thank you for voting! You received <b>" + reward + " VIP Points</b>. (New balance: <b>" + account.getPoints() + " Points</b>)");
        } else {
            player.sendMessage("Vielen Dank für deinen Vote! Du hast <b>" + reward + " VIP-Punkte</b> erhalten. (Neuer Punktestand: <b>" + account.getPoints() + " Punkte</b>)");
        }
        return true;
    }

    private static boolean commandMaster(Player player, String msg) {
        String[] split = msg.split(" ");

        if(split.length == 2) {
            String name = split[1];
            final Player target = World.world.getPlayerByName(name);

            if(target != null && target != player) {
                if(target.getParty() == player.getParty()) {
                    final Party party = target.getParty();
                    party.setChief(target);
                    party.setMaster(target);

                    for(Player member : party.getPlayers())
                        member.send("PL" + target.getId());
                } else {
                    player.sendMessage(player.getLang().trans("command.commandplayer.master.nogroup.name", name));
                }
            }
            return true;
        } else {
            if (player.cantTP()) return true;

            final Party party = player.getParty();

            if (party == null) {
                player.sendMessage(player.getLang().trans("command.commandplayer.master.nogroup"));
                return true;
            }

            final List<Player> players = player.getParty().getPlayers();

            if (!party.getChief().getName().equals(player.getName())) {
                player.sendMessage(player.getLang().trans("command.commandplayer.master.noking"));
                return true;
            }

            if (msg.length() <= 8 && party.getMaster() != null) {
                player.sendMessage(player.getLang().trans("command.commandplayer.master.disabled"));
                players.stream().filter(follower -> follower != party.getMaster())
                        .forEach(follower -> SocketManager.GAME_SEND_MESSAGE(follower, follower.getLang().trans("command.commandplayer.master.nofollow", party.getMaster().getName())));
                party.setMaster(null);
                return true;
            }

            Player target = player;

            if (msg.length() > 8) {
                String name = msg.substring(8, msg.length() - 1);
                target = World.world.getPlayerByName(name);
            }

            if (target == null) {
                player.sendMessage(player.getLang().trans("command.commandplayer.master.noavailable"));
                return true;
            }
            if (target.getParty() == null || !target.getParty().getPlayers().contains(player)) {
                player.sendMessage(player.getLang().trans("command.commandplayer.master.nogroup.atuser"));
                return true;
            }

            party.setMaster(target);

            final String message = player.getLang().trans("command.commandplayer.master.follow", target.getName());
            for (Player follower : players)
                if (follower != target)
                    SocketManager.GAME_SEND_MESSAGE(follower, message);

            party.moveAllPlayersToMaster(null, false);
            SocketManager.GAME_SEND_MESSAGE(target, target.getLang().trans("command.commandplayer.master.master"));
            return true;
        }
    }

    private static boolean commandSkipLoot(Player player, String msg) {
        String lower = msg.toLowerCase().trim();
        boolean isAll = lower.contains("all") || lower.contains("alle");

        boolean targetState;
        if (lower.contains(" on") || lower.contains(" an") || lower.contains(" ein")) {
            targetState = true;
        } else if (lower.contains(" off") || lower.contains(" aus")) {
            targetState = false;
        } else {
            targetState = !player.isSkipLootPanel();
        }

        if (isAll) {
            String ip = player.getAccount() != null ? player.getAccount().getCurrentIp() : "";
            for (Player p : World.world.getOnlinePlayers()) {
                if (p == null) continue;
                boolean match = false;
                if (player.getParty() != null && player.getParty().getPlayers().contains(p)) {
                    match = true;
                } else if (!ip.isEmpty() && p.getAccount() != null && ip.equalsIgnoreCase(p.getAccount().getCurrentIp())) {
                    match = true;
                }
                if (match) {
                    p.setSkipLootPanel(targetState);
                    if (p.getId() != player.getId()) {
                        p.sendMessage("[Kampf] Automatisches Schließen des Kampf-Ergebnisfensters: <b>" 
                            + (targetState ? "<font color='#00FF00'>AKTIVIERT</font>" : "<font color='#FF0000'>DEAKTIVIERT</font>") + "</b>.");
                    }
                }
            }
            player.sendMessage("[Kampf] Automatisches Schließen des Kampf-Ergebnisfensters für <b>alle Gruppen/Account-Charaktere</b>: <b>" 
                + (targetState ? "<font color='#00FF00'>AKTIVIERT</font>" : "<font color='#FF0000'>DEAKTIVIERT</font>") + "</b>.");
        } else {
            player.setSkipLootPanel(targetState);
            player.sendMessage("[Kampf] Automatisches Schließen des Kampf-Ergebnisfensters: <b>" 
                + (targetState ? "<font color='#00FF00'>AKTIVIERT</font>" : "<font color='#FF0000'>DEAKTIVIERT</font>") + "</b>.");
        }
        return true;
    }

    private static boolean commandHelp(Player player, String msg) {
        player.sendMessage("================ <b>Befehlsübersicht</b> ================");
        player.sendMessage("<b>Allgemeine Befehle:</b>");
        player.sendMessage("• <b>.help</b> - Zeigt diese Befehlsübersicht an.");
        player.sendMessage("• <b>.infos</b> - Server-Status, Uptime und Spielerzahlen.");
        player.sendMessage("• <b>.start</b> / <b>.astrub</b> - Teleportiert dich zurück nach Astrub.");
        player.sendMessage("• <b>.savepos</b> - Speichert deine aktuelle Position.");
        player.sendMessage("• <b>.deblo</b> - Befreit dich, falls du auf einer Zelle feststeckst.");
        player.sendMessage("• <b>.group</b> - Lädt automatisch alle deine offenen Accounts in eine Gruppe ein.");
        player.sendMessage("• <b>.master [name]</b> - Multi-Account Gruppensteuerung (Folgen / Tauschen).");
        player.sendMessage("• <b>.pass</b> - Automatisches Passen / Beenden des Zuges im Kampf.");
        player.sendMessage("• <b>.interval [1-29]</b> - Verzögerung zwischen Aktionen der Mules.");
        player.sendMessage("• <b>.all [text]</b> / <b>.noall</b> - Globaler Chatkanal.");
        player.sendMessage("• <b>.walkfast</b> - Sofortiges / schnelles Laufen umschalten.");
        player.sendMessage("• <b>.skiploot [all]</b> - Schließt das Kampf-Ergebnisfenster (Loot-Panel) automatisch.");
        player.sendMessage("• <b>.staff</b> - Zeigt anwesende Teammitglieder.");
        player.sendMessage("• <b>.daily</b> - Tägliche Dungeon-Boss-Quest für VIP-Punkte anzeigen.");
        player.sendMessage("• <b>.vote</b> - Alle 2 Stunden für 50 VIP-Punkte voten.");
        player.sendMessage("• <b>.vip</b> - VIP-Status, Punktestand und Vorteile anzeigen.");
        player.sendMessage(" ");
        player.sendMessage("<b>VIP-Befehle (erfordert VIP-Status):</b>");
        player.sendMessage("• <b>.bank</b> - Bankfach überall und jederzeit öffnen.");
        player.sendMessage("• <b>.vie</b> / <b>.life</b> - Lebenspunkte sofort vollständig regenerieren.");
        player.sendMessage("• <b>.transfer [kategorie]</b> - Schneller Item-Transfer (Handel, Master, Bank).");
        player.sendMessage("• <b>.crush [all | level]</b> - Blitzschnelles Zerkleinern von Items zu Runen.");
        player.sendMessage("=================================================");
        return true;
    }

    //region Commands

    private static boolean commandAll(Player player, String msg) {
        if (player.isInPrison())
            return true;
        if(canalMute && player.getGroup() == null) {
            player.sendMessage(player.getLang().trans("command.commandplayer.commandall.unvailable"));
            return true;
        }
        if (player.noall) {
            player.sendMessage(player.getLang().trans("command.commandplayer.noall"));
            return true;
        }
        if (player.getGroup() == null && System.currentTimeMillis() - player.getGameClient().timeLastTaverne < 10000) {
            player.sendMessage(player.getLang().trans("command.commandplayer.allwait").replace("#1", String.valueOf(10 - ((System.currentTimeMillis() - player.getGameClient().timeLastTaverne) / 1000))));
            return true;
        }

        player.getGameClient().timeLastTaverne = System.currentTimeMillis();

        String prefix = "<font color='#C35617'>[" + (new SimpleDateFormat("HH:mm").format(new Date(System.currentTimeMillis()))) + "] (" + canal + ") (" + (Config.gameServerKey.isEmpty() ? getNameServerById(Config.gameServerId) : Config.gameServerKey) + ") <b><a href='asfunction:onHref,ShowPlayerPopupMenu," + player.getName() + "'>" + player.getName() + "</a></b>";

        Logging.getInstance().write("AllMessage", "[" + (new SimpleDateFormat("HH:mm").format(new Date(System.currentTimeMillis()))) + "] : " + player.getName() + " : " + msg.substring(5, msg.length() - 1));

        final String message = "Im116;" + prefix + "~" + msg.substring(5, msg.length()).replace(";", ":").replace("~", "").replace("|", "").replace("<", "").replace(">", "") + "</font>";

        World.world.getOnlinePlayers().stream().filter(p -> !p.noall).forEach(p -> p.send(message));
        Config.exchangeClient.send("DM" + player.getName() + ";" + getNameServerById(Config.gameServerId) + ";" + msg.substring(5, msg.length()).replace("\n", "").replace("\r", "").replace(";", ":").replace("~", "").replace("|", "").replace("<", "").replace(">", ""));
        return true;
    }

    private static boolean commandNoAll(Player player, String msg) {
        if (player.noall) {
            player.noall = false;
            player.sendMessage(player.getLang().trans("command.commandplayer.all.on"));
        } else {
            player.noall = true;
            player.sendMessage(player.getLang().trans("command.commandplayer.all.off"));
        }
        return true;
    }

    private static boolean commandDeblo(Player player, String msg) {
        if (player.cantTP())
            return true;
        if (player.getFight() != null)
            return true;
        if (player.getCurCell().isWalkable(false)) {
            player.sendMessage(player.getLang().trans("command.commandplayer.deblo.no"));
            return true;
        }
        player.teleport(player.getCurMap().getId(), player.getCurMap().getRandomFreeCellId());
        return true;
    }

    private static boolean commandInfos(Player player, String msg) {
        long uptime = System.currentTimeMillis()
                - Config.startTime;
        int jour = (int) (uptime / (1000 * 3600 * 24));
        uptime %= (1000 * 3600 * 24);
        int hour = (int) (uptime / (1000 * 3600));
        uptime %= (1000 * 3600);
        int min = (int) (uptime / (1000 * 60));
        uptime %= (1000 * 60);
        int sec = (int) (uptime / (1000));
        int nbPlayer = (int) Config.gameServer.getClients().stream().filter(gc -> gc != null && gc.getPlayer() != null).count();

        String mess = player.getLang().trans("command.commandplayer.info.uptime", String.valueOf(jour), String.valueOf(hour), String.valueOf(min), String.valueOf(sec));
        if (nbPlayer > 0)
            mess += player.getLang().trans("command.commandplayer.info.online", String.valueOf(nbPlayer));
        player.sendMessage(mess);
        return true;
    }

    private static boolean commandStaff(Player player, String msg) {
        String message = player.getLang().trans("command.commandplayer.staff");
        boolean vide = true;
        for (Player target : World.world.getOnlinePlayers()) {
            if (target == null)
                continue;
            if (target.getGroup() == null || target.isInvisible())
                continue;

            message += "\n- <b><a href='asfunction:onHref,ShowPlayerPopupMenu," + target.getName() + "'>[" + target.getGroup().getName() + "] " + target.getName() + "</a></b>";
            vide = false;
        }
        if (vide)
            message = player.getLang().trans("command.commandplayer.staff.none");
        player.sendMessage(message);
        return true;
    }
    private static boolean command(String msg, String command) {
        return msg.length() > command.length() && msg.substring(1, command.length() + 1).equalsIgnoreCase(command);
    }
    //endregion

    private static String getNameServerById(int id) {
        switch (id) {
            case 13:
                return "Silouate";
            case 19:
                return "Allister";
            case 22:
                return "Oto Mustam";
            case 1:
                return "Jiva";
            case 2:
                return "Zeus";
            case 37:
                return "Nostalgy";

            case 4001:
                return "Alma";
            case 4002:
                return "Aguabrial";
            case 4005:
                return "Bolgrot";
        }
        return "Unknown";
    }
}
