package org.starloco.locos.entity.exchange;

import org.starloco.locos.client.Player;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.database.data.login.PlayerData;
import org.starloco.locos.game.world.World.Couple;
import org.starloco.locos.job.JobAction;
import org.starloco.locos.job.JobStat;
import org.starloco.locos.object.GameObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CraftSecure extends PlayerExchange {

    private long payKamas = 0;
    private long payIfSuccessKamas = 0;
    private int maxCase = 9;

    private ArrayList<Couple<Integer, Integer>> payItems = new ArrayList<>();
    private ArrayList<Couple<Integer, Integer>> payItemsIfSuccess = new ArrayList<>();

    public CraftSecure(Player player1, Player player2) {
        super(player1, player2);
        JobAction action = this.currentJobAction();
        if (action != null && action.isCraft()) {
            // The action is authoritative. In particular, ethereal repairs use
            // three UI slots even at job level 10, while an ordinary level-10
            // craft only exposes two.
            this.maxCase = action.getMin();
        } else {
            this.maxCase = 0;
            this.cancel();
        }
    }

    public Player getNeeder() {
        return player2;
    }

    public int getMaxCase() {
        return maxCase;
    }

    static long capPaymentComponent(long requested, long otherComponent, long payerBalance) {
        if (requested < 0)
            return -1;

        long balance = Math.max(0, payerBalance);
        long other = Math.max(0, otherComponent);
        long remaining = balance - Math.min(balance, other);
        return Math.min(requested, remaining);
    }

    static int capItemQuantity(int requested, int owned, long alreadyCommitted) {
        if (requested <= 0 || owned <= 0 || alreadyCommitted < 0)
            return 0;

        long remaining = (long) owned - Math.min((long) owned, alreadyCommitted);
        return (int) Math.min((long) requested, remaining);
    }

    static final class KamasReservation {
        private final Player crafter;
        private final Player payer;
        private final long guaranteedPayment;
        private final long reservedPayment;
        private boolean pending = true;

        private KamasReservation(Player crafter, Player payer,
                                 long guaranteedPayment, long reservedPayment) {
            this.crafter = crafter;
            this.payer = payer;
            this.guaranteedPayment = guaranteedPayment;
            this.reservedPayment = reservedPayment;
        }

        static KamasReservation reserve(Player crafter, Player payer,
                                        long guaranteedPayment, long successPayment) {
            if (crafter == null || payer == null
                    || guaranteedPayment < 0 || successPayment < 0
                    || guaranteedPayment > Long.MAX_VALUE - successPayment) {
                return null;
            }

            long total = guaranteedPayment + successPayment;
            if (payer.getKamas() < total
                    || crafter.getKamas() > Long.MAX_VALUE - total
                    || (total > 0 && !payer.addKamas(-total))) {
                return null;
            }
            return new KamasReservation(crafter, payer, guaranteedPayment, total);
        }

        boolean settle(boolean success) {
            if (!pending)
                return false;

            long payment = success ? reservedPayment : guaranteedPayment;
            long refund = reservedPayment - payment;
            if (refund > 0)
                payer.addKamas(refund);
            if (payment > 0)
                crafter.addKamas(payment);
            pending = false;
            return true;
        }

        void refund() {
            if (!pending)
                return;
            if (reservedPayment > 0)
                payer.addKamas(reservedPayment);
            pending = false;
        }
    }

    public synchronized void apply() {
        if (!this.ok1 || !this.ok2)
            return;
        this.ok1 = false;
        this.ok2 = false;

        if (this.player1.getIsCraftingType().size() < 2) {
            this.sendReadyState();
            return;
        }
        int skillId = this.player1.getIsCraftingType().get(1);
        JobStat jobStat = this.player1.getMetierBySkill(skillId);

        if (jobStat == null) {
            this.sendReadyState();
            return;
        }

        JobAction jobAction = jobStat.getJobActionBySkill(skillId);

        if (jobAction == null) {
            this.sendReadyState();
            return;
        }

        Player first = JobAction.compareInventoryLockOrder(
                this.player1, this.player2) <= 0 ? this.player1 : this.player2;
        Player second = first == this.player1 ? this.player2 : this.player1;
        synchronized (first.getItems()) {
            synchronized (second.getItems()) {
                this.applyLocked(jobStat, jobAction);
            }
        }
    }

    private void applyLocked(JobStat jobStat, JobAction jobAction) {
        Map<Player, ArrayList<Couple<Integer, Integer>>> items = new HashMap<>();
        items.put(this.player1, this.items1);
        items.put(this.player2, this.items2);

        if (!this.hasValidCraftItems(jobAction)) {
            SocketManager.GAME_SEND_Ec_PACKET(this.player1, "EI");
            SocketManager.GAME_SEND_Ec_PACKET(this.player2, "EI");
            this.sendReadyState();
            return;
        }

        KamasReservation payment = KamasReservation.reserve(
                this.player1, this.player2, this.payKamas, this.payIfSuccessKamas);
        if (payment == null) {
            SocketManager.GAME_SEND_Im_PACKET(this.player2, "182");
            this.sendReadyState();
            return;
        }

        boolean settled = false;
        try {
            JobAction.CraftExecution execution = jobAction.executePublicCraft(
                    this.player1, this.player2, items);
            if (!execution.isCompleted()) {
                payment.refund();
                settled = true;
                return;
            }

            boolean success = execution.isSuccess();
            Map<Integer, Integer> objectPayments = success
                    ? aggregateItemPayments(this.payItems,
                    this.payItemsIfSuccess)
                    : aggregateItemPayments(this.payItems);
            if (objectPayments == null || !this.giveObjects(objectPayments)) {
                payment.refund();
                settled = true;
                SocketManager.GAME_SEND_Ec_PACKET(this.player1, "EI");
                SocketManager.GAME_SEND_Ec_PACKET(this.player2, "EI");
                return;
            }

            settled = payment.settle(success);
            if (!settled)
                throw new IllegalStateException("Secure-craft payment was already settled");

            long winXP = execution.getExperience();
            if (winXP > 0) {
                jobStat.addXp(this.player1, winXP);
                ArrayList<JobStat> SMs = new ArrayList<>();
                SMs.add(jobStat);
                SocketManager.GAME_SEND_JX_PACKET(this.player1, SMs);
            }

            SocketManager.GAME_SEND_STATS_PACKET(this.player1);
            SocketManager.GAME_SEND_STATS_PACKET(this.player2);
        } catch (RuntimeException | Error exception) {
            if (!settled)
                payment.refund();
            throw exception;
        } finally {
            try {
                this.clearCraftState();
            } finally {
                PlayerData playerData = DatabaseManager.get(PlayerData.class);
                if (playerData != null
                        && playerData.updateKamasAtomically(this.player2, this.player1)) {
                    playerData.update(this.player2);
                    playerData.update(this.player1);
                }
            }
        }
    }

    private void clearCraftState() {
        this.payIfSuccessKamas = 0;
        this.payKamas = 0;
        this.payItems.clear();
        this.payItemsIfSuccess.clear();
        this.items1.clear();
        this.items2.clear();
        this.sendReadyState();
    }

    private boolean hasValidCraftItems(JobAction jobAction) {
        if (!this.hasValidIngredients(jobAction, this.player1, this.items1)
                || !this.hasValidIngredients(jobAction, this.player2, this.items2)) {
            return false;
        }

        Map<Integer, Long> committed = new HashMap<>();

        for (Couple<Integer, Integer> ingredient : this.items2) {
            if (!addCommittedQuantity(committed, ingredient))
                return false;
        }

        return this.addValidPayments(this.payItems, committed)
                && this.addValidPayments(this.payItemsIfSuccess, committed);
    }

    private boolean hasValidIngredients(JobAction jobAction, Player owner,
                                        ArrayList<Couple<Integer, Integer>> ingredients) {
        Map<Integer, Long> committed = new HashMap<>();
        for (Couple<Integer, Integer> ingredient : ingredients) {
            if (ingredient == null || ingredient.first == null
                    || ingredient.second == null || ingredient.second <= 0)
                return false;
            GameObject object = owner.getItems().get(ingredient.first);
            if (!jobAction.isSelectableCraftIngredient(object)
                    || ingredient.second > object.getQuantity()) {
                return false;
            }
            if (!addCommittedQuantity(committed, ingredient)
                    || committed.get(ingredient.first) > object.getQuantity())
                return false;
        }
        return true;
    }

    private boolean addValidPayments(ArrayList<Couple<Integer, Integer>> payments,
                                     Map<Integer, Long> committed) {
        for (Couple<Integer, Integer> payment : payments) {
            if (payment == null || payment.first == null
                    || payment.second == null || payment.second <= 0)
                return false;

            GameObject object = this.player2.getItems().get(payment.first);
            if (!JobAction.isUsablePublicCraftIngredient(object)
                    || this.player1.getItems().containsKey(payment.first)) {
                return false;
            }

            if (!addCommittedQuantity(committed, payment)
                    || committed.get(payment.first) > object.getQuantity())
                return false;
        }
        return true;
    }

    private static boolean addCommittedQuantity(
            Map<Integer, Long> committed, Couple<Integer, Integer> selection) {
        if (selection == null || selection.first == null
                || selection.second == null || selection.second <= 0)
            return false;
        long previous = committed.getOrDefault(selection.first, 0L);
        if (previous > Long.MAX_VALUE - selection.second)
            return false;
        committed.put(selection.first, previous + selection.second);
        return true;
    }

    private void sendReadyState() {
        this.ok1 = false;
        this.ok2 = false;
        SocketManager.GAME_SEND_EXCHANGE_OK(this.player1.getGameClient(), false, this.player1.getId());
        SocketManager.GAME_SEND_EXCHANGE_OK(this.player2.getGameClient(), false, this.player1.getId());
        SocketManager.GAME_SEND_EXCHANGE_OK(this.player1.getGameClient(), false, this.player2.getId());
        SocketManager.GAME_SEND_EXCHANGE_OK(this.player2.getGameClient(), false, this.player2.getId());
    }

    @SafeVarargs
    static Map<Integer, Integer> aggregateItemPayments(
            ArrayList<Couple<Integer, Integer>>... arrays) {
        Map<Integer, Integer> aggregated = new LinkedHashMap<>();
        if (arrays == null)
            return null;
        for (ArrayList<Couple<Integer, Integer>> array : arrays) {
            if (array == null)
                return null;
            for (Couple<Integer, Integer> payment : array) {
                if (payment == null || payment.first == null
                        || payment.second == null || payment.second <= 0)
                    return null;
                int previous = aggregated.getOrDefault(payment.first, 0);
                if (previous > Integer.MAX_VALUE - payment.second)
                    return null;
                aggregated.put(payment.first, previous + payment.second);
            }
        }
        return aggregated;
    }

    private boolean giveObjects(Map<Integer, Integer> payments) {
        if (payments == null)
            return false;
        if (payments.isEmpty())
            return true;

        for (Map.Entry<Integer, Integer> payment : payments.entrySet()) {
            GameObject object = this.player2.getItems().get(payment.getKey());
            if (!JobAction.isUsablePublicCraftIngredient(object)
                    || this.player1.getItems().containsKey(payment.getKey())
                    || payment.getValue() <= 0
                    || payment.getValue() > object.getQuantity()) {
                return false;
            }
        }

        ObjectData objectData = DatabaseManager.get(ObjectData.class);
        PlayerData playerData = DatabaseManager.get(PlayerData.class);
        if (objectData == null || playerData == null)
            return false;

        ObjectData.SecurePaymentBatch batch;
        // applyLocked already owns both inventory monitors.  Keep the player
        // snapshot lock and the object-persistence lock from before the SQL
        // transaction until after the corresponding in-memory mutation: an
        // autosave can therefore observe only the old state or the new state.
        synchronized (playerData) {
            synchronized (objectData) {
                batch = objectData.transferSecureCraftPaymentsAtomically(
                        this.player2, this.player1, payments);
                if (batch == null)
                    return false;
                batch.applyToMemory();
            }
        }

        // Network failures are isolated by notifyObjectTransfer and can no
        // longer turn a committed batch into a failed/refunded payment.
        for (ObjectData.SecurePaymentTransfer transfer
                : batch.getTransfers()) {
            this.notifyObjectTransfer(transfer.getSource(),
                    transfer.getTransferred(), transfer.isCompleteStack());
        }
        return true;
    }

    public synchronized void cancel() {
        this.send("EV");
        this.player1.getIsCraftingType().clear();
        this.player2.getIsCraftingType().clear();
        this.player1.setExchangeAction(null);
        this.player2.setExchangeAction(null);
    }

    public synchronized void setPayKamas(byte type, long kamas) {
        if ((type != 1 && type != 2) || kamas < 0)
            return;

        this.sendReadyState();

        switch (type) {
            case 1:// Pay
                this.payKamas = capPaymentComponent(
                        kamas, this.payIfSuccessKamas, this.player2.getKamas());
                this.send("Ep1;G" + this.payKamas);
                break;
            case 2: // PayIfSuccess
                this.payIfSuccessKamas = capPaymentComponent(
                        kamas, this.payKamas, this.player2.getKamas());
                this.send("Ep2;G" + this.payIfSuccessKamas);
                break;
        }
    }

    @Override
    public synchronized void setKamas(int guid, long kamas) {
        // Secure crafting payments use the dedicated EP protocol only.
    }

    @Override
    public synchronized void addItem(int guid, int quantity, int playerGuid) {
        if (quantity <= 0)
            return;

        Player owner;
        if (this.player1.getId() == playerGuid)
            owner = this.player1;
        else if (this.player2.getId() == playerGuid)
            owner = this.player2;
        else
            return;

        synchronized (owner.getItems()) {
            JobAction jobAction = this.currentJobAction();
            GameObject object = owner.getItems().get(guid);
            if (jobAction == null
                    || !jobAction.isSelectableCraftIngredient(object))
                return;

            long committed = this.getQuaItem(guid, playerGuid);
            if (owner == this.player2)
                committed += this.selectedPayItemQuantity(guid);

            int accepted = capItemQuantity(
                    quantity, object.getQuantity(), committed);
            if (accepted > 0)
                super.addItem(guid, accepted, playerGuid);
        }
    }

    @Override
    public synchronized void removeItem(int guid, int quantity, int playerGuid) {
        if (quantity <= 0
                || (this.player1.getId() != playerGuid && this.player2.getId() != playerGuid)) {
            return;
        }
        super.removeItem(guid, quantity, playerGuid);
    }

    public synchronized void setPayItems(byte type, boolean adding, int guid, int quantity) {
        if ((type != 1 && type != 2) || quantity <= 0)
            return;

        synchronized (this.player2.getItems()) {
            GameObject object = this.player2.getItems().get(guid);
            if (!JobAction.isUsablePublicCraftIngredient(object))
                return;

            this.sendReadyState();
            if (adding) {
                this.addItem(object, quantity, type);
            } else {
                this.removeItem(object, quantity, type);
            }
        }
    }

    private JobAction currentJobAction() {
        if (this.player1.getIsCraftingType().size() < 2)
            return null;
        int skillId = this.player1.getIsCraftingType().get(1);
        JobStat job = this.player1.getMetierBySkill(skillId);
        return job == null ? null : job.getJobActionBySkill(skillId);
    }

    private void addItem(GameObject object, int quantity, byte type) {
        if (quantity <= 0)
            return;

        long committed = selectedPayItemQuantity(object.getGuid())
                + this.getQuaItem(object.getGuid(), this.player2.getId());
        int accepted = capItemQuantity(quantity, object.getQuantity(), committed);
        if (accepted <= 0)
            return;
        quantity = accepted;

        ArrayList<Couple<Integer, Integer>> items = (type == 1 ? this.payItems : this.payItemsIfSuccess);
        Couple<Integer, Integer> couple = getCoupleInList(items, object.getGuid());
        String add = "|" + object.getTemplate().getId() + "|" + object.encodeStats();

        if (couple != null) {
            couple.second += quantity;
            this.player2.send("Ep" + type + ";O+" + object.getGuid() + "|" + couple.second);
            this.player1.send("Ep" + type + ";O+" + object.getGuid() + "|" + couple.second + add);
            return;
        }

        items.add(new Couple<>(object.getGuid(), quantity));
        this.player2.send("Ep" + type + ";O+" + object.getGuid() + "|" + quantity);
        this.player1.send("Ep" + type + ";O+" + object.getGuid() + "|" + quantity + add);
    }

    private long selectedPayItemQuantity(int guid) {
        long selected = 0;
        Couple<Integer, Integer> guaranteed = getCoupleInList(this.payItems, guid);
        Couple<Integer, Integer> conditional = getCoupleInList(this.payItemsIfSuccess, guid);
        if (guaranteed != null && guaranteed.second > 0)
            selected += guaranteed.second;
        if (conditional != null && conditional.second > 0)
            selected += conditional.second;
        return selected;
    }

    private void removeItem(GameObject object, int quantity, byte type) {
        if (quantity <= 0)
            return;
        ArrayList<Couple<Integer, Integer>> items = (type == 1 ? this.payItems : this.payItemsIfSuccess);
        Couple<Integer, Integer> couple = getCoupleInList(items, object.getGuid());

        if(couple == null) return;
        int newQua = couple.second - quantity;

        if (newQua < 1) {
            items.remove(couple);
            this.player1.send("Ep" + type + ";O-" + object.getGuid());
            this.player2.send("Ep" + type + ";O-" + object.getGuid());
        } else {
            couple.second = newQua;
            this.player2.send("Ep" + type + ";O+" + object.getGuid() + "|" + newQua);
            this.player1.send("Ep" + type + ";O+" + object.getGuid() + "|" + newQua + "|" + object.getTemplate().getId() + "|" + object.encodeStats());
        }
    }

    private void send(String packet) {
        this.player1.send(packet);
        this.player2.send(packet);
    }
}
