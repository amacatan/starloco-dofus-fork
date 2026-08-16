package org.starloco.locos.job;

import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Stats;
import org.starloco.locos.common.Formulas;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.fight.spells.SpellEffect;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.world.World;
import org.starloco.locos.game.world.World.Couple;
import org.starloco.locos.job.maging.Rune;
import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.kernel.Logging;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import org.starloco.locos.util.RandomStats;

import java.util.*;
import java.util.Map.Entry;

public class JobAction {

    private static final int MAX_CRAFT_REPETITIONS = 10_000;

    public Map<Integer, Integer> ingredients = new TreeMap<>(), lastCraft = new TreeMap<>();
    public Player player;
    public String data = "";
    public boolean broke = false, broken = false, isRepeat = false;
    private final int id;
    private int min = 1, max = 1;
    private final boolean isCraft;
    private int chan = 100, time = 0, xpWin = 0;
    private JobStat SM;
    private JobCraft jobCraft;
    public JobCraft oldJobCraft;
    private int reConfigingRunes = -1;
    private CraftExecution lastMagingExecution = CraftExecution.invalid();
    private CraftExecution lastPublicCommittedExecution = CraftExecution.invalid();

    /**
     * Result of a public/secure craft attempt.  A rejected input or a database
     * failure is not a profession failure: no payment component may be settled
     * for it.  Completed failures, on the other hand, consumed the validated
     * ingredients and are eligible for the guaranteed payment and craft XP.
     */
    public static final class CraftExecution {
        private final boolean completed;
        private final boolean success;
        private final long experience;

        private CraftExecution(boolean completed, boolean success,
                               long experience) {
            this.completed = completed;
            this.success = success;
            this.experience = Math.max(0, experience);
        }

        static CraftExecution invalid() {
            return new CraftExecution(false, false, 0);
        }

        static CraftExecution completed(boolean success, long experience) {
            return new CraftExecution(true, success, experience);
        }

        public boolean isCompleted() {
            return this.completed;
        }

        public boolean isSuccess() {
            return this.success;
        }

        public long getExperience() {
            return this.experience;
        }
    }

    public JobAction(int sk, int min, int max, boolean craft, int arg, int xpWin) {
        this.id = sk;
        this.min = min;
        this.max = max;
        this.isCraft = craft;
        this.xpWin = xpWin;
        if (craft) this.chan = arg;
        else this.time = arg;
    }

    public int getId() {
        return this.id;
    }

    public int getMin() {
        return this.min;
    }

    public int getMax() {
        return this.max;
    }

    public boolean isCraft() {
        return this.isCraft;
    }

    public int getChance() {
        return this.chan;
    }

    public int getTime() {
        return this.time;
    }

    public int getXpWin() {
        return this.xpWin;
    }

    public JobStat getJobStat() {
        return this.SM;
    }

    public synchronized JobCraft getJobCraft() {
        return this.jobCraft;
    }

    public synchronized void setJobCraft(JobCraft jobCraft) {
        this.jobCraft = jobCraft;
    }

    /**
     * Job actions kept by {@link JobStat} describe what the job can do. Craft UI
     * state must not be stored on those shared descriptors: delayed craft tasks
     * from an old exchange would otherwise be able to act on a newly opened one.
     */
    public JobAction createCraftSession(Player sessionPlayer, JobStat jobStat) {
        JobAction session = new JobAction(this.id, this.min, this.max, this.isCraft,
                this.isCraft ? this.chan : this.time, this.xpWin);
        session.player = sessionPlayer;
        session.SM = jobStat;
        return session;
    }

    public synchronized boolean startCraft(Player craftPlayer) {
        if (!this.isCraft || this.jobCraft != null
                || !this.isCurrentCraftSession(craftPlayer)
                || !this.hasAvailableIngredients(craftPlayer))
            return false;

        this.broke = false;
        this.broken = false;
        this.jobCraft = new JobCraft(this, craftPlayer);
        return true;
    }

    public synchronized boolean repeatCraft(Player craftPlayer, int repetitions) {
        if (repetitions <= 0 || repetitions > MAX_CRAFT_REPETITIONS
                || !this.isCurrentCraftSession(craftPlayer))
            return false;

        if (this.ingredients.isEmpty())
            this.putLastCraftIngredients();
        if (!this.hasAvailableIngredients(craftPlayer))
            return false;

        if (this.jobCraft == null)
            this.jobCraft = new JobCraft(this, craftPlayer);
        this.broke = false;
        this.broken = false;
        this.jobCraft.setAction(repetitions);
        return true;
    }

    public synchronized void stopCraft() {
        this.broken = true;
    }

    public synchronized void cancelCraft() {
        this.broke = true;
        this.broken = true;
        this.isRepeat = false;
        this.ingredients.clear();
        this.lastCraft.clear();
        this.oldJobCraft = null;
        this.jobCraft = null;
    }

    private boolean isCurrentCraftSession(Player craftPlayer) {
        if (craftPlayer == null || craftPlayer != this.player)
            return false;
        ExchangeAction<?> exchangeAction = craftPlayer.getExchangeAction();
        return exchangeAction != null
                && exchangeAction.getType() == ExchangeAction.CRAFTING
                && exchangeAction.getValue() == this;
    }

    private boolean hasAvailableIngredients(Player craftPlayer) {
        if (this.ingredients.isEmpty()
                || this.selectedTemplateCount(this.ingredients, craftPlayer) > this.min)
            return false;
        for (Entry<Integer, Integer> ingredient : this.ingredients.entrySet()) {
            GameObject object = craftPlayer.getItems().get(ingredient.getKey());
            if (ingredient.getValue() == null || ingredient.getValue() <= 0
                    || !this.isSelectableCraftIngredient(object)
                    || object.getQuantity() < ingredient.getValue())
                return false;
        }
        return true;
    }

    private int selectedTemplateCount(Map<Integer, Integer> selected,
                                      Player craftPlayer) {
        Set<Integer> templates = new HashSet<>();
        for (Integer guid : selected.keySet()) {
            GameObject object = craftPlayer.getItems().get(guid);
            if (object != null && object.getTemplate() != null)
                templates.add(object.getTemplate().getId());
        }
        return templates.size();
    }

    public synchronized boolean addIngredient(Player player, int id, int quantity) {
        if (quantity == 0 || this.jobCraft != null || !this.isCurrentCraftSession(player))
            return false;

        int oldQuantity = this.ingredients.getOrDefault(id, 0);
        int newQuantity;

        if (quantity > 0) {
            GameObject object = player.getItems().get(id);
            if (!this.isSelectableCraftIngredient(object)
                    || oldQuantity >= object.getQuantity())
                return false;
            if (oldQuantity == 0
                    && !this.containsSelectedTemplate(object.getTemplate().getId())
                    && this.selectedTemplateCount(this.ingredients, player) >= this.min)
                return false;

            long requested = (long) oldQuantity + quantity;
            newQuantity = (int) Math.min(requested, object.getQuantity());
        } else {
            long removed = -(long) quantity;
            if (removed > oldQuantity)
                return false;
            newQuantity = (int) (oldQuantity - removed);
        }

        if (newQuantity == oldQuantity)
            return false;
        if (newQuantity > 0) {
            this.ingredients.put(id, newQuantity);
            SocketManager.GAME_SEND_EXCHANGE_MOVE_OK(player, 'O', "+",
                    id + "|" + newQuantity);
        } else {
            this.ingredients.remove(id);
            SocketManager.GAME_SEND_EXCHANGE_MOVE_OK(player, 'O', "-", String.valueOf(id));
        }
        return true;
    }

    private boolean containsSelectedTemplate(int templateId) {
        for (Integer guid : this.ingredients.keySet()) {
            GameObject selected = this.player.getItems().get(guid);
            if (selected != null && selected.getTemplate() != null
                    && selected.getTemplate().getId() == templateId)
                return true;
        }
        return false;
    }

    public synchronized boolean putLastCraftIngredients() {
        if (this.player == null || this.jobCraft != null || !this.ingredients.isEmpty()
                || !this.isCurrentCraftSession(this.player))
            return false;

        return this.restoreLastCraftIngredients(true);
    }

    synchronized boolean prepareNextRepeat(Player craftPlayer) {
        if (!this.isCurrentCraftSession(craftPlayer) || this.broke || this.broken)
            return false;
        if (this.isMaging())
            return this.selectedTemplateCount(this.ingredients, craftPlayer) <= this.min
                    && this.hasAvailableIngredients(craftPlayer);
        this.ingredients.clear();
        return this.restoreLastCraftIngredients(false);
    }

    synchronized boolean ownsJobCraft(JobCraft craft) {
        return this.jobCraft == craft;
    }

    synchronized void completeSingleCraft(JobCraft craft) {
        if (this.jobCraft != craft)
            return;
        this.oldJobCraft = craft;
        this.jobCraft = null;
    }

    private boolean restoreLastCraftIngredients(boolean sendPackets) {
        if (this.lastCraft.isEmpty()) {
            this.ingredients.clear();
            return false;
        }

        Map<Integer, Integer> restored = new TreeMap<>();
        for (Entry<Integer, Integer> entry : this.lastCraft.entrySet()) {
            GameObject object = this.player.getItems().get(entry.getKey());
            if (!this.isSelectableCraftIngredient(object) || entry.getValue() == null
                    || entry.getValue() <= 0 || object.getQuantity() < entry.getValue()) {
                this.ingredients.clear();
                return false;
            }
            restored.put(entry.getKey(), entry.getValue());
        }

        if (this.selectedTemplateCount(restored, this.player) > this.min) {
            this.ingredients.clear();
            return false;
        }

        this.ingredients.clear();
        this.ingredients.putAll(restored);
        if (sendPackets) {
            for (Entry<Integer, Integer> entry : restored.entrySet())
                SocketManager.GAME_SEND_EXCHANGE_MOVE_OK(this.player, 'O', "+",
                        entry.getKey() + "|" + entry.getValue());
        }
        return this.hasAvailableIngredients(this.player);
    }

    public synchronized void resetCraft() {
        this.cancelCraft();
    }

    /**
     * Compatibility entry point used by older callers.  New secure-craft code
     * consumes the richer execution result so it can distinguish a profession
     * failure from invalid input or an unavailable database.
     */
    public boolean craftPublicMode(Player crafter, Player receiver,
                                   Map<Player, ArrayList<Couple<Integer, Integer>>> list) {
        CraftExecution execution = this.executePublicCraft(crafter, receiver, list);
        return execution.isCompleted() && execution.isSuccess();
    }

    public synchronized CraftExecution executePublicCraft(
            Player crafter, Player receiver,
            Map<Player, ArrayList<Couple<Integer, Integer>>> list) {
        this.lastPublicCommittedExecution = CraftExecution.invalid();
        try {
            if (crafter == null || receiver == null)
                return CraftExecution.invalid();
            Player first = compareInventoryLockOrder(crafter, receiver) <= 0
                    ? crafter : receiver;
            Player second = first == crafter ? receiver : crafter;
            synchronized (first.getItems()) {
                if (second == first)
                    return this.executePublicCraftInternal(crafter, receiver, list);
                synchronized (second.getItems()) {
                    return this.executePublicCraftInternal(crafter, receiver, list);
                }
            }
        } catch (RuntimeException | Error notificationFailure) {
            return preserveCommittedExecution(this.lastPublicCommittedExecution,
                    () -> {
                        throw notificationFailure;
                    });
        }
    }

    public static int compareInventoryLockOrder(Player first, Player second) {
        if (first == second)
            return 0;
        int byId = Integer.compare(first.getId(), second.getId());
        if (byId != 0)
            return byId;
        return Integer.compare(System.identityHashCode(first),
                System.identityHashCode(second));
    }

    private CraftExecution executePublicCraftInternal(
            Player crafter, Player receiver,
            Map<Player, ArrayList<Couple<Integer, Integer>>> list) {
        if (!this.isCraft || crafter == null || receiver == null || list == null)
            return CraftExecution.invalid();

        this.player = crafter;
        JobStat jobStat = crafter.getMetierBySkill(this.id);
        if (jobStat == null || jobStat.getTemplate() == null
                || jobStat.getJobActionBySkill(this.id) == null) {
            sendPublicCraftError(crafter, receiver);
            return CraftExecution.invalid();
        }
        this.SM = jobStat;

        if (this.isEtherealRepair())
            return this.executePublicEtherealRepair(crafter, receiver, list,
                    jobStat);

        if (this.isMaging()) {
            this.lastMagingExecution = CraftExecution.invalid();
            this.craftMaging(false, receiver, list);
            return this.lastMagingExecution;
        }

        PublicCraftSelection selection = this.validatePublicCraftSelection(
                crafter, receiver, list, jobStat);
        if (selection == null) {
            sendPublicCraftError(crafter, receiver);
            return CraftExecution.invalid();
        }

        List<Integer> recipes = jobStat.getTemplate().getListBySkill(this.id);
        int templateId = recipes == null ? -1
                : World.world.getObjectByIngredientForJob(
                        new ArrayList<>(recipes), selection.recipeItems);
        ObjectTemplate resultTemplate = World.world.getObjTemplate(templateId);
        if (templateId == -1 || resultTemplate == null
                || !jobStat.getTemplate().canCraft(this.id, templateId)) {
            sendPublicCraftError(crafter, receiver);
            if (crafter.getCurMap() != null)
                SocketManager.GAME_SEND_IO_PACKET_TO_MAP(crafter.getCurMap(),
                        crafter.getId(), "-");
            return CraftExecution.invalid();
        }

        int ingredientCount = selection.recipeItems.size();
        int chance = JobConstant.getChanceByNbrCaseByLvl(
                jobStat.get_lvl(), ingredientCount);
        boolean success = jobStat.get_lvl() == 100 || this.id == 109
                || chance >= Formulas.getRandomValue(1, 100);

        GameObject result = null;
        if (success) {
            result = resultTemplate.createNewItem(1, false);
            if (result == null) {
                sendPublicCraftError(crafter, receiver);
                return CraftExecution.invalid();
            }
            if (selection.signed) {
                result.addTxtStat(Constant.STATS_SIGNATURE, crafter.getName());
                persistQuantity(result);
            }
        }

        if (!sourcesStillAvailable(selection.sources)) {
            if (result != null)
                removeUnownedCraftResult(result);
            sendPublicCraftError(crafter, receiver);
            return CraftExecution.invalid();
        }
        for (PublicCraftIngredient source : selection.sources.values())
            consumePublicCraftIngredient(source);
        if (result != null) {
            synchronized (receiver.getItems()) {
                receiver.getItems().put(result.getGuid(), result);
            }
            World.world.addGameObject(result);
        }

        long experience = (long) Formulas.calculXpWinCraft(jobStat.get_lvl(),
                ingredientCount) * Config.rateJob;
        if (jobStat.getTemplate().getId() == 28 && experience == 1)
            experience = 10;
        CraftExecution committed = CraftExecution.completed(success, experience);
        this.lastPublicCommittedExecution = committed;

        for (PublicCraftIngredient source : selection.sources.values())
            notifyConsumedPublicCraftIngredient(source);
        if (result != null)
            SocketManager.GAME_SEND_OAKO_PACKET(receiver, result);

        SocketManager.GAME_SEND_Ow_PACKET(crafter);
        SocketManager.GAME_SEND_Ow_PACKET(receiver);
        if (Logging.USE_LOG)
            Logging.getInstance().write("SecureCraft", crafter.getName()
                    + " a crafté avec " + (success ? "SUCCES" : "ECHEC")
                    + " l'item " + templateId + " (" + resultTemplate.getName()
                    + ") pour " + receiver.getName());

        if (!success) {
            SocketManager.GAME_SEND_Ec_PACKET(crafter, "EF");
            SocketManager.GAME_SEND_Ec_PACKET(receiver, "EF");
            if (crafter.getCurMap() != null)
                SocketManager.GAME_SEND_IO_PACKET_TO_MAP(crafter.getCurMap(),
                        crafter.getId(), "-" + templateId);
            SocketManager.GAME_SEND_Im_PACKET(crafter, "0118");
        } else {
            String stats = result.encodeStats();
            crafter.send("ErKO+" + result.getGuid() + "|1|" + templateId
                    + "|" + stats);
            receiver.send("ErKO+" + result.getGuid() + "|1|" + templateId
                    + "|" + stats);
            crafter.send("EcK;" + templateId + ";T" + receiver.getName()
                    + ";" + stats);
            receiver.send("EcK;" + templateId + ";B" + crafter.getName()
                    + ";" + stats);
            if (crafter.getCurMap() != null)
                SocketManager.GAME_SEND_IO_PACKET_TO_MAP(crafter.getCurMap(),
                        crafter.getId(), "+" + templateId);
        }

        return committed;
    }

    private PublicCraftSelection validatePublicCraftSelection(
            Player crafter, Player receiver,
            Map<Player, ArrayList<Couple<Integer, Integer>>> list,
            JobStat jobStat) {
        Map<Integer, PublicCraftIngredient> sources =
                collectPublicCraftSources(crafter, receiver, list);
        if (sources == null || sources.isEmpty())
            return null;
        Map<Integer, Integer> recipeItems = new HashMap<>();
        int signatureQuantity = 0;

        for (PublicCraftIngredient source : sources.values()) {
            int templateId = source.object.getTemplate().getId();
            if (templateId == 7508) {
                try {
                    signatureQuantity = Math.addExact(
                            signatureQuantity, source.quantity);
                } catch (ArithmeticException overflow) {
                    return null;
                }
                continue;
            }
            try {
                recipeItems.merge(templateId, source.quantity, Math::addExact);
            } catch (ArithmeticException overflow) {
                return null;
            }
        }

        if (recipeItems.isEmpty()
                || recipeItems.size() + (signatureQuantity == 1 ? 1 : 0) > this.min
                || (signatureQuantity != 0
                && (signatureQuantity != 1 || jobStat.get_lvl() != 100)))
            return null;
        return new PublicCraftSelection(sources, recipeItems,
                signatureQuantity == 1);
    }

    private static Map<Integer, PublicCraftIngredient> collectPublicCraftSources(
            Player crafter, Player receiver,
            Map<Player, ArrayList<Couple<Integer, Integer>>> list) {
        Map<Integer, PublicCraftIngredient> sources = new LinkedHashMap<>();
        for (Entry<Player, ArrayList<Couple<Integer, Integer>>> ownerEntry
                : list.entrySet()) {
            Player owner = ownerEntry.getKey();
            if ((owner != crafter && owner != receiver)
                    || ownerEntry.getValue() == null)
                return null;
            for (Couple<Integer, Integer> offered : ownerEntry.getValue()) {
                if (offered == null || offered.first == null
                        || offered.second == null || offered.second <= 0)
                    return null;
                GameObject object = owner.getItems().get(offered.first);
                if (!isUsablePublicCraftIngredient(object))
                    return null;

                PublicCraftIngredient source = sources.get(offered.first);
                if (source != null && source.owner != owner)
                    return null;
                long combined = (long) offered.second
                        + (source == null ? 0 : source.quantity);
                if (combined > object.getQuantity() || combined > Integer.MAX_VALUE)
                    return null;
                sources.put(offered.first, new PublicCraftIngredient(
                        owner, object, (int) combined));
            }
        }
        return sources;
    }

    public static boolean isUsablePublicCraftIngredient(GameObject object) {
        if (object == null || object.getTemplate() == null
                || object.getQuantity() <= 0 || object.isAttach()
                || object.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                || hasObvijevanAttachment(object)
                || object.getTxtStat().containsKey(Constant.STATS_MIMIBIOTE))
            return false;
        return true;
    }

    public boolean isSelectableCraftIngredient(GameObject object) {
        if (object == null || object.getTemplate() == null
                || object.getQuantity() <= 0 || object.isAttach()
                || object.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                || hasObvijevanAttachment(object))
            return false;
        // Forgemaging changes equipment without consuming its identity; a
        // mimibiote appearance is therefore preserved by the detached-copy
        // path. Ordinary crafting consumes its input and must reject cosmetics.
        return this.isMaging() || isUsablePublicCraftIngredient(object);
    }

    static boolean hasObvijevanAttachment(GameObject object) {
        if (object == null || object.getObvijevanPos() != 0
                || object.getObvijevanLook() != 0)
            return true;
        for (int stat = 970; stat <= 974; stat++)
            if (object.getStats().getEffects().containsKey(stat))
                return true;
        return false;
    }

    private CraftExecution executePublicEtherealRepair(
            Player crafter, Player receiver,
            Map<Player, ArrayList<Couple<Integer, Integer>>> list,
            JobStat jobStat) {
        Map<Integer, PublicCraftIngredient> sources =
                collectPublicCraftSources(crafter, receiver, list);
        if (jobStat.get_lvl() < 10 || sources == null || sources.size() != 2) {
            sendPublicCraftError(crafter, receiver);
            return CraftExecution.invalid();
        }

        PublicCraftIngredient weaponSource = null;
        PublicCraftIngredient potionSource = null;
        int weaponType = repairWeaponType(this.id);
        for (PublicCraftIngredient source : sources.values()) {
            GameObject object = source.object;
            if (object.getTemplate().getType() == weaponType) {
                if (weaponSource != null || source.quantity != 1
                        || object.getQuantity() != 1) {
                    sendPublicCraftError(crafter, receiver);
                    return CraftExecution.invalid();
                }
                weaponSource = source;
            } else if (isCompatibleRepairPotion(this.id,
                    object.getTemplate().getId())) {
                if (potionSource != null || source.quantity != 1) {
                    sendPublicCraftError(crafter, receiver);
                    return CraftExecution.invalid();
                }
                potionSource = source;
            } else {
                sendPublicCraftError(crafter, receiver);
                return CraftExecution.invalid();
            }
        }
        if (weaponSource == null || potionSource == null) {
            sendPublicCraftError(crafter, receiver);
            return CraftExecution.invalid();
        }

        GameObject weapon = weaponSource.object;
        String durability = weapon.getTxtStat().get(Constant.STATS_RESIST);
        int current;
        try {
            current = durability == null ? -1
                    : Integer.parseInt(durability, 16);
        } catch (NumberFormatException invalidDurability) {
            current = -1;
        }
        int restored = restoredDurability(current,
                weapon.getResistanceMax(weapon.getTemplate().getStrTemplate()),
                potionSource.object.getStats().getEffect(702), 1);
        if (restored < 0 || !sourcesStillAvailable(sources)) {
            sendPublicCraftError(crafter, receiver);
            return CraftExecution.invalid();
        }

        boolean success = repairSucceeds(jobStat.get_lvl(),
                Formulas.getRandomValue(0, 100));
        consumePublicCraftIngredient(potionSource);
        if (success) {
            weapon.getTxtStat().put(Constant.STATS_RESIST,
                    Integer.toHexString(restored));
            persistQuantity(weapon);
        }

        long experience = (long) Formulas.calculXpWinCraft(
                jobStat.get_lvl(), 2) * Config.rateJob;
        CraftExecution committed = CraftExecution.completed(success, experience);
        this.lastPublicCommittedExecution = committed;

        notifyConsumedPublicCraftIngredient(potionSource);
        if (success) {
            SocketManager.GAME_SEND_UPDATE_ITEM(weaponSource.owner, weapon);
            String stats = weapon.encodeStats();
            crafter.send("ErKO+" + weapon.getGuid() + "|1|"
                    + weapon.getTemplate().getId() + "|" + stats);
            receiver.send("ErKO+" + weapon.getGuid() + "|1|"
                    + weapon.getTemplate().getId() + "|" + stats);
            crafter.send("EcK;" + weapon.getTemplate().getId() + ";T"
                    + receiver.getName() + ";" + stats);
            receiver.send("EcK;" + weapon.getTemplate().getId() + ";B"
                    + crafter.getName() + ";" + stats);
        } else {
            SocketManager.GAME_SEND_Ec_PACKET(crafter, "EF");
            SocketManager.GAME_SEND_Ec_PACKET(receiver, "EF");
            SocketManager.GAME_SEND_Im_PACKET(crafter, "0118");
        }
        if (crafter.getCurMap() != null)
            SocketManager.GAME_SEND_IO_PACKET_TO_MAP(crafter.getCurMap(),
                    crafter.getId(), (success ? "+" : "-")
                            + weapon.getTemplate().getId());
        SocketManager.GAME_SEND_Ow_PACKET(crafter);
        SocketManager.GAME_SEND_Ow_PACKET(receiver);

        return committed;
    }

    private static boolean sourcesStillAvailable(
            Map<Integer, PublicCraftIngredient> sources) {
        for (PublicCraftIngredient source : sources.values())
            if (source.owner.getItems().get(source.object.getGuid())
                    != source.object
                    || !isUsablePublicCraftIngredient(source.object)
                    || source.object.getQuantity() < source.quantity)
                return false;
        return true;
    }

    private static void consumePublicCraftIngredient(
            PublicCraftIngredient source) {
        int remaining = source.object.getQuantity() - source.quantity;
        if (remaining == 0) {
            source.owner.removeItem(source.object.getGuid());
            World.world.removeGameObject(source.object.getGuid());
        } else {
            source.object.setQuantity(remaining);
            persistQuantity(source.object);
        }
    }

    private static void notifyConsumedPublicCraftIngredient(
            PublicCraftIngredient source) {
        if (source.owner.hasItemGuid(source.object.getGuid()))
            SocketManager.GAME_SEND_OBJECT_QUANTITY_PACKET(source.owner,
                    source.object);
        else
            SocketManager.GAME_SEND_REMOVE_ITEM_PACKET(source.owner,
                    source.object.getGuid());
    }

    private static void logPostCommitFailure(Throwable failure) {
        try {
            Logging.getInstance().write("SecureCraft",
                    "Notification post-commit ignorée: " + failure);
        } catch (Throwable ignored) {
            // The craft is already committed. Logging must never turn it into
            // an invalid attempt and trigger a payment refund.
        }
    }

    static CraftExecution preserveCommittedExecution(
            CraftExecution committed, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException | Error notificationFailure) {
            if (committed != null && committed.isCompleted()) {
                logPostCommitFailure(notificationFailure);
                return committed;
            }
            throw notificationFailure;
        }
        return committed == null ? CraftExecution.invalid() : committed;
    }

    private static void removeUnownedCraftResult(GameObject result) {
        if (result == null || result.getGuid() <= 0)
            return;
        World.world.addGameObject(result);
        World.world.removeGameObject(result.getGuid());
    }

    private static void sendPublicCraftError(Player crafter, Player receiver) {
        if (crafter != null)
            SocketManager.GAME_SEND_Ec_PACKET(crafter, "EI");
        if (receiver != null && receiver != crafter)
            SocketManager.GAME_SEND_Ec_PACKET(receiver, "EI");
    }

    private static final class PublicCraftIngredient {
        private final Player owner;
        private final GameObject object;
        private final int quantity;

        private PublicCraftIngredient(Player owner, GameObject object,
                                      int quantity) {
            this.owner = owner;
            this.object = object;
            this.quantity = quantity;
        }
    }

    private static final class PublicCraftSelection {
        private final Map<Integer, PublicCraftIngredient> sources;
        private final Map<Integer, Integer> recipeItems;
        private final boolean signed;

        private PublicCraftSelection(
                Map<Integer, PublicCraftIngredient> sources,
                Map<Integer, Integer> recipeItems, boolean signed) {
            this.sources = sources;
            this.recipeItems = recipeItems;
            this.signed = signed;
        }
    }

    public boolean isMaging() {
        return this.id == 1 || this.id == 113 || this.id == 115 || this.id == 116 || this.id == 117
                || this.id == 118 || this.id == 119 || this.id == 120 || (this.id >= 163 && this.id <= 169);
    }

    synchronized void craft(boolean isRepeat) {
        if (!this.isCraft || !this.isCurrentCraftSession(this.player)
                || this.broke || this.broken)
            return;

        if (this.isMaging()) {
            this.craftMaging1(isRepeat, 1);
            return;
        }

        try {
            Map<Integer, Integer> selected = new TreeMap<>(this.ingredients);
            JobStat jobStat = this.SM;
            if (this.isEtherealRepair()) {
                JobStat current = this.player.getMetierBySkill(this.id);
                if (jobStat != null && current == jobStat && jobStat.get_lvl() >= 10
                        && this.craftEtherealRepair(selected, jobStat)) {
                    this.lastCraft.clear();
                    this.lastCraft.putAll(selected);
                } else {
                    SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                    this.broken = isRepeat;
                }
                this.ingredients.clear();
                return;
            }

            Map<Integer, GameObject> selectedObjects = new LinkedHashMap<>();
            Map<Integer, Integer> items = this.validateRecipeIngredients(
                    selected, selectedObjects);
            if (items == null) {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                this.broken = isRepeat;
                return;
            }

            Job recipeJob;
            if (jobStat != null) {
                JobStat current = this.player.getMetierBySkill(this.id);
                if (current != jobStat)
                    recipeJob = null;
                else
                    recipeJob = jobStat.getTemplate();
            } else {
                recipeJob = World.world.getMetier(this.id);
            }

            boolean signed = false;
            Integer signatureQuantity = items.get(7508);
            if (signatureQuantity != null) {
                if (jobStat == null || jobStat.get_lvl() != 100
                        || signatureQuantity != 1) {
                    SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                    this.ingredients.clear();
                    this.broken = isRepeat;
                    return;
                }
                items.remove(7508);
                signed = true;
            }
            int recipeIngredientCount = items.size();
            List<Integer> recipes = recipeJob == null ? null
                    : recipeJob.getListBySkill(this.id);
            int templateId = recipes == null ? -1
                    : World.world.getObjectByIngredientForJob(
                            new ArrayList<>(recipes), items);
            ObjectTemplate resultTemplate = World.world.getObjTemplate(templateId);
            if (templateId == -1 || resultTemplate == null || recipeJob == null
                    || !recipeJob.canCraft(this.id, templateId)) {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                if (this.player.getCurMap() != null)
                    SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(),
                            this.player.getId(), "-");
                this.ingredients.clear();
                this.broken = isRepeat;
                return;
            }

            boolean success = true;
            if (jobStat != null) {
                int chance = JobConstant.getChanceByNbrCaseByLvl(
                        jobStat.get_lvl(), recipeIngredientCount);
                success = chance >= Formulas.getRandomValue(0, 100);
                if (chance == 99)
                    success = chance * 2 >= Formulas.getRandomValue(0, 200);
                if (jobStat.get_lvl() == 100 || this.id == 109)
                    success = true;
            }

            GameObject craftResult = null;
            if (success) {
                craftResult = this.createCraftResult(resultTemplate, signed);
                if (craftResult == null) {
                    SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                    this.broken = isRepeat;
                    return;
                }
            }

            this.consumeRecipeIngredients(selected, selectedObjects);
            SocketManager.GAME_SEND_Ow_PACKET(this.player);

            if (!success) {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "EF");
                if (this.player.getCurMap() != null)
                    SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(),
                            this.player.getId(), "-" + templateId);
                SocketManager.GAME_SEND_Im_PACKET(this.player, "0118");
            } else {
                this.giveCraftResult(craftResult, resultTemplate);
            }

            if (jobStat != null) {
                int winXP = Formulas.calculXpWinCraft(jobStat.get_lvl(),
                        recipeIngredientCount) * Config.rateJob;
                if (winXP > 0) {
                    jobStat.addXp(this.player, winXP);
                    SocketManager.GAME_SEND_JX_PACKET(this.player,
                            new ArrayList<>(Collections.singletonList(jobStat)));
                }
            }

            this.lastCraft.clear();
            this.lastCraft.putAll(selected);
            this.ingredients.clear();
        } finally {
            if (!isRepeat) {
                this.oldJobCraft = this.jobCraft;
                this.jobCraft = null;
            }
        }
    }

    Map<Integer, Integer> validateRecipeIngredients(
            Map<Integer, Integer> selected,
            Map<Integer, GameObject> selectedObjects) {
        if (selected.isEmpty())
            return null;

        Map<Integer, Integer> items = new HashMap<>();
        for (Entry<Integer, Integer> entry : selected.entrySet()) {
            GameObject object = this.player.getItems().get(entry.getKey());
            Integer quantity = entry.getValue();
            if (quantity == null || quantity <= 0
                    || !this.isSelectableCraftIngredient(object)
                    || object.getQuantity() < quantity)
                return null;
            try {
                items.merge(object.getTemplate().getId(), quantity, Math::addExact);
            } catch (ArithmeticException exception) {
                return null;
            }
            selectedObjects.put(entry.getKey(), object);
        }
        return items.size() <= this.min ? items : null;
    }

    private void consumeRecipeIngredients(Map<Integer, Integer> selected,
                                          Map<Integer, GameObject> objects) {
        for (Entry<Integer, Integer> entry : selected.entrySet()) {
            GameObject object = objects.get(entry.getKey());
            int remaining = object.getQuantity() - entry.getValue();
            if (remaining == 0) {
                this.player.removeItem(object.getGuid());
                World.world.removeGameObject(object.getGuid());
                SocketManager.GAME_SEND_REMOVE_ITEM_PACKET(this.player,
                        object.getGuid());
            } else {
                object.setQuantity(remaining);
                persistQuantity(object);
                SocketManager.GAME_SEND_OBJECT_QUANTITY_PACKET(this.player, object);
            }
        }
    }

    private GameObject createCraftResult(ObjectTemplate template, boolean signed) {
        GameObject result = signed
                ? template.createNewItem(1, false)
                : template.createNewItemWithoutDuplication(
                        this.player.getItems().values(), 1, false);
        if (result == null)
            return null;
        if (signed) {
            result.addTxtStat(Constant.STATS_SIGNATURE, this.player.getName());
            ObjectData objectData = DatabaseManager.get(ObjectData.class);
            if (objectData != null)
                objectData.update(result);
        }
        return result;
    }

    private void giveCraftResult(GameObject result, ObjectTemplate template) {
        if (this.player.getItems().get(result.getGuid()) == null) {
            if (this.player.addItem(result, true, false))
                World.world.addGameObject(result);
        } else {
            persistQuantity(result);
            SocketManager.GAME_SEND_UPDATE_OBJECT_DISPLAY_PACKET(this.player, result);
        }
        SocketManager.GAME_SEND_Ow_PACKET(this.player);
        SocketManager.GAME_SEND_Em_PACKET(this.player,
                "KO+" + result.getGuid() + "|1|" + template.getId() + "|"
                        + result.encodeStats().replace(";", "#"));
        SocketManager.GAME_SEND_Ec_PACKET(this.player, "K;" + template.getId());
        if (this.player.getCurMap() != null)
            SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(),
                    this.player.getId(), "+" + template.getId());
    }

    public static boolean isEtherealRepairSkill(int skillId) {
        return skillId >= 142 && skillId <= 149;
    }

    private boolean isEtherealRepair() {
        return isEtherealRepairSkill(this.id);
    }

    static int repairWeaponType(int skillId) {
        switch (skillId) {
            case 142:
                return Constant.ITEM_TYPE_DAGUES;
            case 143:
                return Constant.ITEM_TYPE_HACHE;
            case 144:
                return Constant.ITEM_TYPE_MARTEAU;
            case 145:
                return Constant.ITEM_TYPE_EPEE;
            case 146:
                return Constant.ITEM_TYPE_PELLE;
            case 147:
                return Constant.ITEM_TYPE_BATON;
            case 148:
                return Constant.ITEM_TYPE_BAGUETTE;
            case 149:
                return Constant.ITEM_TYPE_ARC;
            default:
                return -1;
        }
    }

    static boolean isCompatibleRepairPotion(int skillId, int templateId) {
        if (skillId >= 142 && skillId <= 146)
            return templateId == 2529 || templateId == 2538 || templateId == 2541;
        if (skillId >= 147 && skillId <= 149)
            return templateId == 2539 || templateId == 2540 || templateId == 2543;
        return false;
    }

    static int restoredDurability(int current, int maximum, int potionBonus,
                                  int potionQuantity) {
        if (current < 0 || maximum <= 0 || current >= maximum
                || potionBonus <= 0 || potionQuantity <= 0)
            return -1;
        long restored = (long) current + (long) potionBonus * potionQuantity;
        return (int) Math.min(restored, maximum);
    }

    static boolean repairSucceeds(int jobLevel, int roll) {
        return roll >= 0 && roll <= 100
                && JobConstant.getChanceByNbrCaseByLvl(jobLevel, 2) >= roll;
    }

    private boolean craftEtherealRepair(Map<Integer, Integer> selected,
                                         JobStat jobStat) {
        if (selected.size() != 2)
            return false;

        int weaponType = repairWeaponType(this.id);
        GameObject weapon = null;
        GameObject potion = null;
        int potionQuantity = 0;

        for (Entry<Integer, Integer> entry : selected.entrySet()) {
            GameObject object = this.player.getItems().get(entry.getKey());
            int quantity = entry.getValue() == null ? 0 : entry.getValue();
            if (object == null || object.getTemplate() == null
                    || quantity <= 0 || object.isAttach()
                    || object.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                    || object.getObvijevanLook() != 0
                    || object.getQuantity() < quantity)
                return false;

            if (object.getTemplate().getType() == weaponType) {
                // Durability is stored on the GameObject, not per unit. Mutating a
                // stacked weapon would therefore repair every unit for one potion.
                if (weapon != null || quantity != 1 || object.getQuantity() != 1)
                    return false;
                weapon = object;
            } else if (isCompatibleRepairPotion(this.id,
                    object.getTemplate().getId())) {
                if (potion != null || quantity != 1)
                    return false;
                potion = object;
                potionQuantity = quantity;
            } else {
                return false;
            }
        }

        if (weapon == null || potion == null)
            return false;
        String durability = weapon.getTxtStat().get(Constant.STATS_RESIST);
        if (durability == null)
            return false;

        int current;
        try {
            current = Integer.parseInt(durability, 16);
        } catch (NumberFormatException exception) {
            return false;
        }
        int maximum = weapon.getResistanceMax(weapon.getTemplate().getStrTemplate());
        int potionBonus = potion.getStats().getEffect(702);
        int restored = restoredDurability(current, maximum, potionBonus,
                potionQuantity);
        if (restored < 0)
            return false;

        if (potion.getQuantity() == 1) {
            this.player.removeItem(potion.getGuid());
            World.world.removeGameObject(potion.getGuid());
            SocketManager.GAME_SEND_REMOVE_ITEM_PACKET(this.player, potion.getGuid());
        } else {
            potion.setQuantity(potion.getQuantity() - 1);
            persistQuantity(potion);
            SocketManager.GAME_SEND_OBJECT_QUANTITY_PACKET(this.player, potion);
        }
        SocketManager.GAME_SEND_Ow_PACKET(this.player);

        boolean success = repairSucceeds(jobStat.get_lvl(),
                Formulas.getRandomValue(0, 100));
        if (success) {
            weapon.getTxtStat().put(Constant.STATS_RESIST,
                    Integer.toHexString(restored));
            ObjectData objectData = DatabaseManager.get(ObjectData.class);
            if (objectData != null)
                objectData.update(weapon);

            SocketManager.GAME_SEND_UPDATE_ITEM(this.player, weapon);
            SocketManager.GAME_SEND_Em_PACKET(this.player,
                    "KO+" + weapon.getGuid() + "|1|" + weapon.getTemplate().getId()
                            + "|" + weapon.encodeStats().replace(";", "#"));
            SocketManager.GAME_SEND_Ec_PACKET(this.player,
                    "K;" + weapon.getTemplate().getId());
            if (this.player.getCurMap() != null)
                SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(),
                        this.player.getId(), "+" + weapon.getTemplate().getId());
        } else {
            SocketManager.GAME_SEND_Ec_PACKET(this.player, "EF");
            SocketManager.GAME_SEND_Im_PACKET(this.player, "0118");
            if (this.player.getCurMap() != null)
                SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(),
                        this.player.getId(), "-" + weapon.getTemplate().getId());
        }

        int winXP = Formulas.calculXpWinCraft(jobStat.get_lvl(), 2)
                * Config.rateJob;
        if (winXP > 0) {
            jobStat.addXp(this.player, winXP);
            SocketManager.GAME_SEND_JX_PACKET(this.player,
                    new ArrayList<>(Collections.singletonList(jobStat)));
        }
        return true;
    }

    public static float coefExo = 0.25f;

    /* ********FM TOUT POURRI*************/
    private synchronized boolean craftMaging(boolean isRepeat, Player receiver, Map<Player, ArrayList<Couple<Integer, Integer>>> items) {
        boolean isSigningRune = false;
        GameObject objectFm = null, sourceObjectFm = null, signingRune = null, runeOrPotion = null;
        Player sourceOwner = null;
        int lvlElementRune = 0, statId = -1, lvlQuaStatsRune = 0, statsAdd = 0, poid = 0, idRune = 0;
        boolean bonusRune = false;
        String statsObjectFm = "-1";

        final boolean secure = items != null && receiver != null;
        final Map<Integer, Integer> ingredients = items == null ? this.ingredients : new HashMap<>();

        if(items != null) {
            for(Entry<Player, ArrayList<Couple<Integer, Integer>>> entry : items.entrySet()) {
                for(Couple<Integer, Integer> couple : entry.getValue()) {
                    if (couple == null || couple.second == null
                            || couple.second <= 0) {
                        SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                        return false;
                    }
                    try {
                        ingredients.merge(couple.first, couple.second,
                                Math::addExact);
                    } catch (ArithmeticException overflow) {
                        SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                        return false;
                    }
                }
            }
        }

        if (!validateMagingIngredients(ingredients, receiver)) {
            SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
            if (receiver != null)
                SocketManager.GAME_SEND_Ec_PACKET(receiver, "EI");
            return false;
        }

        for (int id : ingredients.keySet()) {
            GameObject object = findMagingObject(id, this.player, receiver);

            if(object == null) {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                if (this.player.getCurMap() != null)
                    SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(), this.player.getId(), "-");
                ingredients.clear();
                return false;
            }

            int template = object.getTemplate().getId();
            if (object.getTemplate().getType() == Constant.ITEM_TYPE_RUNE_FORGEMAGIE
                    || isElementalMagingPotion(template))
                idRune = id;

            //region gros switch rune
            switch (template) {
                //region on s'en tape
                case 1333:
                    statId = 99;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1335:
                    statId = 96;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1337:
                    statId = 98;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1338:
                    statId = 97;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1340:
                    statId = 97;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1341:
                    statId = 96;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1342:
                    statId = 98;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1343:
                    statId = 99;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1345:
                    statId = 99;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1346:
                    statId = 96;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1347:
                    statId = 98;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1348:
                    statId = 97;
                    lvlElementRune = object.getTemplate().getLevel();
                    runeOrPotion = object;
                    break;
                case 1519:
                    runeOrPotion = object;
                    statsObjectFm = "76";
                    statsAdd = 1;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1521:
                    runeOrPotion = object;
                    statsObjectFm = "7c";
                    statsAdd = 1;
                    poid = 6;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1522:
                    runeOrPotion = object;
                    statsObjectFm = "7e";
                    statsAdd = 1;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1523:
                    runeOrPotion = object;
                    statsObjectFm = "7d";
                    statsAdd = 3;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1524:
                    runeOrPotion = object;
                    statsObjectFm = "77";
                    statsAdd = 1;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1525:
                    runeOrPotion = object;
                    statsObjectFm = "7b";
                    statsAdd = 1;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1545:
                    runeOrPotion = object;
                    statsObjectFm = "76";
                    statsAdd = 3;
                    poid = 3;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1546:
                    runeOrPotion = object;
                    statsObjectFm = "7c";
                    statsAdd = 3;
                    poid = 18;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1547:
                    runeOrPotion = object;
                    statsObjectFm = "7e";
                    statsAdd = 3;
                    poid = 3;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1548:
                    runeOrPotion = object;
                    statsObjectFm = "7d";
                    statsAdd = 10;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1549:
                    runeOrPotion = object;
                    statsObjectFm = "77";
                    statsAdd = 3;
                    poid = 3;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1550:
                    runeOrPotion = object;
                    statsObjectFm = "7b";
                    statsAdd = 3;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1551:
                    runeOrPotion = object;
                    statsObjectFm = "76";
                    statsAdd = 10;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1552:
                    runeOrPotion = object;
                    statsObjectFm = "7c";
                    statsAdd = 10;
                    poid = 50;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1553:
                    runeOrPotion = object;
                    statsObjectFm = "7e";
                    statsAdd = 10;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1554:
                    runeOrPotion = object;
                    statsObjectFm = "7d";
                    statsAdd = 30;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1555:
                    runeOrPotion = object;
                    statsObjectFm = "77";
                    statsAdd = 10;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1556:
                    runeOrPotion = object;
                    statsObjectFm = "7b";
                    statsAdd = 10;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1557:
                    runeOrPotion = object;
                    statsObjectFm = "6f";
                    statsAdd = 1;
                    poid = 100;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 1558:
                    runeOrPotion = object;
                    statsObjectFm = "80";
                    statsAdd = 1;
                    poid = 90;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7433:
                    runeOrPotion = object;
                    statsObjectFm = "73";
                    statsAdd = 1;
                    poid = 30;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7434:
                    runeOrPotion = object;
                    statsObjectFm = "b2";
                    statsAdd = 1;
                    poid = 20;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7435:
                    runeOrPotion = object;
                    statsObjectFm = "79";
                    statsAdd = 1;
                    poid = 20;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7436:
                    runeOrPotion = object;
                    statsObjectFm = "8a";
                    statsAdd = 1;
                    poid = 2;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7437:
                    runeOrPotion = object;
                    statsObjectFm = "dc";
                    statsAdd = 1;
                    poid = 2;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7438:
                    runeOrPotion = object;
                    statsObjectFm = "75";
                    statsAdd = 1;
                    poid = 50;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7442:
                    runeOrPotion = object;
                    statsObjectFm = "b6";
                    statsAdd = 1;
                    poid = 30;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7443:
                    runeOrPotion = object;
                    statsObjectFm = "9e";
                    statsAdd = 10;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7444:
                    runeOrPotion = object;
                    statsObjectFm = "9e";
                    statsAdd = 30;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7445:
                    runeOrPotion = object;
                    statsObjectFm = "9e";
                    statsAdd = 100;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7446:
                    runeOrPotion = object;
                    statsObjectFm = "e1";
                    statsAdd = 1;
                    poid = 15;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7447:
                    runeOrPotion = object;
                    statsObjectFm = "e2";
                    statsAdd = 1;
                    poid = 2;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7448:
                    runeOrPotion = object;
                    statsObjectFm = "ae";
                    statsAdd = 10;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7449:
                    runeOrPotion = object;
                    statsObjectFm = "ae";
                    statsAdd = 30;
                    poid = 3;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7450:
                    runeOrPotion = object;
                    statsObjectFm = "ae";
                    statsAdd = 100;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7451:
                    runeOrPotion = object;
                    statsObjectFm = "b0";
                    statsAdd = 1;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7452:
                    runeOrPotion = object;
                    statsObjectFm = "f3";
                    statsAdd = 1;
                    poid = 4;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7453:
                    runeOrPotion = object;
                    statsObjectFm = "f2";
                    statsAdd = 1;
                    poid = 4;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7454:
                    runeOrPotion = object;
                    statsObjectFm = "f1";
                    statsAdd = 1;
                    poid = 4;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7455:
                    runeOrPotion = object;
                    statsObjectFm = "f0";
                    statsAdd = 1;
                    poid = 4;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7456:
                    runeOrPotion = object;
                    statsObjectFm = "f4";
                    statsAdd = 1;
                    poid = 4;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7457:
                    runeOrPotion = object;
                    statsObjectFm = "d5";
                    statsAdd = 1;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7458:
                    runeOrPotion = object;
                    statsObjectFm = "d4";
                    statsAdd = 1;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7459:
                    runeOrPotion = object;
                    statsObjectFm = "d2";
                    statsAdd = 1;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7460:
                    runeOrPotion = object;
                    statsObjectFm = "d6";
                    statsAdd = 1;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7560:
                    runeOrPotion = object;
                    statsObjectFm = "d3";
                    statsAdd = 1;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 8379:
                    runeOrPotion = object;
                    statsObjectFm = "7d";
                    statsAdd = 10;
                    poid = 10;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 10662:
                    runeOrPotion = object;
                    statsObjectFm = "b0";
                    statsAdd = 3;
                    poid = 15;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 10613:
                    runeOrPotion = object;
                    statsObjectFm = "e1";
                    statsAdd = 3;
                    poid = 15;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 10615:
                    runeOrPotion = object;
                    statsObjectFm = "e2";
                    statsAdd = 3;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 10616:
                    runeOrPotion = object;
                    statsObjectFm = "e2";
                    statsAdd = 10;
                    poid = 15;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;

                case 10618:
                    runeOrPotion = object;
                    statsObjectFm = "8a";
                    statsAdd = 3;
                    poid = 5;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 10619:
                    runeOrPotion = object;
                    statsObjectFm = "8a";
                    statsAdd = 10;
                    poid = 20;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 7508:
                    isSigningRune = true;
                    signingRune = object;
                    break;
                case 11118:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "76";
                    statsAdd = 15;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11119:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "7c";
                    statsAdd = 15;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11120:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "7e";
                    statsAdd = 15;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11121:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "7d";
                    statsAdd = 45;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11122:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "77";
                    statsAdd = 15;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11123:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "7b";
                    statsAdd = 15;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11124:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "b0";
                    statsAdd = 10;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11125:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "73";
                    statsAdd = 3;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11126:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "b2";
                    statsAdd = 5;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11127:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "70";
                    statsAdd = 5;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11128:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "8a";
                    statsAdd = 10;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 11129:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "dc";
                    statsAdd = 5;
                    poid = 1;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                case 10057:
                    bonusRune = true;
                    runeOrPotion = object;
                    statsObjectFm = "31b";
                    statsAdd = 1;
                    poid = 0;
                    lvlQuaStatsRune = object.getTemplate().getLevel();
                    break;
                //endregion
                default:
                    int type = object.getTemplate().getType();
                    if ((type >= 1 && type <= 11) || (type >= 16 && type <= 22) || type == 81 || type == 102 || type == 114 || object.getTemplate().getPACost() > 0) {
                        final Player owner = this.player.hasItemGuid(object.getGuid()) ? this.player : receiver;
                        if (owner == null || sourceObjectFm != null) {
                            sourceObjectFm = null;
                            break;
                        }
                        sourceObjectFm = object;
                        sourceOwner = owner;
                        break;
                    }
            }
            //endregion
        }

        //region Calcul formule
        double poid2 = getPwrPerEffet(Integer.parseInt(statsObjectFm, 16));
        if (poid2 > 0.0)
            poid = statsAdd * ((int) poid2);

        if (SM == null || sourceObjectFm == null || sourceOwner == null || runeOrPotion == null) {
            if(receiver != null)
                SocketManager.GAME_SEND_Ec_PACKET(receiver, "EI");
            SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
            if (this.player.getCurMap() != null)
                SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(), this.player.getId(), "-");

            ingredients.clear();
            return false;
        }
        objectFm = createDetachedMagingCopy(sourceObjectFm);
        if (objectFm == null) {
            SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
            return false;
        }
        Map<Integer, String> preservedTextStats = new HashMap<>(
                objectFm.getTxtStat());
        Map<Integer, Integer> preservedSoulStats = new HashMap<>(
                objectFm.getSoulStat());
        List<String> preservedSpellStats = new ArrayList<>(
                objectFm.getSpellStats());

        final ObjectTemplate template = objectFm.getTemplate();
        ArrayList<Integer> chances = new ArrayList<>();

        int chance, lvlJob = SM.get_lvl(), currentWeightTotal = 1, pwrPerte;
        int winXP = 0;
        int objTemplateID = template.getId();
        String statStringObj = objectFm.encodeStats();

        if (lvlElementRune > 0 && lvlQuaStatsRune == 0) {
            chance = Formulas.calculChanceByElement(lvlJob, template.getLevel(), lvlElementRune);
            if (chance > 100 - (lvlJob / 20))
                chance = 100 - (lvlJob / 20);
            if (chance < (lvlJob / 20))
                chance = (lvlJob / 20);
            chances.add(0, chance);
            chances.add(1, 0);
            chances.add(2, 100 - chance);
        } else if (lvlQuaStatsRune > 0 && lvlElementRune == 0) {
            int currentWeightStats = 1;
            if (!statStringObj.isEmpty()) {
                currentWeightTotal = currentTotalWeigthBase(statStringObj, objectFm); // Poids total de l'objet : PWRg
                currentWeightStats = currentWeithStats(objectFm, statsObjectFm); // Poids � ajouter : PWRcarac
            }

            int currentTotalBase = WeithTotalBase(objTemplateID); // Poids maximum de l'objet : PWRmax
            int currentMinBase = WeithTotalBaseMin(objTemplateID);

            if (currentTotalBase < 0)
                currentTotalBase = 0;
            if (currentWeightStats < 0)
                currentWeightStats = 0;
            if (currentWeightTotal < 0)
                currentWeightTotal = 0;

            float coef = 1;
            int baseStats = viewBaseStatsItem(objectFm, statsObjectFm), currentStats = viewActualStatsItem(objectFm, statsObjectFm);

            if (baseStats == 1 && currentStats == 1 || baseStats == 1 && currentStats == 0) {
                coef = 1.0f;
            } else if (baseStats == 2 && currentStats == 2) {
                coef = 0.50f;
            } else if (baseStats == 0 && currentStats == 0 || baseStats == 0 && currentStats == 1) {
                coef = coefExo;
            }

            float x = 1;
            boolean canFM = true;
            int statMax = getStatBaseMaxs(objectFm.getTemplate(), statsObjectFm), actualJet = getActualJet(objectFm, statsObjectFm);

            if (actualJet > statMax) {
                x = 0.8F;
                int overPerEffect = (int) getOverPerEffet(Integer.parseInt(statsObjectFm, 16));
                //if (statMax == 0)
                if (actualJet >= (statMax + overPerEffect))
                    canFM = false;
                if(Integer.parseInt(statsObjectFm, 16) == 111) {
                    if(objectFm.isOverFm2(111, 1))
                        if(!canFM)
                            canFM = true;
                } else if(Integer.parseInt(statsObjectFm, 16) == 128) {
                    if(objectFm.isOverFm2(128, 1))
                        if(!canFM)
                            canFM = true;
                }
            }
            if (lvlJob < (int) Math.floor(template.getLevel() / 2))
                canFM = false; // On rate le FM si le m�tier n'est pas suffidant

            int diff = (int) Math.abs((currentTotalBase * 1.3f) - currentWeightTotal);

            if (canFM) {
                chances = Formulas.chanceFM(currentTotalBase, currentMinBase, currentWeightTotal, currentWeightStats, poid, diff, coef, statMax, getStatBaseMins(objectFm.getTemplate(), statsObjectFm), currentStats(objectFm, statsObjectFm), x, bonusRune, statsAdd);
            } else {// Si l'objet est au dessus de l'over (impossible statistiquement ... mais evite un gelano 2 PA :p)
                chances.add(0, 0);
                chances.add(1, 0);
            }
        }

        int aleatoryChance = Formulas.getRandomValue(1, 100), SC = chances.get(0), SN = chances.get(1);
        boolean successC = (aleatoryChance <= SC), successN = (aleatoryChance <= (SC + SN));

        if(objectFm.getPuit() >= statsAdd) {
            if(runeOrPotion.getTemplate().getId() != 1558 && runeOrPotion.getTemplate().getId() != 1557 && runeOrPotion.getTemplate().getId() != 7438) {
                if(Formulas.getRandomValue(1, 2) == 1)
                    successC = true;
            }
        }

        if(runeOrPotion.getTemplate().getId() == 1558 || runeOrPotion.getTemplate().getId() == 1557)
            if(Formulas.getRandomValue(0, 100) == 1)
                successC = true;

        if (successC || successN) {
            winXP = Formulas.calculXpWinFm(objectFm.getTemplate().getLevel(), poid)
                    * Config.rateJob;
        }
        //endregion

        //region succès critique
        if (successC) {
            int coef = 0;
            pwrPerte = 0;

            if (lvlElementRune == 1) coef = 50;
            else if (lvlElementRune == 25) coef = 65;
            else if (lvlElementRune == 50) coef = 85;
            if (isSigningRune)
                objectFm.addTxtStat(985, this.player.getName());

            if (lvlElementRune > 0 && lvlQuaStatsRune == 0) {
                for (SpellEffect effect : objectFm.getEffects()) {
                    if (effect.getEffectID() != 100)
                        continue;
                    String[] infos = effect.getArgs().split(";");
                    try {
                        int min = Integer.parseInt(infos[0], 16);
                        int max = Integer.parseInt(infos[1], 16);
                        int newMin = (min * coef) / 100;
                        int newMax = (max * coef) / 100;
                        if (newMin == 0)
                            newMin = 1;
                        String newRange = "1d" + (newMax - newMin + 1) + "+" + (newMin - 1);
                        String newArgs = Integer.toHexString(newMin) + ";" + Integer.toHexString(newMax) + ";-1;-1;0;" + newRange;
                        effect.setArgs(newArgs);
                        effect.setEffectID(statId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (lvlQuaStatsRune > 0 && lvlElementRune == 0) {
                boolean negative = false;
                int currentStats = viewActualStatsItem(objectFm, statsObjectFm);

                if (currentStats == 2) {
                    if (statsObjectFm.compareTo("7b") == 0) {
                        statsObjectFm = "98";
                        negative = true;
                    }
                    if (statsObjectFm.compareTo("77") == 0) {
                        statsObjectFm = "9a";
                        negative = true;
                    }
                    if (statsObjectFm.compareTo("7e") == 0) {
                        statsObjectFm = "9b";
                        negative = true;
                    }
                    if (statsObjectFm.compareTo("76") == 0) {
                        statsObjectFm = "9d";
                        negative = true;
                    }
                    if (statsObjectFm.compareTo("7c") == 0) {
                        statsObjectFm = "9c";
                        negative = true;
                    }
                    if (statsObjectFm.compareTo("7d") == 0) {
                        statsObjectFm = "99";
                        negative = true;
                    }
                }

                if (statStringObj.isEmpty()) {
                    String statsStr = statsObjectFm + "#" + Integer.toHexString(statsAdd) + "#0#0#0d0+" + statsAdd;
                    objectFm.clearStats();
                    objectFm.parseStringToStats(statsStr);
                } else {
                    String statsStr;
                    if (currentStats == 1 || currentStats == 2)
                        statsStr = objectFm.parseFMStatsString(statsObjectFm, objectFm, statsAdd, negative);
                    else
                        statsStr = objectFm.parseFMStatsString(statsObjectFm, objectFm, statsAdd, negative) + "," + statsObjectFm + "#" + Integer.toHexString(statsAdd) + "#0#0#0d0+" + statsAdd;

                    objectFm.clearStats();
                    objectFm.parseStringToStats(statsStr);
                }
            }

        }
        //endregion
        //region Succès neutre
        else if (successN) {
            pwrPerte = 0;
            if (isSigningRune) {
                objectFm.addTxtStat(985, this.player.getName());
            }

            boolean negative = false;
            int currentStats = viewActualStatsItem(objectFm, statsObjectFm);

            if (currentStats == 2) {
                if (statsObjectFm.compareTo("7b") == 0) {
                    statsObjectFm = "98";
                    negative = true;
                }
                if (statsObjectFm.compareTo("77") == 0) {
                    statsObjectFm = "9a";
                    negative = true;
                }
                if (statsObjectFm.compareTo("7e") == 0) {
                    statsObjectFm = "9b";
                    negative = true;
                }
                if (statsObjectFm.compareTo("76") == 0) {
                    statsObjectFm = "9d";
                    negative = true;
                }
                if (statsObjectFm.compareTo("7c") == 0) {
                    statsObjectFm = "9c";
                    negative = true;
                }
                if (statsObjectFm.compareTo("7d") == 0) {
                    statsObjectFm = "99";
                    negative = true;
                }
            }
            if (statStringObj.isEmpty()) {
                String statsStr = statsObjectFm + "#" + Integer.toHexString(statsAdd) + "#0#0#0d0+" + statsAdd;
                objectFm.clearStats();
                objectFm.parseStringToStats(statsStr);
            } else {
                String statsStr;

                if (objectFm.getPuit() <= 0) {// EC en premier s'il n'y a pas de puits
                    statsStr = objectFm.parseStringStatsEC_FM(objectFm, statsAdd, runeOrPotion.getTemplate().getId());
                    objectFm.clearStats();
                    objectFm.parseStringToStats(statsStr);
                    pwrPerte = currentWeightTotal - currentTotalWeigthBase(statsStr, objectFm);
                }
                if (currentStats == 1 || currentStats == 2)
                    statsStr = objectFm.parseFMStatsString(statsObjectFm, objectFm, statsAdd, negative);
                else
                    statsStr = objectFm.parseFMStatsString(statsObjectFm, objectFm, statsAdd, negative) + "," + statsObjectFm + "#" + Integer.toHexString(statsAdd) + "#0#0#0d0+" + statsAdd;
                objectFm.clearStats();
                objectFm.parseStringToStats(statsStr);
            }

        }
        //endregion
        //region Echec critique
        else {// EC
            pwrPerte = 0;

            if (!statStringObj.isEmpty()) {
                String statsStr = objectFm.parseStringStatsEC_FM(objectFm, statsAdd, -1);
                objectFm.clearStats();
                objectFm.parseStringToStats(statsStr);
                pwrPerte = currentWeightTotal - currentTotalWeigthBase(statsStr, objectFm);
            }

        }
        //endregion

        restoreMagingMetadata(objectFm, preservedTextStats,
                preservedSoulStats, preservedSpellStats,
                isSigningRune && (successC || successN)
                        ? this.player.getName() : null);
        objectFm.setPuit(Math.max(0,
                (objectFm.getPuit() + pwrPerte) - poid));
        int newQuantity = ingredients.get(idRune) == null ? 0 : ingredients.get(idRune) - 1;

        Player runeOwner = findMagingOwner(runeOrPotion, this.player, receiver);
        Player signingOwner = signingRune == null ? null
                : findMagingOwner(signingRune, this.player, receiver);
        Player resultOwner = receiver == null ? this.player : receiver;
        if (!commitMagingResult(resultOwner, sourceOwner, sourceObjectFm,
                runeOwner, runeOrPotion, signingOwner, signingRune, objectFm)) {
            SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
            if (receiver != null)
                SocketManager.GAME_SEND_Ec_PACKET(receiver, "EI");
            this.broken = isRepeat;
            return false;
        }

        if (secure) {
            CraftExecution committed = CraftExecution.completed(
                    successC || successN, winXP);
            this.lastMagingExecution = committed;
            this.lastPublicCommittedExecution = committed;
        }
        notifyMagingCommit(resultOwner, sourceOwner, sourceObjectFm,
                runeOwner, runeOrPotion, signingOwner, signingRune, objectFm);

        if (winXP > 0 && !secure) {
            SM.addXp(this.player, winXP);
            ArrayList<JobStat> SMs = new ArrayList<>();
            SMs.add(SM);
            SocketManager.GAME_SEND_JX_PACKET(this.player, SMs);
        }

        String resultData = objectFm.getGuid() + "|1|"
                + objectFm.getTemplate().getId() + "|" + objectFm.encodeStats();
        if (!this.isRepeat)
            this.reConfigingRunes = -1;
        if ((this.reConfigingRunes != 0 || this.broken) && receiver == null)
            SocketManager.GAME_SEND_EXCHANGE_MOVE_OK_FM(this.player, 'O', "+",
                    resultData);
        this.data = resultData;

        if (this.player.getCurMap() != null)
            SocketManager.GAME_SEND_IO_PACKET_TO_MAP(this.player.getCurMap(),
                    this.player.getId(), (successC || successN ? "+" : "-")
                            + objTemplateID);
        if (successC) {
            if (!secure)
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "K;" + objTemplateID);
        } else if (successN) {
            if (pwrPerte > 0) {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "EF");
                SocketManager.GAME_SEND_Im_PACKET(this.player, "0194");
            } else {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "K;" + objTemplateID);
            }
        } else {
            SocketManager.GAME_SEND_Ec_PACKET(this.player, "EF");
            SocketManager.GAME_SEND_Im_PACKET(this.player,
                    pwrPerte > 0 ? "0117" : "0183");
        }

        if(receiver == null) {
            this.player.send("EmKO-" + sourceObjectFm.getGuid() + "|1|");
            this.ingredients.clear();
            this.player.send("EMKO+" + objectFm.getGuid() + "|1");
            this.ingredients.putAll(nextMagingIngredients(objectFm.getGuid(),
                    idRune, newQuantity + 1));

            if (newQuantity >= 1) {
                this.player.send("EMKO+" + idRune + "|" + newQuantity);
                this.ingredients.put(idRune, newQuantity);
            } else {
                this.player.send("EMKO-" + idRune);
            }
        } else {
            String stats = objectFm.encodeStats();
            this.player.send("ErKO+" + objectFm.getGuid() + "|1|"
                    + objTemplateID + "|" + stats);
            receiver.send("ErKO+" + objectFm.getGuid() + "|1|"
                    + objTemplateID + "|" + stats);
            if (successC || successN) {
                this.player.send("EcK;" + objTemplateID + ";T"
                        + receiver.getName() + ";" + stats);
                receiver.send("EcK;" + objTemplateID + ";B"
                        + this.player.getName() + ";" + stats);
            } else {
                receiver.send("EcEF");
            }
        }

        this.lastCraft.clear();
        this.lastCraft.putAll(this.ingredients);

        SocketManager.GAME_SEND_Ow_PACKET(this.player);
        if (!isRepeat) this.setJobCraft(null);
        return !secure || successC || successN;
    }

    private boolean validateMagingIngredients(Map<Integer, Integer> selected,
                                              Player receiver) {
        if (this.SM == null || this.SM.getTemplate() == null
                || this.player == null
                || this.player.getMetierBySkill(this.id) != this.SM
                || selected == null || selected.isEmpty())
            return false;

        int weapons = 0, consumables = 0, signatures = 0;
        boolean elementalPotion = false;
        GameObject weapon = null;
        for (Entry<Integer, Integer> entry : selected.entrySet()) {
            if (entry.getKey() == null)
                return false;
            GameObject object = findMagingObject(entry.getKey(), this.player,
                    receiver);
            Integer selectedQuantity = entry.getValue();
            Player owner = findMagingOwner(object, this.player, receiver);
            if (object == null || object.getTemplate() == null || owner == null
                    || selectedQuantity == null || selectedQuantity <= 0
                    || object.getQuantity() < selectedQuantity
                    || object.isAttach()
                    || object.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                    || hasObvijevanAttachment(object))
                return false;

            int templateId = object.getTemplate().getId();
            int type = object.getTemplate().getType();
            if (this.isAvailableObject(this.SM.getTemplate().getId(), type)) {
                if (selectedQuantity != 1 || ++weapons > 1)
                    return false;
                weapon = object;
            } else if (type == Constant.ITEM_TYPE_RUNE_FORGEMAGIE) {
                if (++consumables > 1)
                    return false;
            } else if (isElementalMagingPotion(templateId)) {
                elementalPotion = true;
                if (++consumables > 1)
                    return false;
            } else if (templateId == 7508) {
                if (selectedQuantity != 1 || this.SM.get_lvl() != 100
                        || ++signatures > 1)
                    return false;
            } else {
                return false;
            }
        }
        return weapons == 1 && consumables == 1
                && isMagingLevelSufficient(this.SM.get_lvl(),
                weapon.getTemplate().getLevel())
                && (!elementalPotion
                || isWeaponMagingJob(this.SM.getTemplate().getId())
                && hasNeutralMagingDamage(weapon));
    }

    static boolean hasNeutralMagingDamage(GameObject object) {
        if (object == null)
            return false;
        for (SpellEffect effect : object.getEffects())
            if (effect.getEffectID() == 100)
                return true;
        return false;
    }

    static Map<Integer, Integer> nextMagingIngredients(int weaponGuid,
                                                        int consumableGuid,
                                                        int selectedQuantity) {
        Map<Integer, Integer> next = new TreeMap<>();
        next.put(weaponGuid, 1);
        int remaining = selectedQuantity - 1;
        if (consumableGuid > 0 && remaining > 0)
            next.put(consumableGuid, remaining);
        return next;
    }

    static GameObject createDetachedMagingCopy(GameObject source) {
        if (source == null || source.getTemplate() == null)
            return null;
        Map<Integer, Integer> effects = new HashMap<>(source.getStats().getEffects());
        Stats stats = new Stats(effects);
        ArrayList<SpellEffect> spellEffects = new ArrayList<>();
        for (SpellEffect effect : source.getEffects())
            spellEffects.add(effect.clone());
        GameObject copy = new GameObject(-1, source.getTemplate().getId(), 1,
                Constant.ITEM_POS_NO_EQUIPED, stats, spellEffects,
                new HashMap<>(source.getSoulStat()),
                new HashMap<>(source.getTxtStat()), source.getPuit());
        copy.getSpellStats().addAll(source.getSpellStats());
        return copy;
    }

    static void restoreMagingMetadata(GameObject object,
                                      Map<Integer, String> textStats,
                                      Map<Integer, Integer> soulStats,
                                      List<String> spellStats,
                                      String newSignature) {
        object.getTxtStat().clear();
        object.getTxtStat().putAll(textStats);
        if (newSignature != null)
            object.getTxtStat().put(Constant.STATS_CHANGE_BY, newSignature);
        object.getSoulStat().clear();
        object.getSoulStat().putAll(soulStats);
        object.getSpellStats().clear();
        object.getSpellStats().addAll(spellStats);
    }

    private static Player findMagingOwner(GameObject object, Player first,
                                           Player second) {
        if (object == null)
            return null;
        if (first != null && first.hasItemGuid(object.getGuid()))
            return first;
        if (second != null && second.hasItemGuid(object.getGuid()))
            return second;
        return null;
    }

    private static GameObject findMagingObject(int guid, Player first,
                                               Player second) {
        GameObject object = first == null ? null : first.getItems().get(guid);
        if (object == null && second != null)
            object = second.getItems().get(guid);
        return object;
    }

    /**
     * Persists the finished item before debiting any source.  Inserting the
     * final state (instead of inserting a blank clone and updating it later)
     * also makes a database failure leave the whole attempt untouched.
     */
    private static boolean commitMagingResult(Player resultOwner,
                                               Player weaponOwner,
                                               GameObject weapon,
                                               Player consumableOwner,
                                               GameObject consumable,
                                               Player signingOwner,
                                               GameObject signingRune,
                                               GameObject result) {
        if (resultOwner == null || weaponOwner == null || weapon == null
                || consumableOwner == null || consumable == null || result == null
                || weapon.getQuantity() <= 0 || consumable.getQuantity() <= 0
                || !weaponOwner.hasItemGuid(weapon.getGuid())
                || !consumableOwner.hasItemGuid(consumable.getGuid())
                || weapon.isAttach()
                || weapon.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                || consumable.isAttach()
                || consumable.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                || (signingRune != null && (signingOwner == null
                || signingRune.getQuantity() <= 0
                || !signingOwner.hasItemGuid(signingRune.getGuid())
                || signingRune.isAttach()
                || signingRune.getPosition() != Constant.ITEM_POS_NO_EQUIPED)))
            return false;

        ObjectData objectData = DatabaseManager.get(ObjectData.class);
        if (!persistNewMagingResult(objectData, result))
            return false;

        consumeMagingObject(weaponOwner, weapon);
        consumeMagingObject(consumableOwner, consumable);
        if (signingRune != null)
            consumeMagingObject(signingOwner, signingRune);

        synchronized (resultOwner.getItems()) {
            resultOwner.getItems().put(result.getGuid(), result);
        }
        World.world.addGameObject(result);
        return true;
    }

    private static void notifyMagingCommit(Player resultOwner,
                                           Player weaponOwner,
                                           GameObject weapon,
                                           Player consumableOwner,
                                           GameObject consumable,
                                           Player signingOwner,
                                           GameObject signingRune,
                                           GameObject result) {
        Set<Player> affectedPlayers = Collections.newSetFromMap(
                new IdentityHashMap<>());
        affectedPlayers.add(resultOwner);
        affectedPlayers.add(weaponOwner);
        affectedPlayers.add(consumableOwner);
        if (signingOwner != null)
            affectedPlayers.add(signingOwner);
        notifyConsumedMagingObject(weaponOwner, weapon);
        notifyConsumedMagingObject(consumableOwner, consumable);
        if (signingRune != null)
            notifyConsumedMagingObject(signingOwner, signingRune);
        SocketManager.GAME_SEND_OAKO_PACKET(resultOwner, result);
        for (Player affected : affectedPlayers)
            SocketManager.GAME_SEND_Ow_PACKET(affected);
    }

    static boolean persistNewMagingResult(ObjectData objectData,
                                           GameObject result) {
        return objectData != null && result != null && objectData.insert(result)
                && result.getGuid() > 0;
    }

    private static void consumeMagingObject(Player owner, GameObject object) {
        if (object.getQuantity() <= 1) {
            owner.removeItem(object.getGuid());
            World.world.removeGameObject(object.getGuid());
            return;
        }
        object.setQuantity(object.getQuantity() - 1);
        persistQuantity(object);
    }

    private static void notifyConsumedMagingObject(Player owner,
                                                   GameObject object) {
        if (owner.hasItemGuid(object.getGuid()))
            SocketManager.GAME_SEND_OBJECT_QUANTITY_PACKET(owner, object);
        else
            SocketManager.GAME_SEND_REMOVE_ITEM_PACKET(owner, object.getGuid());
    }

    private static void persistQuantity(GameObject object) {
        // Temporary objects use negative GUIDs and have no database row yet.
        if (object == null || object.getGuid() <= 0)
            return;
        ObjectData objectData = DatabaseManager.get(ObjectData.class);
        if (objectData != null)
            objectData.update(object);
    }

    static int remainingMagingWell(int well, float loss) {
        if (well <= 0 || loss <= 0)
            return Math.max(0, well);
        return Math.max(0, Math.round(well - loss));
    }

    static int magingWellAfterStatLoss(int remainingWell,
                                       float weightStillToLose) {
        long generated = weightStillToLose < 0
                ? (long) Math.ceil(-(double) weightStillToLose) : 0;
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, remainingWell) + generated);
    }

    public static int getStatBaseMaxs(ObjectTemplate objMod, String statsModif) {
        String[] split = objMod.getStrTemplate().split(",");
        for (String s : split) {
            String[] stats = s.split("#");
            if (stats[0].toLowerCase().compareTo(statsModif.toLowerCase()) > 0) {
            	
            } else if (stats[0].toLowerCase().compareTo(statsModif.toLowerCase()) == 0) {
                int max = Integer.parseInt(stats[2], 16);
                if (max == 0)
                    max = Integer.parseInt(stats[1], 16);
                return max;
            }
        }
        return 0;
    }

    public static int getStatBaseMins(ObjectTemplate objMod, String statsModif) {
        String[] split = objMod.getStrTemplate().split(",");
        for (String s : split) {
            String[] stats = s.split("#");
            if (stats[0].toLowerCase().compareTo(statsModif.toLowerCase()) > 0) {
            } else if (stats[0].toLowerCase().compareTo(statsModif.toLowerCase()) == 0) {
                return Integer.parseInt(stats[1], 16);
            }
        }
        return 0;
    }

    public static int WeithTotalBaseMin(int objTemplateID) {
        int weight = 0;
        int alt = 0;
        String statsTemplate = "";
        statsTemplate = World.world.getObjTemplate(objTemplateID).getStrTemplate();
        if (statsTemplate == null || statsTemplate.isEmpty())
            return 0;
        String[] split = statsTemplate.split(",");
        for (String s : split) {
            String[] stats = s.split("#");
            int statID = Integer.parseInt(stats[0], 16);
            boolean sig = true;
            for (int a : Constant.ARMES_EFFECT_IDS)
                if (a == statID)
                    sig = false;
            if (!sig)
                continue;
            String jet = "";
            int value = 1;
            try {
                jet = stats[4];
                value = Formulas.getRandomJet(null, null, jet);
                try {
                    int min = Integer.parseInt(stats[1], 16);
                    value = min;
                } catch (Exception e) {
                    value = Formulas.getRandomJet(null, null, jet);
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            int statX = 1;
            if (statID == 125 || statID == 158 || statID == 174) {
                statX = 1;
            } else if (statID == 118 || statID == 126 || statID == 119
                    || statID == 123) {
                statX = 2;
            } else if (statID == 138 || statID == 666 || statID == 226
                    || statID == 220) // de
            // da�os,Trampas %
            {
                statX = 3;
            } else if (statID == 124 || statID == 176) {
                statX = 5;
            } else if (statID == 240 || statID == 241 || statID == 242
                    || statID == 243 || statID == 244)

            {
                statX = 7;
            } else if (statID == 210 || statID == 211 || statID == 212
                    || statID == 213 || statID == 214)

            {
                statX = 8;
            } else if (statID == 225 || statID == 121) {
                statX = 15;
            } else if (statID == 178 ) {
                statX = 20;
            } else if (statID == 115 || statID == 182) {
                statX = 30;
            } else if (statID == 117) {
                statX = 50;
            } else if (statID == 128) {
                statX = 90;
            } else if (statID == 111) {
                statX = 100;
            }
            weight = value * statX;
            alt += weight;
        }
        return alt;
    }

    public static int WeithTotalBase(int objTemplateID) {
        int weight = 0;
        int alt = 0;
        String statsTemplate = "";
        statsTemplate = World.world.getObjTemplate(objTemplateID).getStrTemplate();
        if (statsTemplate == null || statsTemplate.isEmpty())
            return 0;
        String[] split = statsTemplate.split(",");
        for (String s : split) {
            String[] stats = s.split("#");
            int statID = Integer.parseInt(stats[0], 16);
            boolean sig = true;
            for (int a : Constant.ARMES_EFFECT_IDS)
                if (a == statID)
                    sig = false;
            if (!sig)
                continue;
            String jet = "";
            int value = 1;
            try {
                jet = stats[4];
                value = Formulas.getRandomJet(null, null, jet);
                try {
                    int min = Integer.parseInt(stats[1], 16);
                    int max = Integer.parseInt(stats[2], 16);
                    value = min;
                    if (max != 0)
                        value = max;
                } catch (Exception e) {
                    e.printStackTrace();
                    value = Formulas.getRandomJet(null, null, jet);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            int statX = 1;
            if (statID == 125 || statID == 158 || statID == 174) {
                statX = 1;
            } else if (statID == 118 || statID == 126 || statID == 119
                    || statID == 123) {
                statX = 2;
            } else if (statID == 138 || statID == 666 || statID == 226
                    || statID == 220) // de
            // da�os,Trampas %
            {
                statX = 3;
            } else if (statID == 124 || statID == 176) {
                statX = 5;
            } else if (statID == 240 || statID == 241 || statID == 242
                    || statID == 243 || statID == 244)

            {
                statX = 7;
            } else if (statID == 210 || statID == 211 || statID == 212
                    || statID == 213 || statID == 214)

            {
                statX = 8;
            } else if (statID == 225 || statID == 121) {
                statX = 15;
            } else if (statID == 178 ) {
                statX = 20;
            } else if (statID == 115 || statID == 182) {
                statX = 30;
            } else if (statID == 117) {
                statX = 50;
            } else if (statID == 128) {
                statX = 90;
            } else if (statID == 111) {
                statX = 100;
            }
            weight = value * statX;
            alt += weight;
        }
        return alt;
    }

    public static int currentWeithStats(GameObject obj, String statsModif) {
        for (Entry<Integer, Integer> entry : obj.getStats().getEffects().entrySet()) {
            int statID = entry.getKey();
            if (Integer.toHexString(statID).toLowerCase().compareTo(statsModif.toLowerCase()) > 0) {
            } else if (Integer.toHexString(statID).toLowerCase().compareTo(statsModif.toLowerCase()) == 0) {
                int statX = 1;
                int coef = 1;
                int BaseStats = viewBaseStatsItem(obj, Integer.toHexString(statID));
                if (BaseStats == 2) {
                    coef = 3;
                } else if (BaseStats == 0) {
                    coef = 8;
                }
                if (statID == 125 || statID == 158 || statID == 174) {
                    statX = 1;
                } else if (statID == 118 || statID == 126 || statID == 119
                        || statID == 123)

                {
                    statX = 2;
                } else if (statID == 138 || statID == 666 || statID == 226
                        || statID == 220) // da�os,Trampas
                // %
                {
                    statX = 3;
                } else if (statID == 124 || statID == 176) {
                    statX = 5;
                } else if (statID == 240 || statID == 241 || statID == 242
                        || statID == 243 || statID == 244)

                {
                    statX = 7;
                } else if (statID == 210 || statID == 211 || statID == 212
                        || statID == 213 || statID == 214) {
                    statX = 8;
                } else if (statID == 225 || statID == 121) {
                    statX = 15;
                } else if (statID == 178 ) {
                    statX = 20;
                } else if (statID == 115 || statID == 182) {
                    statX = 30;
                } else if (statID == 117) {
                    statX = 50;
                } else if (statID == 128) {
                    statX = 90;
                } else if (statID == 111) {
                    statX = 100;
                }
                int Weight = entry.getValue() * statX * coef;
                return Weight;
            }
        }
        return 0;
    }

    public static int currentStats(GameObject obj, String statsModif) {
        for (Entry<Integer, Integer> entry : obj.getStats().getEffects().entrySet()) {
            int statID = entry.getKey();
            if (Integer.toHexString(statID).toLowerCase().compareTo(statsModif.toLowerCase()) > 0) {
            } else if (Integer.toHexString(statID).toLowerCase().compareTo(statsModif.toLowerCase()) == 0) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public static int currentTotalWeigthBase(String statsModelo, GameObject obj) {
        if (statsModelo.equalsIgnoreCase(""))
            return 0;
        int Weigth = 0;
        int Alto = 0;
        String[] split = statsModelo.split(",");
        for (String s : split) {
            String[] stats = s.split("#");
            int statID = Integer.parseInt(stats[0], 16);
            if (statID == 985 || statID == 988)
                continue;
            boolean xy = false;
            for (int a : Constant.ARMES_EFFECT_IDS)
                if (a == statID)
                    xy = true;
            if (xy)
                continue;
            String jet;
            int qua;
            try {
                jet = stats[4];
                qua = Formulas.getRandomJet(null, null, jet);
                try {
                    int min = Integer.parseInt(stats[1], 16);
                    int max = Integer.parseInt(stats[2], 16);
                    qua = min;
                    if (max != 0)
                        qua = max;
                } catch (Exception e) {
                    e.printStackTrace();
                    qua = Formulas.getRandomJet(null, null, jet);
                }
            } catch (Exception e) {
                continue;
                // Ok :/
            }
            int statX = 1;
            int coef = 1;
            int statsBase = viewBaseStatsItem(obj, stats[0]);
            if (statsBase == 2) {
                coef = 3;
            } else if (statsBase == 0) {
                coef = 2;
            }
            if (statID == 125 || statID == 158 || statID == 174) {
                statX = 1;
            } else if (statID == 118 || statID == 126 || statID == 119
                    || statID == 123) {
                statX = 2;
            } else if (statID == 138 || statID == 666 || statID == 226
                    || statID == 220) // de
            // da�os,Trampas %
            {
                statX = 3;
            } else if (statID == 124 || statID == 176) {
                statX = 5;
            } else if (statID == 240 || statID == 241 || statID == 242
                    || statID == 243 || statID == 244) {
                statX = 7;
            } else if (statID == 210 || statID == 211 || statID == 212
                    || statID == 213 || statID == 214)

            {
                statX = 8;
            } else if (statID == 225|| statID == 121) {
                statX = 15;
            } else if (statID == 178 ) {
                statX = 20;
            } else if (statID == 115 || statID == 182) {
                statX = 30;
            } else if (statID == 117) {
                statX = 50;
            } else if (statID == 128) {
                statX = 90;
            } else if (statID == 111) {
                statX = 100;
            }
            Weigth = qua * statX * coef;
            Alto += Weigth;
        }
        return Alto;
    }

    public static int getBaseMaxJet(int templateID, String statsModif) {
        ObjectTemplate t = World.world.getObjTemplate(templateID);
        String[] splitted = t.getStrTemplate().split(",");
        for (String s : splitted) {
            String[] stats = s.split("#");
            if (stats[0].compareTo(statsModif) > 0)//Effets n'existe pas de base
            {
            } else if (stats[0].compareTo(statsModif) == 0)//L'effet existe bien !
            {
                int max = Integer.parseInt(stats[2], 16);
                if (max == 0)
                    max = Integer.parseInt(stats[1], 16);//Pas de jet maximum on prend le minimum
                return max;
            }
        }
        return 0;
    }

    public static int getActualJet(GameObject obj, String statsModif) {
        for (Entry<Integer, Integer> entry : obj.getStats().getEffects().entrySet()) {
            if (Integer.toHexString(entry.getKey()).compareTo(statsModif) > 0)//Effets inutiles
            {
            } else if (Integer.toHexString(entry.getKey()).compareTo(statsModif) == 0)//L'effet existe bien !
            {
                int JetActual = entry.getValue();
                return JetActual;
            }
        }
        return 0;
    }

    public static byte viewActualStatsItem(GameObject obj, String stats)//retourne vrai si le stats est actuellement sur l'item
    {
        if (!obj.encodeStats().isEmpty()) {
            for (Entry<Integer, Integer> entry : obj.getStats().getEffects().entrySet()) {
                if (Integer.toHexString(entry.getKey()).compareTo(stats) > 0)//Effets inutiles
                {
                    if (Integer.toHexString(entry.getKey()).compareTo("98") == 0
                            && stats.compareTo("7b") == 0) {
                        return 2;
                    } else if (Integer.toHexString(entry.getKey()).compareTo("9a") == 0
                            && stats.compareTo("77") == 0) {
                        return 2;
                    } else if (Integer.toHexString(entry.getKey()).compareTo("9b") == 0
                            && stats.compareTo("7e") == 0) {
                        return 2;
                    } else if (Integer.toHexString(entry.getKey()).compareTo("9d") == 0
                            && stats.compareTo("76") == 0) {
                        return 2;
                    } else if (Integer.toHexString(entry.getKey()).compareTo("74") == 0
                            && stats.compareTo("75") == 0) {
                        return 2;
                    } else if (Integer.toHexString(entry.getKey()).compareTo("99") == 0
                            && stats.compareTo("7d") == 0) {
                        return 2;
                    } else {
                    }
                } else if (Integer.toHexString(entry.getKey()).compareTo(stats) == 0)//L'effet existe bien !
                {
                    return 1;
                }
            }
            return 0;
        } else {
            return 0;
        }
    }

    public static byte viewBaseStatsItem(GameObject obj, String ItemStats)//retourne vrai si le stats existe de base sur l'item
    {

        String[] splitted = obj.getTemplate().getStrTemplate().split(",");
        for (String s : splitted) {
            String[] stats = s.split("#");
            if (stats[0].compareTo(ItemStats) > 0)//Effets n'existe pas de base
            {
                if (stats[0].compareTo("98") == 0
                        && ItemStats.compareTo("7b") == 0) {
                    return 2;
                } else if (stats[0].compareTo("9a") == 0
                        && ItemStats.compareTo("77") == 0) {
                    return 2;
                } else if (stats[0].compareTo("9b") == 0
                        && ItemStats.compareTo("7e") == 0) {
                    return 2;
                } else if (stats[0].compareTo("9d") == 0
                        && ItemStats.compareTo("76") == 0) {
                    return 2;
                } else if (stats[0].compareTo("74") == 0
                        && ItemStats.compareTo("75") == 0) {
                    return 2;
                } else if (stats[0].compareTo("99") == 0
                        && ItemStats.compareTo("7d") == 0) {
                    return 2;
                } else {
                }
            } else if (stats[0].compareTo(ItemStats) == 0)//L'effet existe bien !
            {
                return 1;
            }
        }
        return 0;
    }

    public static double getPwrPerEffet(int effect) {
        double r = 0.0;
        switch (effect) {
            case Constant.STATS_ADD_PA:
                r = 100.0;
                break;
            case Constant.STATS_ADD_PM2:
                r = 90.0;
                break;
            case Constant.STATS_ADD_VIE:
                r = 0.25;
                break;
            case Constant.STATS_MULTIPLY_DOMMAGE:
                r = 100.0;
                break;
            case Constant.STATS_ADD_CC:
                r = 30.0;
                break;
            case Constant.STATS_ADD_PO:
                r = 51.0;
                break;
            case Constant.STATS_ADD_FORC:
                r = 1.0;
                break;
            case Constant.STATS_ADD_AGIL:
                r = 1.0;
                break;
            case Constant.STATS_ADD_PA2:
                r = 100.0;
                break;
            case Constant.STATS_ADD_DOMA:
                r = 20.0;
                break;
            case Constant.STATS_ADD_EC:
                r = 1.0;
                break;
            case Constant.STATS_ADD_CHAN:
                r = 1.0;
                break;
            case Constant.STATS_ADD_SAGE:
                r = 3.0;
                break;
            case Constant.STATS_ADD_VITA:
                r = 0.25;
                break;
            case Constant.STATS_ADD_INTE:
                r = 1.0;
                break;
            case Constant.STATS_ADD_PM:
                r = 90.0;
                break;
            case Constant.STATS_ADD_PERDOM:
                r = 2.0;
                break;
            case Constant.STATS_ADD_PDOM:
                r = 2.0;
                break;
            case Constant.STATS_ADD_PODS:
                r = 0.25;
                break;
            case Constant.STATS_ADD_ADODGE:
                r = 1.0;
                break;
            case Constant.STATS_ADD_MDODGE:
                r = 1.0;
                break;
            case Constant.STATS_ADD_INIT:
                r = 0.1;
                break;
            case Constant.STATS_ADD_PROS:
                r = 3.0;
                break;
            case Constant.STATS_ADD_SOIN:
                r = 20.0;
                break;
            case Constant.STATS_SUMMON_COUNT:
                r = 30.0;
                break;
            case Constant.STATS_ADD_RP_TER:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_EAU:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_AIR:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_FEU:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_NEU:
                r = 6.0;
                break;
            case Constant.STATS_ADD_TRAP_DOM:
                r = 15.0;
                break;
            case Constant.STATS_ADD_TRAP_PERDOM:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_FEU:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_NEU:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_TER:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_EAU:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_AIR:
                r = 2.0;
                break;
            case Constant.STATS_ADD_RP_PVP_TER:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_PVP_EAU:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_PVP_AIR:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_PVP_FEU:
                r = 6.0;
                break;
            case Constant.STATS_ADD_RP_PVP_NEU:
                r = 6.0;
                break;
            case Constant.STATS_ADD_R_PVP_TER:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_PVP_EAU:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_PVP_AIR:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_PVP_FEU:
                r = 2.0;
                break;
            case Constant.STATS_ADD_R_PVP_NEU:
                r = 2.0;
                break;
        }
        return r;
    }

    public static double getOverPerEffet(int effect) {
        double r = 0.0;
        switch (effect) {
            case Constant.STATS_ADD_PA:
                r = 1.0;
                break;
            case Constant.STATS_ADD_PM2:
                r = 0.0;
                break;
            case Constant.STATS_ADD_VIE:
                r = 404.0;
                break;
            case Constant.STATS_MULTIPLY_DOMMAGE:
                r = 0.0;
                break;
            case Constant.STATS_ADD_CC:
                r = 3.0;
                break;
            case Constant.STATS_ADD_PO:
                r = 0.0;
                break;
            case Constant.STATS_ADD_FORC:
                r = 101.0;
                break;
            case Constant.STATS_ADD_AGIL:
                r = 101.0;
                break;
            case Constant.STATS_ADD_PA2:
                r = 0.0;
                break;
            case Constant.STATS_ADD_DOMA:
                r = 5.0;
                break;
            case Constant.STATS_ADD_EC:
                r = 0.0;
                break;
            case Constant.STATS_ADD_CHAN:
                r = 101.0;
                break;
            case Constant.STATS_ADD_SAGE:
                r = 33.0;
                break;
            case Constant.STATS_ADD_VITA:
                r = 404.0;
                break;
            case Constant.STATS_ADD_INTE:
                r = 101.0;
                break;
            case Constant.STATS_ADD_PM:
                r = 0.0;
                break;
            case Constant.STATS_ADD_PERDOM:
                r = 50.0;
                break;
            case Constant.STATS_ADD_PDOM:
                r = 50.0;
                break;
            case Constant.STATS_ADD_PODS:
                r = 404.0;
                break;
            case Constant.STATS_ADD_ADODGE:
                r = 0.0;
                break;
            case Constant.STATS_ADD_MDODGE:
                r = 0.0;
                break;
            case Constant.STATS_ADD_INIT:
                r = 1010.0;
                break;
            case Constant.STATS_ADD_PROS:
                r = 33.0;
                break;
            case Constant.STATS_ADD_SOIN:
                r = 5.0;
                break;
            case Constant.STATS_SUMMON_COUNT:
                r = 3.0;
                break;
            case Constant.STATS_ADD_RP_TER:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_EAU:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_AIR:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_FEU:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_NEU:
                r = 16.0;
                break;
            case Constant.STATS_ADD_TRAP_DOM:
                r = 6.0;
                break;
            case Constant.STATS_ADD_TRAP_PERDOM:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_FEU:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_NEU:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_TER:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_EAU:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_AIR:
                r = 50.0;
                break;
            case Constant.STATS_ADD_RP_PVP_TER:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_PVP_EAU:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_PVP_AIR:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_PVP_FEU:
                r = 16.0;
                break;
            case Constant.STATS_ADD_RP_PVP_NEU:
                r = 16.0;
                break;
            case Constant.STATS_ADD_R_PVP_TER:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_PVP_EAU:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_PVP_AIR:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_PVP_FEU:
                r = 50.0;
                break;
            case Constant.STATS_ADD_R_PVP_NEU:
                r = 50.0;
                break;
        }
        return r;
    }
    //endregion
    /* *********************/

    //region Old craft with new formulas
    private synchronized void craftMaging1(boolean isReapeat, int repeat) {
        GameObject gameObject = null, runeObject = null, potionObject = null, signingObject = null;

        if (this.SM == null) {
            this.rejectMagingAttempt(isReapeat);
            return;
        }
        if (this.player.getMetierBySkill(this.id) != this.SM) {
            this.rejectMagingAttempt(isReapeat);
            return;
        }

        //region Vérification de craft
        /* Type : 26 = potion pour les cac
           Type : 78 = rune
           Signature : Type 50 ou Id 7508 */

        for (Entry<Integer, Integer> ingredient : this.ingredients.entrySet()) {
            GameObject object = this.player.getItems().get(ingredient.getKey());
            Integer selectedQuantity = ingredient.getValue();
            if (object == null || object.getTemplate() == null
                    || selectedQuantity == null || selectedQuantity <= 0
                    || object.getQuantity() < selectedQuantity
                    || object.isAttach()
                    || object.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                    || hasObvijevanAttachment(object)) {
                this.rejectMagingAttempt(isReapeat);
                return;
            }
            int type = object.getTemplate().getType();

            if (this.isAvailableObject(this.SM.getTemplate().getId(), type)) {
                if (gameObject != null || selectedQuantity != 1) {
                    this.rejectMagingAttempt(isReapeat);
                    return;
                }
                gameObject = object;
            } else if (type == Constant.ITEM_TYPE_RUNE_FORGEMAGIE) {
                if (runeObject != null) {
                    this.rejectMagingAttempt(isReapeat);
                    return;
                }
                runeObject = object;
            } else if (type == Constant.ITEM_TYPE_FM_POTION
                    && isElementalMagingPotion(object.getTemplate().getId())) {
                if (potionObject != null) {
                    this.rejectMagingAttempt(isReapeat);
                    return;
                }
                potionObject = object;
            } else if (object.getTemplate().getId() == 7508) {
                if (signingObject != null || selectedQuantity != 1
                        || this.SM.get_lvl() != 100) {
                    this.rejectMagingAttempt(isReapeat);
                    return;
                }
                signingObject = object;
            } else {
                this.rejectMagingAttempt(isReapeat);
                return;
            }
        }

        if (gameObject == null || (runeObject == null && potionObject == null)
                || (runeObject != null && potionObject != null)) {
            this.rejectMagingAttempt(isReapeat);
            return;
        }
        if (!isMagingLevelSufficient(this.SM.get_lvl(),
                gameObject.getTemplate().getLevel())) {
            this.rejectMagingAttempt(isReapeat);
            return;
        }
        if(this.analyzeObject(gameObject)) {
            player.sendMessage("Impossible d'FM ce type d'objet pour le moment (avec faiblesses)");
            this.rejectMagingAttempt(isReapeat);
            return;
        }
        //endregion Vérification de craft

        /* Poids max : 100 si > EC à 100%
           EXO : Si ça dépasse la valeur de la stats originale ou si elle n'existe pas */
        if(runeObject != null) {
            Rune runeTemplate = Rune.getRuneById(runeObject.getTemplate().getId()); // On trouve le template de la rune qu'on souhaite appliqué à l'item

            if (runeTemplate == null) { // Si elle n'existe pas..
                this.rejectMagingAttempt(isReapeat);
                return;
            }

            GameObject sourceGameObject = gameObject;
            gameObject = createDetachedMagingCopy(sourceGameObject);
            if (gameObject == null) {
                this.rejectMagingAttempt(isReapeat);
                return;
            }

            //region Initialisation des variables principales
            String[] originalSplitStats = gameObject.getTemplate().getStrTemplate().split(",");
            String[] actualObjectSplitStats = gameObject.encodeStats().split(","); // Liste toutes les stats originale de l'objet

            String concernedOriginalJet = null, concernedActualJet = null; // Jet originale concerner
            float PWRGmin = 0, PWRGactual = 0, PWRGmax = 0;

            for (String jet : originalSplitStats) { // On fait une iteration de chaque ligne de l'objet originale
                if(jet.isEmpty()) continue;
                int id = Short.parseShort(jet.split("#")[0], 16);

                if (id == runeTemplate.getCharacteristic()) // Si l'ID de la stats est égale à l'ID de la stats de la rune
                    concernedOriginalJet = jet; // On met la ligne concerner a jour
            }

            int PWRexotique = 0;

            for (String jet : actualObjectSplitStats) { // On fait une iteration de chaque ligne de l'objet actuel
                if (jet.isEmpty()) continue;
                short id = Short.parseShort(jet.split("#")[0], 16);

                if (id == Constant.STATS_OWNER_1 || id == Constant.STATS_CHANGE_BY || id == Constant.STATS_BUILD_BY) continue;

                Rune rune = Rune.getRuneByCharacteristicAndByWeight(id);
                if (rune != null) PWRGactual += this.getPWR(rune, jet, (byte) 1);

                if (id == runeTemplate.getCharacteristic()) { // Si l'ID de la stats est égale à l'ID de la stats de la rune
                    concernedActualJet = String.valueOf(gameObject.getStats().getEffect(Integer.parseInt(jet.split("#")[0], 16))); // On met la ligne concerner a jour
                }

                boolean exist = false;
                for(String jet2 : originalSplitStats) {
                    if(jet2.isEmpty()) continue;
                    int id2 = Short.parseShort(jet2.split("#")[0], 16);
                    if(id == id2) {
                        exist = true;
                        break;
                    }
                }

                if(!exist && rune != null) {
                    PWRexotique += this.getPWR(rune, jet, (byte) 1);
                }
            }

            short
                    actualJet = concernedActualJet == null ? 0 : Short.parseShort(concernedActualJet),
                    minJet = concernedOriginalJet == null ? 0 : (short) Formulas.getMinJet(concernedOriginalJet.split("#")[4]),
                    maxJet = concernedOriginalJet == null ? 1 : (short) Formulas.getMaxJet(concernedOriginalJet.split("#")[4]);
            //endregion Initialisation des variables principales

            //region Début des calculs des PWR & PWRG
            float PWGRune = runeTemplate.getWeight();
            float PWRRune = PWGRune / runeTemplate.getBonus();
            float
                    PWRactual = actualJet * PWRRune,
                    PWRmax = maxJet * PWRRune;

            for (String jet : originalSplitStats) {
                short id = Short.parseShort(jet.split("#")[0], 16);
                Rune rune = Rune.getRuneByCharacteristicAndByWeight(id);

                if(rune == null) continue;

                PWRGmin += this.getPWR(rune, jet, (byte) 0);
                PWRGmax += this.getPWR(rune, jet, (byte) 2);
            }
            //endregion Début des calculs des PWR & PWRG

            //region Réussite normal
            byte factorJet = 47, factorObject = 50, successLevel = 5;
                      float EtatJet = (maxJet - minJet) <= 0 ?
                    0 :
                    (((actualJet + runeTemplate.getBonus()) - minJet) * 100) / (maxJet - minJet);
            if(EtatJet < 60)
                EtatJet = 60;

            float EtatObjet = (PWRGmax - PWRGmin) <= 0 ?
                    0 :
                    (float) Math.ceil(((PWRGactual - PWRGmin) * 100 / (PWRGmax - PWRGmin)));
            if(EtatObjet < 15)
                EtatObjet = 15;

            float successJet = 1, successObject = 0;
            byte criticSuccess = 1, neutralSuccess = 50, criticFail = 1;

            if(concernedOriginalJet == null) {
                // CAS EXOTIQUE
                if(PWGRune < 50) {
                    factorJet = 40;
                    factorObject = 54;
                    successLevel = 5;
                    EtatJet = 100;
                }

                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 65 && PWRRune == 1)
                    EtatJet = 150;

                EtatObjet = (float) Math.ceil(15 + PWRactual + PWGRune * 3);

                if (EtatJet >= 80)
                    successJet = factorJet * EtatJet / 100;

                successObject = factorObject * EtatObjet / 100;

                criticSuccess = (byte) Math.ceil(100 - (successJet + successObject + successLevel));
                criticSuccess = criticSuccess < 0 ? 0 : criticSuccess;

                if (criticSuccess > 50)
                    neutralSuccess = (byte) (100 - criticSuccess);
                else if (criticSuccess < 25)
                    neutralSuccess = (byte) (50 - (40 - criticSuccess));

                criticFail = (byte) (100 - (neutralSuccess + criticSuccess));

                if(PWGRune > 50) {
                    // Pa/Pm/Po
                    criticSuccess = 1;
                    neutralSuccess = 0;
                    criticFail = 99;
                }
                if(PWRexotique >= 101) {
                    criticSuccess = 0;
                    neutralSuccess = 0;
                    criticFail = 100;
                }
            } else if(PWRactual + PWGRune > PWRmax && PWRactual + PWGRune < 101) {
                // CAS OVERMAX
                factorJet = 60;
                factorObject = 54;
                successLevel = 5;
                EtatJet = 100;

                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 65 && PWRRune == 1)
                    EtatJet = 150;
                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 80 && PWRRune == 1)
                    EtatJet = 300;
                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 85 && PWRRune == 3)
                    EtatJet = 200;

                if (EtatJet >= 80)
                    successJet = factorJet * EtatJet / 100;

                if (EtatObjet >= 50)
                    successObject = factorObject * EtatObjet / 100;
                else
                    successObject = EtatObjet;

                criticSuccess = (byte) Math.ceil(100 - (successJet + successObject + successLevel));
                criticSuccess = criticSuccess < 0 ? 0 : criticSuccess;

                if (criticSuccess > 50)
                    neutralSuccess = (byte) (100 - criticSuccess);
                else if (criticSuccess < 25)
                    neutralSuccess = (byte) (50 - (40 - criticSuccess));

                criticFail = (byte) (100 - (neutralSuccess + criticSuccess));

                if(criticSuccess > 25) {
                    criticSuccess = 25;
                    neutralSuccess = 25;
                    criticFail = 50;
                }
                if(criticSuccess <= 1) {
                    criticSuccess = 1;
                    neutralSuccess = 22;
                    criticFail = 77;
                }
            } else {
                // CAS NORMAL
                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 65 && PWRRune == 1)
                    EtatJet = 150;
                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 80 && PWRRune == 1)
                    EtatJet = 300;
                if(PWGRune <= 3 && (actualJet / maxJet) * 100 > 85 && PWRRune == 3)
                    EtatJet = 200;


                if (EtatJet >= 52)
                    successJet = factorJet * EtatJet / 100;
                else
                    successJet = EtatJet / 4;

                if (EtatObjet >= 50)
                    successObject = factorObject * EtatObjet / 100;
                else
                    successObject = EtatObjet;

                criticSuccess = (byte) Math.ceil(100 - (successJet + successObject + successLevel));
                criticSuccess = criticSuccess < 0 ? 0 : criticSuccess;

                if (criticSuccess > 50)
                    neutralSuccess = (byte) (100 - criticSuccess);
                else if (criticSuccess < 25)
                    neutralSuccess = (byte) (50 - (40 - criticSuccess));

                criticFail = (byte) (100 - (neutralSuccess + criticSuccess));

                if(criticSuccess < 15) {
                    criticSuccess = 15;
                    neutralSuccess = 50;
                    criticFail = 35;
                }
            }
            if(PWRactual + PWGRune > PWRmax && PWRactual + PWGRune >= 101) {
                criticSuccess = 0;
                neutralSuccess = 0;
                criticFail = 100;
            }
            //endregion

            RandomStats<Byte> randomStats = new RandomStats<>();
            randomStats.add((int) criticSuccess, (byte) 0);
            randomStats.add((int) neutralSuccess, (byte) 1);
            randomStats.add((int) criticFail, (byte) 2);
            byte result = randomStats.get();

            if(this.player.getGroup() != null) {
                this.player.sendMessage("PWRGmin à max : " + PWRGmin + " | " + PWRGmax + " | " + PWRGactual);
                this.player.sendMessage("FO: " + factorObject + " EB: " + EtatObjet + " | FJ: " + factorJet + " | EJ: " + EtatJet);
                this.player.sendMessage("SC: " + criticSuccess + " | SN: " + neutralSuccess + " | EC: " + criticFail + " | R: " + result);
            }
            //region success critique
            if (result == 0) {
                int newQuantity = this.ingredients.get(runeObject.getGuid()) - 1;
                int winXP = Formulas.calculXpWinFm(gameObject.getTemplate().getLevel(), (int) Math.floor(runeTemplate.getWeight())) * Config.rateJob;

                GameObject newObject = gameObject;

                if (signingObject != null) {
                    if (newObject.getTxtStat().containsKey(985))
                        newObject.getTxtStat().remove(985);
                    newObject.addTxtStat(985, this.player.getName());
                }

                newObject.getStats().addOneStat(runeTemplate.getCharacteristic(), runeTemplate.getBonus());

                if (!commitMagingResult(this.player, this.player,
                        sourceGameObject, this.player, runeObject,
                        signingObject == null ? null : this.player,
                        signingObject, newObject)) {
                    this.rejectMagingAttempt(isReapeat);
                    return;
                }
                if (winXP > 0)
                    this.SM.addXp(this.player, winXP);
                this.player.send("JX|" + this.SM.getTemplate().getId() + ";"
                        + this.SM.get_lvl() + ";" + this.SM.getXpString(";") + ";");

                SocketManager.GAME_SEND_Ow_PACKET(this.player);

                this.player.send("EmKO+" + newObject.getGuid() + "|1|" + newObject.getTemplate().getId() + "|" + newObject.encodeStats());
                this.player.send("IO" + this.player.getId() + "|+" + newObject.getTemplate().getId()); // Icon tête joueur :  +/-
                this.player.send("EcK;" + newObject.getTemplate().getId());//Vous avez crée...

                this.ingredients.clear();
                this.player.send("EMKO-" + sourceGameObject.getGuid() + "|1");
                this.player.send("EMKO+" + newObject.getGuid() + "|1");
                this.ingredients.put(newObject.getGuid(), 1);

                if (newQuantity >= 1) {
                    this.player.send("EMKO+" + runeObject.getGuid() + "|" + newQuantity);
                    this.ingredients.put(runeObject.getGuid(), newQuantity);
                } else {
                    this.player.send("EMKO-" + runeObject.getGuid());
                }
                if (signingObject != null)
                    this.player.send("EMKO-" + signingObject.getGuid());

                this.oldJobCraft = this.jobCraft;
                if (!isReapeat) this.setJobCraft(null);
                return;
            }
            //endregion

            int puit = gameObject.getPuit();
            float PWGLoose = PWGRune;
            if(this.player.getGroup() != null)
                player.sendMessage("Puit before : " + puit);

            int previousWell = puit;
            puit = remainingMagingWell(previousWell, PWGLoose);
            PWGLoose = Math.max(0, PWGLoose - previousWell);

            if(this.player.getGroup() != null)
                player.sendMessage("Puit after : " + puit);
            boolean cancel = false;
            int pendingXp = 0;
            //region Succès neutre
            if(result == 1) {
                if(actualObjectSplitStats.length == 1 && (actualObjectSplitStats[0].isEmpty() || Short.parseShort(actualObjectSplitStats[0].split("#")[0], 16) == runeTemplate.getCharacteristic()))
                    cancel = true;

                pendingXp = Formulas.calculXpWinFm(gameObject.getTemplate().getLevel(),
                        (int) Math.floor(runeTemplate.getWeight())) * Config.rateJob;

                if(!cancel) {
                    List<Short> blacklist = new ArrayList<>();
                    List<String> stats = getStatsToLoose(runeTemplate, actualObjectSplitStats, originalSplitStats, blacklist);
                    int brokeJet;

                    while (PWGLoose > 0 && !stats.isEmpty()) {
                        String jet = stats.get(Formulas.random.nextInt(stats.size()));
                        short id = Short.parseShort(jet.split("#")[0], 16);
                        if (id == Constant.STATS_OWNER_1 || id == Constant.STATS_CHANGE_BY || id == Constant.STATS_BUILD_BY) continue;
                        Rune rune = Rune.getRuneByCharacteristicAndByWeight(id);
                        float PWRJetRune = rune.getWeight() * rune.getBonus();
                        float PWRGJet = this.getPWR(rune, jet, (byte) 1);

                        if (PWGRune > 50) {
                            brokeJet = Math.round(10 + ((PWGRune * 20) / PWRJetRune));
                        } else if (PWGRune >= 10 && PWGRune <= 50) {
                            brokeJet = Math.round(10 + ((PWGRune * 50) / PWRJetRune));
                        } else {
                            brokeJet = Math.round(10 + ((PWGRune * 100) / PWRJetRune));
                        }

                        byte random = (byte) Formulas.getRandomValue(1, 100);
                        if (random > brokeJet) {
                            blacklist.add(id);
                        } else {
                            int puitLoose = Formulas.getRandomValue(1, (int) (Math.ceil(PWGLoose / PWRJetRune)));
                            int old = gameObject.getStats().get(id);
                            int value = gameObject.getStats().addOneStat(id, -puitLoose);
                            old = old - puitLoose;

                            PWGLoose = PWGLoose - (PWRGJet - (rune.getWeight() * value));
                            if(old < 0) PWGLoose += -old * rune.getWeight();
                        }
                        actualObjectSplitStats = gameObject.encodeStats().split(",");
                        stats = getStatsToLoose(runeTemplate, actualObjectSplitStats, originalSplitStats, blacklist);
                    }

                    if(this.player.getGroup() != null)
                        player.sendMessage("Puit remove PWGLoose : " + PWGLoose);
                    puit = magingWellAfterStatLoss(puit, PWGLoose);
                }
            }
            //endregion

            //region Echec critique
            if(result == 2) {
                List<String> stats = getStatsToLoose(runeTemplate, actualObjectSplitStats, originalSplitStats, null);

                while(PWGLoose > 0 && !stats.isEmpty()) {
                    String jet = stats.get(Formulas.random.nextInt(stats.size()));
                    short id = Short.parseShort(jet.split("#")[0], 16);
                    if (id == Constant.STATS_OWNER_1 || id == Constant.STATS_CHANGE_BY || id == Constant.STATS_BUILD_BY) continue;
                    Rune rune = Rune.getRuneByCharacteristicAndByWeight(id);
                    float PWRJetRune = rune.getWeight() * rune.getBonus();
                    float PWRGJet = this.getPWR(rune, jet, (byte) 1);

                    int puitLoose = Formulas.getRandomValue(1, (int) (Math.ceil(PWGLoose / PWRJetRune)));
                    int old = gameObject.getStats().get(id);
                    int value = gameObject.getStats().addOneStat(id, -puitLoose);
                    old = old - puitLoose;

                    PWGLoose = PWGLoose - (PWRGJet - (rune.getWeight() * value));
                    if(old < 0) PWGLoose += -old * rune.getWeight();
                    actualObjectSplitStats = gameObject.encodeStats().split(",");
                    stats = getStatsToLoose(runeTemplate, actualObjectSplitStats, originalSplitStats, null);
                }

                if(this.player.getGroup() != null)
                    player.sendMessage("Puit remove PWGLoose : " + PWGLoose);
                puit = magingWellAfterStatLoss(puit, PWGLoose);
            }
            //endregion

            int newQuantity = this.ingredients.get(runeObject.getGuid()) - 1;
            GameObject newObject = gameObject;

            if(puit < 0) puit = 0;
            newObject.setPuit(puit);
            if(this.player.getGroup() != null)
                player.sendMessage("Puit finish : " + puit);


            if(result == 1) { // succes neutre
                if (signingObject != null) {
                    if (newObject.getTxtStat().containsKey(985))
                        newObject.getTxtStat().remove(985);
                    newObject.addTxtStat(985, this.player.getName());
                }

                if(!cancel)
                    newObject.getStats().addOneStat(runeTemplate.getCharacteristic(), runeTemplate.getBonus());
            }

            if (!commitMagingResult(this.player, this.player,
                    sourceGameObject, this.player, runeObject,
                    signingObject == null ? null : this.player,
                    signingObject, newObject)) {
                this.rejectMagingAttempt(isReapeat);
                return;
            }
            if (pendingXp > 0)
                this.SM.addXp(this.player, pendingXp);
            this.player.send(result == 1 ? "Im0194" : "Im0117");

            SocketManager.GAME_SEND_Ow_PACKET(this.player);

            this.player.send("EmKO+" + newObject.getGuid() + "|1|" + newObject.getTemplate().getId() + "|" + newObject.encodeStats());

            this.player.send("IO" + this.player.getId() + "|-" + newObject.getTemplate().getId()); // Icon tête joueur :  +/-

            this.player.send("EMKO-" + sourceGameObject.getGuid() + "|1");
            this.ingredients.clear();

            this.player.send("EMKO+" + newObject.getGuid() + "|1");
            this.ingredients.put(newObject.getGuid(), 1);


            if (newQuantity >= 1) {
                this.player.send("EMKO+" + runeObject.getGuid() + "|" + newQuantity);
                this.ingredients.put(runeObject.getGuid(), newQuantity);
            } else {
                this.player.send("EMKO-" + runeObject.getGuid());
            }
            if (signingObject != null)
                this.player.send("EMKO-" + signingObject.getGuid());

            this.oldJobCraft = this.jobCraft;
            if (!isReapeat) this.setJobCraft(null);
        } else if(potionObject != null) {
            if (this.SM == null
                    || !isWeaponMagingJob(this.SM.getTemplate().getId())) {
                SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
                this.broken = isReapeat;
                return;
            }
            if (!this.craftMaging(isReapeat, null, null))
                this.broken = isReapeat;
        }
        //endregion
    }

    static boolean isElementalMagingPotion(int templateId) {
        switch (templateId) {
            case 1333:
            case 1335:
            case 1337:
            case 1338:
            case 1340:
            case 1341:
            case 1342:
            case 1343:
            case 1345:
            case 1346:
            case 1347:
            case 1348:
                return true;
            default:
                return false;
        }
    }

    static boolean isWeaponMagingJob(int jobId) {
        return jobId >= JobConstant.JOB_FM_DAGUE
                && jobId <= JobConstant.JOB_SM_BATON;
    }

    static boolean isMagingLevelSufficient(int jobLevel, int itemLevel) {
        return jobLevel >= Math.max(0, itemLevel / 2);
    }

    private void rejectMagingAttempt(boolean isRepeat) {
        SocketManager.GAME_SEND_Ec_PACKET(this.player, "EI");
        this.broken = isRepeat;
    }

    private boolean analyzeObject(GameObject gameObject) {
        for(Entry<Integer, Integer> stat : gameObject.getStats().getEffects().entrySet()) {
            switch(stat.getKey()) {
                case Constant.STATS_REM_PA:
                case Constant.STATS_REM_PM:
                case Constant.STATS_REM_AGIL:
                case Constant.STATS_REM_CHAN:
                case Constant.STATS_REM_FORC:
                case Constant.STATS_REM_INTE:
                case Constant.STATS_REM_SAGE:
                case Constant.STATS_REM_VITA:
                case Constant.STATS_REM_PO:
                case Constant.STATS_REM_PA2:
                case Constant.STATS_REM_PROS:
                case Constant.STATS_REM_AFLEE:
                case Constant.STATS_REM_MFLEE:
                case Constant.STATS_REM_DOMA:
                case Constant.STATS_REM_INIT:
                case Constant.STATS_REM_PM2:
                case Constant.STATS_REM_PODS:
                case Constant.STATS_REM_R_AIR:
                case Constant.STATS_REM_R_FEU:
                case Constant.STATS_REM_R_TER:
                case Constant.STATS_REM_R_EAU:
                case Constant.STATS_REM_RP_AIR:
                case Constant.STATS_REM_RP_FEU:
                case Constant.STATS_REM_RP_TER:
                case Constant.STATS_REM_RP_EAU:
                case Constant.STATS_REM_R_NEU:
                case Constant.STATS_REM_RP_NEU:
                case Constant.STATS_REM_RP_PVP_AIR:
                case Constant.STATS_REM_RP_PVP_FEU:
                case Constant.STATS_REM_RP_PVP_TER:
                case Constant.STATS_REM_RP_PVP_EAU:
                case Constant.STATS_REM_RP_PVP_NEU:
                case Constant.STATS_REM_SOIN:
                case Constant.STATS_REM_CC:
                    return true;
            }
        }
        return false;
    }

    private List<String> getStatsToLoose(Rune fm, String[] actualStats, String[] originalStats, List<Short> blacklist) {
        List<String> high = new ArrayList<>(), low = new ArrayList<>();
        for(String s1 : actualStats) {
            if(s1.isEmpty())
                continue;

            short id1 = Short.parseShort(s1.split("#")[0], 16);
            if (id1 == Constant.STATS_CHANGE_BY || id1 == Constant.STATS_BUILD_BY) continue;
            Rune r1 = Rune.getRuneByCharacteristic(id1);

            if(r1 == null || blacklist != null && (r1.getCharacteristic() == fm.getCharacteristic() || blacklist.stream().filter(i -> i == id1).count() == 1))
                continue;
            boolean exist = false, overmax = false;

            for (String s2 : originalStats) {
                if(s2.isEmpty())
                    continue;

                short id2 = Short.parseShort(s2.split("#")[0], 16);
                if (id2 == Constant.STATS_CHANGE_BY || id2 == Constant.STATS_BUILD_BY) continue;
                Rune r2 = Rune.getRuneByCharacteristic(id1);

                if(id1 == id2) {
                    exist = true;
                    float pwr1 = this.getPWR(r1, s1, (byte) 1), pwr2 = this.getPWR(r2, s2, (byte) 2);
                    if(pwr1 > pwr2)
                        overmax = true;
                }
            }

            if(!exist || overmax) high.add(s1);
            else low.add(s1);
        }
        return high.size() > 0 ? high : low;
    }

    private float getPWR(Rune rune, String jet, byte type) {
        float weight = rune == null ? 1 : Rune.getRuneByCharacteristicAndByWeight(rune.getCharacteristic()).getWeight();
        switch(type) {
            case 0:// min
                return weight * Formulas.getMinJet(jet.split("#")[4]);
            case 1:// actual
                return weight * Short.parseShort(jet.split("\\+")[jet.split("\\+").length - 1]);
            case 2:// max
                return weight * Formulas.getMaxJet(jet.split("#")[4]);
        }
        return 0;
    }

    private boolean isAvailableObject(int jobId, int type) {
        switch(jobId) {
            case 62://Cordomage
                return type == 10 || type == 11;
            case 63://Joaillomage
                return type == 1 || type == 9;
            case 64://Costumage
                return type == 16 || type == 17 || type == Constant.ITEM_TYPE_SAC_DOS;
            case 43://Forgemage de Dagues
                return type == 5;
            case 44://Forgemage d'Epées
                return type == 6;
            case 45://Forgemage de Marteaux
                return type == 7;
            case 46://Forgemage de Pelles
                return type == 8;
            case 47://Forgemage de Haches
                return type == 19;
            case 48://Sculptemage d'Arcs
                return type == 2;
            case 49://Sculptemage de Baguettes
                return type == 3;
            case 50://Sculptemage de Bâtons
                return type == 4;
        }
        return false;
    }
    //endregion Old craft with new formulas
}
