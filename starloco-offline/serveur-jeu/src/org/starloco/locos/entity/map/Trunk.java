
package org.starloco.locos.entity.map;

import org.starloco.locos.client.Account;
import org.starloco.locos.client.Player;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.game.BankData;
import org.starloco.locos.database.data.game.TrunkData;
import org.starloco.locos.database.data.login.PlayerData;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class Trunk {

    private int id;
    private int houseId;
    private int mapId;
    private int cellId;
    private String key;
    private int ownerId;
    private long kamas;
    private Player player = null;
    private Map<Integer, GameObject> object = new HashMap<>();

    public Trunk(int id, int houseId, int mapId, int cellId) {
        this.id = id;
        this.houseId = houseId;
        this.mapId = mapId;
        this.cellId = cellId;
    }

    public static void closeCode(Player P) {
        SocketManager.GAME_SEND_KODE(P, "V");
    }

    public static Optional<Trunk> getTrunkIdByCoord(int map_id, int cell_id) {
        for (Entry<Integer, Trunk> trunk : World.world.getTrunks().entrySet())
            if (trunk.getValue().getMapId() == map_id && trunk.getValue().getCellId() == cell_id)
                return Optional.ofNullable(trunk.getValue());
        return Optional.empty();
    }

    public static void lock(Player P, String packet) {
        Trunk t = (Trunk) P.getExchangeAction().getValue();
        if (t == null)
            return;
        if (t.isTrunk(P, t)) {
            ((TrunkData) DatabaseManager.get(TrunkData.class)).updateCode(P, t, packet); //Change le code
            t.setKey(packet);
            closeCode(P);
        } else {
            closeCode(P);
        }
        P.setExchangeAction(null);
    }

    public static void open(Player P, String packet, boolean isTrunk) {//Ouvrir un coffre
        Trunk t = (Trunk) P.getExchangeAction().getValue();
        if (t == null)
            return;
        if (packet.compareTo(t.getKey()) == 0 || isTrunk)//Si c'est chez lui ou que le mot de passe est bon
        {
            t.player = P;
            SocketManager.GAME_SEND_ECK_PACKET(P.getGameClient(), 5, "");
            SocketManager.GAME_SEND_EL_TRUNK_PACKET(P, t);
            closeCode(P);
            P.setExchangeAction(new ExchangeAction<>(ExchangeAction.IN_TRUNK, t));
        } else if (packet.compareTo(t.getKey()) != 0)//Mauvais code
        {
            SocketManager.GAME_SEND_KODE(P, "KE");
            closeCode(P);
            P.setExchangeAction(null);
        }
    }

    public static Stream<Trunk> getTrunksByHouse(House h) {
        return World.world.getTrunks().values().stream().filter(trunk -> trunk.getHouseId() == h.getId());
    }

    public synchronized void setObjects(String object) {
        for (String item : object.split("\\|")) {
            if (item.equals(""))
                continue;
            String[] infos = item.split(":");
            int guid = Integer.parseInt(infos[0]);

            GameObject obj = World.world.getGameObject(guid);
            if (obj == null)
                continue;
            this.object.put(obj.getGuid(), obj);
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getHouseId() {
        return houseId;
    }

    public void setHouseId(int houseId) {
        this.houseId = houseId;
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public int getCellId() {
        return cellId;
    }

    public void setCellId(int cellId) {
        this.cellId = cellId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public synchronized long getKamas() {
        return kamas;
    }

    public synchronized void setKamas(long kamas) {
        this.kamas = kamas;
    }

    public synchronized long transferKamas(Player target, long requested) {
        if (target == null || requested == 0)
            return 0;

        if (requested > 0) {
            long available = Math.min(requested, target.getKamas());
            long capacity = Integer.MAX_VALUE - this.kamas;
            long transferred = Math.min(available, Math.max(0, capacity));
            if (transferred <= 0 || !target.addKamas(-transferred))
                return 0;

            this.kamas += transferred;
            return transferred;
        }

        long wanted = requested == Long.MIN_VALUE ? Long.MAX_VALUE : -requested;
        long transferred = Math.min(wanted, this.kamas);
        if (transferred <= 0 || target.getKamas() > Long.MAX_VALUE - transferred)
            return 0;

        if (!target.addKamas(transferred))
            return 0;
        this.kamas -= transferred;
        return transferred;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public synchronized Map<Integer, GameObject> getObject() {
        return object;
    }

    public synchronized void setObject(Map<Integer, GameObject> object) {
        this.object = object;
    }

    public void Lock(Player P) {
        P.setExchangeAction(new ExchangeAction<>(ExchangeAction.LOCK_TRUNK, this));
        SocketManager.GAME_SEND_KODE(P, "CK1|8");
    }

    public void enter(Player player) {
        if (player.getFight() != null || player.getExchangeAction() != null)
            return;

        House house = World.world.getHouse(getHouseId());

        if (house.getOwnerId() == player.getAccID() && this.getOwnerId() != player.getAccID())
            this.setOwnerId(player.getAccID());
        if (this.getOwnerId() == player.getAccID() || (player.getGuild() != null && player.getGuild().getId() == house.getGuildId() && house.canDo(Constant.C_GNOCODE))) {
            player.setExchangeAction(new ExchangeAction<>(ExchangeAction.IN_TRUNK, this));
            open(player, "-", true);
        } else if(player.getGuild() != null && player.getGuild().getId() == house.getGuildId() && !house.canDo(Constant.C_GNOCODE)) {
            player.setExchangeAction(new ExchangeAction<>(ExchangeAction.IN_TRUNK, this));
            SocketManager.GAME_SEND_KODE(player, "CK0|8");
        } else if (player.getGuild() == null && house.canDo(Constant.C_OCANTOPEN)) {
            SocketManager.GAME_SEND_MESSAGE(player, player.getLang().trans("area.map.entity.trunk.enter.guilde.only"));
        } else if (this.getOwnerId() > 0) {
            SocketManager.GAME_SEND_KODE(player, "CK0|8");
        }
    }

    public boolean isTrunk(Player P, Trunk t)//Savoir si c'est son coffre
    {
        return t.getOwnerId() == P.getAccID();
    }

    public synchronized String parseToTrunkPacket() {
        StringBuilder packet = new StringBuilder();

        for (GameObject obj : this.object.values())
            packet.append("O").append(obj.encodeItem()).append(";");
        if (getKamas() != 0)
            packet.append("G").append(getKamas());
        return packet.toString();
    }

    public synchronized void addInTrunk(int guid, int qua, Player P) {
        if (P == null || qua <= 0)
            return;
        ExchangeAction<?> exchangeAction = P.getExchangeAction();
        if (exchangeAction == null || exchangeAction.getType() != ExchangeAction.IN_TRUNK
                || exchangeAction.getValue() != this) {
            return;
        }

        if (this.object.size() >= 10000) // Le plus grand c'est pour si un admin ajoute des objets via la bdd...
        {
            SocketManager.GAME_SEND_MESSAGE(P, P.getLang().trans("area.map.entity.trunk.addintrunk.max"));
            return;
        }

        GameObject PersoObj = World.world.getGameObject(guid);
        if (PersoObj == null)
            return;
        if(PersoObj.isAttach()) return;
        //Si le joueur n'a pas l'item dans son sac ...
        if (P.getItems().get(guid) == null)
            return;
        String str = "";

        //Si c'est un item �quip� ...
        if (PersoObj.getPosition() != Constant.ITEM_POS_NO_EQUIPED)
            return;

        GameObject TrunkObj = getSimilarTrunkItem(PersoObj);
        int newQua = PersoObj.getQuantity() - qua;
        if (TrunkObj == null)//S'il n'y pas d'item du meme Template
        {
            //S'il ne reste pas d'item dans le sac
            if (newQua <= 0) {
                //On enleve l'objet du sac du joueur
                P.removeItem(PersoObj.getGuid());
                //On met l'objet du sac dans le coffre, avec la meme quantit�
                this.object.put(PersoObj.getGuid(), PersoObj);
                str = "O+" + PersoObj.getGuid() + "|" + PersoObj.getQuantity()
                        + "|" + PersoObj.getTemplate().getId() + "|"
                        + PersoObj.encodeStats();
                SocketManager.GAME_SEND_REMOVE_ITEM_PACKET(P, guid);
            } else
            //S'il reste des objets au joueur
            {
                //on modifie la quantit� d'item du sac
                PersoObj.setQuantity(newQua);
                //On ajoute l'objet au coffre et au monde
                TrunkObj = PersoObj.getClone(qua, true);
                World.world.addGameObject(TrunkObj);
                this.object.put(TrunkObj.getGuid(), TrunkObj);
                //Envoie des packets
                str = "O+" + TrunkObj.getGuid() + "|" + TrunkObj.getQuantity()
                        + "|" + TrunkObj.getTemplate().getId() + "|"
                        + TrunkObj.encodeStats();
                SocketManager.GAME_SEND_OBJECT_QUANTITY_PACKET(P, PersoObj);
            }
        } else
        // S'il y avait un item du meme template
        {
            //S'il ne reste pas d'item dans le sac
            if (newQua <= 0) {
                //On enleve l'objet du sac du joueur
                P.removeItem(PersoObj.getGuid());
                //On enleve l'objet du monde
                World.world.removeGameObject(PersoObj.getGuid());
                //On ajoute la quantit� a l'objet dans le coffre
                TrunkObj.setQuantity(TrunkObj.getQuantity()
                        + PersoObj.getQuantity());
                //on envoie l'ajout au coffre de l'objet
                str = "O+" + TrunkObj.getGuid() + "|" + TrunkObj.getQuantity()
                        + "|" + TrunkObj.getTemplate().getId() + "|"
                        + TrunkObj.encodeStats();
                //on envoie la supression de l'objet du sac au joueur
                SocketManager.GAME_SEND_REMOVE_ITEM_PACKET(P, guid);
            } else
            //S'il restait des objets
            {
                //on modifie la quantit� d'item du sac
                PersoObj.setQuantity(newQua);
                TrunkObj.setQuantity(TrunkObj.getQuantity() + qua);
                str = "O+" + TrunkObj.getGuid() + "|" + TrunkObj.getQuantity()
                        + "|" + TrunkObj.getTemplate().getId() + "|"
                        + TrunkObj.encodeStats();
                SocketManager.GAME_SEND_OBJECT_QUANTITY_PACKET(P, PersoObj);
            }
        }

        for (Player perso : P.getCurMap().getPlayers())
            if (perso.getExchangeAction() != null && perso.getExchangeAction().getType() == ExchangeAction.IN_TRUNK && getId() == ((Trunk) perso.getExchangeAction().getValue()).getId())
                SocketManager.GAME_SEND_EsK_PACKET(perso, str);

        SocketManager.GAME_SEND_Ow_PACKET(P);
        ((TrunkData) DatabaseManager.get(TrunkData.class)).update(this);
        ((PlayerData) DatabaseManager.get(PlayerData.class)).update(P);
    }

    static final class ObjectWithdrawal {
        final GameObject withdrawn;
        final GameObject remaining;

        private ObjectWithdrawal(GameObject withdrawn, GameObject remaining) {
            this.withdrawn = withdrawn;
            this.remaining = remaining;
        }
    }

    synchronized ObjectWithdrawal withdrawObject(
            int guid, int requested,
            BiFunction<GameObject, Integer, GameObject> partialFactory) {
        if (requested <= 0)
            return null;

        GameObject source = this.object.get(guid);
        if (source == null || source.getQuantity() <= 0)
            return null;

        int withdrawnQuantity = Math.min(requested, source.getQuantity());
        if (withdrawnQuantity == source.getQuantity()) {
            this.object.remove(guid);
            return new ObjectWithdrawal(source, null);
        }

        if (partialFactory == null)
            return null;
        GameObject withdrawn = partialFactory.apply(source, withdrawnQuantity);
        if (withdrawn == null || withdrawn == source
                || withdrawn.getGuid() == source.getGuid()
                || withdrawn.getQuantity() != withdrawnQuantity) {
            return null;
        }

        source.setQuantity(source.getQuantity() - withdrawnQuantity);
        return new ObjectWithdrawal(withdrawn, source);
    }

    public synchronized void removeFromTrunk(int guid, int qua, Player P) {
        if (P == null || qua <= 0)
            return;
        ExchangeAction<?> exchangeAction = P.getExchangeAction();
        if (exchangeAction == null || exchangeAction.getType() != ExchangeAction.IN_TRUNK
                || exchangeAction.getValue() != this) {
            return;
        }

        ObjectWithdrawal withdrawal = withdrawObject(
                guid, qua, (source, quantity) -> source.getClone(quantity, true));
        if (withdrawal == null)
            return;

        World.world.addGameObject(withdrawal.withdrawn);
        boolean addedAsNewStack = P.addItem(withdrawal.withdrawn, true, false);
        if (!addedAsNewStack)
            World.world.removeGameObject(withdrawal.withdrawn.getGuid());

        String str;
        if (withdrawal.remaining == null) {
            str = "O-" + guid;
        } else {
            str = "O+" + withdrawal.remaining.getGuid() + "|"
                    + withdrawal.remaining.getQuantity() + "|"
                    + withdrawal.remaining.getTemplate().getId() + "|"
                    + withdrawal.remaining.encodeStats();
        }

        for (Player perso : P.getCurMap().getPlayers())
            if (perso.getExchangeAction() != null
                    && perso.getExchangeAction().getType() == ExchangeAction.IN_TRUNK
                    && getId() == ((Trunk) perso.getExchangeAction().getValue()).getId())
                SocketManager.GAME_SEND_EsK_PACKET(perso, str);

        if (addedAsNewStack)
            SocketManager.GAME_SEND_Ow_PACKET(P);
        ((TrunkData) DatabaseManager.get(TrunkData.class)).update(this);
        ((PlayerData) DatabaseManager.get(PlayerData.class)).update(P);
    }

    private GameObject getSimilarTrunkItem(GameObject obj) {
        for (GameObject object : this.object.values())
            if(World.world.getConditionManager().stackIfSimilar(object, obj, true))
                return object;
        return null;
    }

    public synchronized String parseTrunkObjetsToDB() {
        StringBuilder str = new StringBuilder();
        for (Entry<Integer, GameObject> entry : this.object.entrySet()) {
            GameObject obj = entry.getValue();
            str.append(obj.getGuid()).append("|");
        }
        return str.toString();
    }

    public synchronized void moveTrunkToBank(Account Cbank) {
        for (Entry<Integer, GameObject> obj : this.object.entrySet())
            Cbank.getBank().add(obj.getValue());
        this.object.clear();
        ((TrunkData) DatabaseManager.get(TrunkData.class)).update(this);
        ((BankData) DatabaseManager.get(BankData.class)).update(Cbank);
    }
}
