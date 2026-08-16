package org.starloco.locos.job;

import org.starloco.locos.client.Account;
import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Stats;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.fight.spells.SpellEffect;
import org.starloco.locos.game.GameClient;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.world.World;
import org.starloco.locos.game.world.World.Couple;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JobCraftChecks {
    private static final int NORMAL_JOB_ID = JobConstant.JOB_BIJOUTIER;
    private static final int NORMAL_SKILL_ID = 11;
    private static final int RESULT_TEMPLATE_ID = 993_001;
    private static final int INGREDIENT_TEMPLATE_ID = 993_002;
    private static final int SECOND_TEMPLATE_ID = 993_003;
    private static final int TEST_ACCOUNT_BASE = 994_000;
    private static int nextAccountId = TEST_ACCOUNT_BASE;

    private JobCraftChecks() {
    }

    public static void run() throws Exception {
        installTemplate(RESULT_TEMPLATE_ID, Constant.ITEM_TYPE_RESSOURCE, "");
        installTemplate(INGREDIENT_TEMPLATE_ID, Constant.ITEM_TYPE_RESSOURCE, "");
        installTemplate(SECOND_TEMPLATE_ID, Constant.ITEM_TYPE_RESSOURCE, "");

        openingCraftCreatesABoundSession();
        specialWorkbenchesUseStrictAuthoritativeSlots();
        usingObjectActionsLockMovementUntilReleased();
        ingredientPacketsAddRemoveAndReplaySelections();
        invalidSelectionsAreRejected();
        splitStacksAreMergedBeforeRecipeLookup();
        successfulCraftConsumesItsSnapshotAndCreditsTheResult();
        resultStackOverflowNeverConsumesIngredients();
        invalidRecipesNeverConsumeIngredients();
        replayRestorationIsAllOrNothing();
        magingRepeatKeepsItsRebuiltSelection();
        magingJetThresholdsUseTheActualPercentage();
        magingCopiesStacksBeforeMutationAndCommitsOnce();
        secureMagingNeverReportsAnUncommittedAttemptAsSuccessful();
        publicCraftExecutionDistinguishesInvalidFailureAndSuccess();
        postCommitNotificationFailuresPreserveTheExecutionOutcome();
        legacyIntegerCraftPayloadsAreIgnoredSafely();
        signatureRunesMustBeSingleAndLevelOneHundred();
        etherealRepairRulesAreCanonical();
        stoppedAndStaleCraftsSendOnlyTheirTerminalPacket();
        cancellingASessionClearsItsState();
    }

    private static void openingCraftCreatesABoundSession() throws Exception {
        Job job = normalJob();
        JobStat stat = new JobStat(1, job, 10, 0);
        JobAction definition = stat.getJobActionBySkill(NORMAL_SKILL_ID);
        Context context = context(stat);

        check(context.player.useCraftSkill(NORMAL_SKILL_ID, 99),
                "A learned craft skill must open its exchange");
        ExchangeAction<?> exchange = context.player.getExchangeAction();
        check(exchange != null && exchange.getType() == ExchangeAction.CRAFTING,
                "Craft opening must install a crafting exchange");
        check(exchange.getValue() instanceof JobAction,
                "Craft exchanges must store a JobAction, not a skill Integer");

        JobAction session = (JobAction) exchange.getValue();
        check(session != definition,
                "Mutable craft state must not reuse the JobStat action descriptor");
        check(session.player == context.player && session.getJobStat() == stat,
                "A new craft session must bind both its player and JobStat");
        check(session.getMin() == 3,
                "The server-side job level must determine the ingredient slots");
        check(context.stub.writes().stream().anyMatch(packet ->
                        "ECK3|3;11".equals(String.valueOf(packet))),
                "ECK must expose the authoritative slot count");
        check(!context.player.useCraftSkill(NORMAL_SKILL_ID, 3),
                "A player must not open a second exchange over an active craft");
    }

    private static void specialWorkbenchesUseStrictAuthoritativeSlots()
            throws Exception {
        int[] skills = {22, 110, 121, 151};
        int[] slots = {1, 2, 8, 4};
        for (int i = 0; i < skills.length; i++) {
            int skill = skills[i];
            World.world.addJob(new Job(skill, "",
                    skill + ";" + RESULT_TEMPLATE_ID, "7000;" + skill));
        }

        Context context = context(null);
        for (int i = 0; i < skills.length; i++) {
            check(context.player.useCraftSkill(skills[i], 99),
                    "The whitelisted special workbench must open: " + skills[i]);
            JobAction action = currentAction(context.player);
            check(action != null && action.getMin() == slots[i]
                            && action.getJobStat() == null,
                    "Special workbench slots must be fixed server-side: " + skills[i]);
            action.cancelCraft();
            context.player.setExchangeAction(null);
        }
        check(!context.player.useCraftSkill(999, 1),
                "An arbitrary jobless skill must never open a craft exchange");
    }

    private static void usingObjectActionsLockMovementUntilReleased()
            throws Exception {
        Context context = context(null);
        check(context.player.beginUsingObjectAction(null),
                "A free player must be able to reserve an object-use action");
        check(context.player.getDoAction(),
                "Gathering must block movement while its delayed action is active");
        check(!context.player.beginUsingObjectAction(null),
                "A second object-use action must not replace the current one");

        context.player.setExchangeAction(null);
        check(!context.player.getDoAction(),
                "Clearing the gathering exchange must release movement");

        context.player.setExchangeAction(new ExchangeAction<>(
                ExchangeAction.CRAFTING, Integer.valueOf(NORMAL_SKILL_ID)));
        check(context.player.getDoAction(),
                "A crafting exchange must block movement");
        context.player.setExchangeAction(null);
        check(!context.player.getDoAction(),
                "Closing a crafting exchange must release movement");

        context.player.setExchangeAction(new ExchangeAction<>(
                ExchangeAction.CRAFTING_SECURE_WITH, Integer.valueOf(42)));
        check(context.player.getDoAction(),
                "A secure-crafting exchange must block movement");
        context.player.setExchangeAction(null);
        check(!context.player.getDoAction(),
                "Closing secure crafting must release movement");

        context.player.setExchangeAction(new ExchangeAction<>(
                ExchangeAction.BREAKING_OBJECTS, new Object()));
        check(context.player.getDoAction(),
                "A crusher exchange must block movement");
        context.player.setExchangeAction(null);
        check(!context.player.getDoAction(),
                "Closing a crusher exchange must release movement");
    }

    private static void ingredientPacketsAddRemoveAndReplaySelections()
            throws Exception {
        GameObject first = item(995_001, INGREDIENT_TEMPLATE_ID, 5,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject second = item(995_002, SECOND_TEMPLATE_ID, 4,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(10, first, second);
        JobAction action = currentAction(context.player);

        context.client.parsePacket("EMO+" + first.getGuid() + "|3");
        check(action.ingredients.get(first.getGuid()) == 3,
                "EMO+ must add the requested ingredient quantity");
        context.client.parsePacket("EMO+" + first.getGuid() + "|99");
        check(action.ingredients.get(first.getGuid()) == 5,
                "Ingredient additions must be capped to the owned stack");
        context.client.parsePacket("EMO-" + first.getGuid() + "|2");
        check(action.ingredients.get(first.getGuid()) == 3,
                "EMO- must remove the requested ingredient quantity");
        context.client.parsePacket("EMO+" + second.getGuid() + "|2");

        action.lastCraft.clear();
        action.lastCraft.putAll(action.ingredients);
        action.ingredients.clear();
        context.client.parsePacket("EL");
        check(action.ingredients.equals(action.lastCraft),
                "EL must restore the complete previous ingredient selection");

        context.client.parsePacket("EMO+bad|1");
        context.client.parsePacket("EMO+" + first.getGuid() + "|-1");
        check(action.ingredients.equals(action.lastCraft),
                "Malformed ingredient packets must leave the selection unchanged");
    }

    private static void invalidSelectionsAreRejected() throws Exception {
        GameObject attached = item(995_010, INGREDIENT_TEMPLATE_ID, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        attached.getTxtStat().put(Constant.STATS_OWNER_1, "nobody");
        GameObject equipped = item(995_011, INGREDIENT_TEMPLATE_ID, 1,
                Constant.ITEM_POS_ARME);
        GameObject living = item(995_012, INGREDIENT_TEMPLATE_ID, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        living.setObvijevanLook(1);
        Context context = normalContext(10, attached, equipped, living);
        JobAction action = currentAction(context.player);

        context.client.parsePacket("EMO+" + attached.getGuid() + "|1");
        context.client.parsePacket("EMO+" + equipped.getGuid() + "|1");
        context.client.parsePacket("EMO+" + living.getGuid() + "|1");
        check(action.ingredients.isEmpty(),
                "Attached, equipped and living items must not enter a craft");
    }

    private static void splitStacksAreMergedBeforeRecipeLookup()
            throws Exception {
        GameObject first = item(995_020, INGREDIENT_TEMPLATE_ID, 5,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject second = item(995_021, INGREDIENT_TEMPLATE_ID, 7,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(10, first, second);
        JobAction action = currentAction(context.player);

        Map<Integer, Integer> selected = new LinkedHashMap<>();
        selected.put(first.getGuid(), 2);
        selected.put(second.getGuid(), 3);
        Map<Integer, GameObject> objects = new LinkedHashMap<>();
        Map<Integer, Integer> byTemplate =
                action.validateRecipeIngredients(selected, objects);
        check(byTemplate != null && byTemplate.size() == 1
                        && byTemplate.get(INGREDIENT_TEMPLATE_ID) == 5,
                "Two GUIDs of one template must be summed for recipe lookup");
        check(objects.size() == 2,
                "The consumption snapshot must retain each source GUID");

        GameObject hugeOne = item(995_022, SECOND_TEMPLATE_ID,
                Integer.MAX_VALUE, Constant.ITEM_POS_NO_EQUIPED);
        GameObject hugeTwo = item(995_023, SECOND_TEMPLATE_ID,
                Integer.MAX_VALUE, Constant.ITEM_POS_NO_EQUIPED);
        context.player.getItems().put(hugeOne.getGuid(), hugeOne);
        context.player.getItems().put(hugeTwo.getGuid(), hugeTwo);
        selected.clear();
        selected.put(hugeOne.getGuid(), Integer.MAX_VALUE);
        selected.put(hugeTwo.getGuid(), Integer.MAX_VALUE);
        check(action.validateRecipeIngredients(selected, new HashMap<>()) == null,
                "A merged template quantity overflow must invalidate the snapshot");
        check(hugeOne.getQuantity() == Integer.MAX_VALUE
                        && hugeTwo.getQuantity() == Integer.MAX_VALUE,
                "Overflow validation must not mutate source stacks");

        GameObject splitOne = item(995_024, INGREDIENT_TEMPLATE_ID, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject splitTwo = item(995_025, INGREDIENT_TEMPLATE_ID, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject otherType = item(995_026, SECOND_TEMPLATE_ID, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        Context twoSlot = normalContext(1, splitOne, splitTwo, otherType);
        JobAction twoSlotAction = currentAction(twoSlot.player);
        check(twoSlotAction.addIngredient(twoSlot.player, splitOne.getGuid(), 1)
                        && twoSlotAction.addIngredient(twoSlot.player, otherType.getGuid(), 1)
                        && twoSlotAction.addIngredient(twoSlot.player, splitTwo.getGuid(), 1),
                "Split GUIDs of an existing template must not consume another recipe slot");
        Map<Integer, Integer> atSlotLimit = twoSlotAction.validateRecipeIngredients(
                new LinkedHashMap<>(twoSlotAction.ingredients), new HashMap<>());
        check(atSlotLimit != null && atSlotLimit.size() == 2
                        && atSlotLimit.get(INGREDIENT_TEMPLATE_ID) == 2,
                "Slot validation must count merged templates rather than source GUIDs");
    }

    private static void invalidRecipesNeverConsumeIngredients()
            throws Exception {
        GameObject first = item(995_030, INGREDIENT_TEMPLATE_ID, 5,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject second = item(995_031, INGREDIENT_TEMPLATE_ID, 7,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(10, first, second);
        JobAction action = currentAction(context.player);
        action.ingredients.put(first.getGuid(), 2);
        action.ingredients.put(second.getGuid(), 3);

        action.craft(false);

        check(first.getQuantity() == 5 && second.getQuantity() == 7,
                "An unknown recipe must be rejected before consuming ingredients");
        check(context.stub.writes().stream().anyMatch(packet ->
                        "EcEI".equals(String.valueOf(packet))),
                "An invalid recipe must report EI to the client");
    }

    private static void successfulCraftConsumesItsSnapshotAndCreditsTheResult()
            throws Exception {
        java.util.ArrayList<Couple<Integer, Integer>> recipe =
                new java.util.ArrayList<>();
        recipe.add(new Couple<>(INGREDIENT_TEMPLATE_ID, 2));
        World.world.addCraft(RESULT_TEMPLATE_ID, recipe);

        GameObject ingredient = item(-995_027, INGREDIENT_TEMPLATE_ID, 5,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject resultStack = item(-995_028, RESULT_TEMPLATE_ID, 4,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(100, ingredient, resultStack);
        JobAction action = currentAction(context.player);
        action.ingredients.put(ingredient.getGuid(), 2);

        action.craft(false);

        check(ingredient.getQuantity() == 3 && resultStack.getQuantity() == 5,
                "A valid craft must consume its validated snapshot and credit one result");
        check(action.lastCraft.get(ingredient.getGuid()) == 2
                        && action.ingredients.isEmpty(),
                "A successful craft must retain an exact replay selection only");
        check(context.stub.writes().stream().anyMatch(packet ->
                        ("EcK;" + RESULT_TEMPLATE_ID).equals(String.valueOf(packet))),
                "A successful craft must report its result to the client");
    }

    private static void resultStackOverflowNeverConsumesIngredients()
            throws Exception {
        GameObject ingredient = item(-995_029, INGREDIENT_TEMPLATE_ID, 5,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject fullResultStack = item(-995_030, RESULT_TEMPLATE_ID,
                Integer.MAX_VALUE, Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(100, ingredient, fullResultStack);
        JobAction action = currentAction(context.player);
        action.ingredients.put(ingredient.getGuid(), 2);

        action.craft(false);

        check(ingredient.getQuantity() == 5
                        && fullResultStack.getQuantity() == Integer.MAX_VALUE,
                "A result-stack overflow must abort before debiting ingredients");
        check(context.stub.writes().stream().anyMatch(packet ->
                        "EcEI".equals(String.valueOf(packet))),
                "A result-stack overflow must report EI");
    }

    private static void replayRestorationIsAllOrNothing() throws Exception {
        GameObject existing = item(995_040, INGREDIENT_TEMPLATE_ID, 3,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject missing = item(995_041, SECOND_TEMPLATE_ID, 3,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(10, existing, missing);
        JobAction action = currentAction(context.player);
        action.lastCraft.put(existing.getGuid(), 2);
        action.lastCraft.put(missing.getGuid(), 2);
        context.player.getItems().remove(missing.getGuid());

        check(!action.prepareNextRepeat(context.player),
                "Repeat must stop when any previous ingredient is unavailable");
        check(action.ingredients.isEmpty(),
                "Failed replay restoration must not keep a partial selection");
    }

    private static void magingRepeatKeepsItsRebuiltSelection() throws Exception {
        GameObject forgedObject = item(995_045, INGREDIENT_TEMPLATE_ID, 1,
                Constant.ITEM_POS_NO_EQUIPED);
        installTemplate(1333, Constant.ITEM_TYPE_FM_POTION, "");
        GameObject elementalPotion = item(995_046, 1333, 4,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = context(null, forgedObject, elementalPotion);
        JobAction action = new JobAction(1, 3, 0, true, 10, 0)
                .createCraftSession(context.player, null);
        context.player.setAway(true);
        context.player.setExchangeAction(new ExchangeAction<>(
                ExchangeAction.CRAFTING, action));
        action.ingredients.put(forgedObject.getGuid(), 1);
        action.ingredients.put(elementalPotion.getGuid(), 3);

        check(action.prepareNextRepeat(context.player),
                "Forgemaging repeat must validate the selection rebuilt by the prior attempt");
        check(action.ingredients.get(forgedObject.getGuid()) == 1
                        && action.ingredients.get(elementalPotion.getGuid()) == 3,
                "Forgemaging repeat must not replace rebuilt ingredients from an empty lastCraft");
        check(JobAction.isElementalMagingPotion(1333)
                        && JobAction.isElementalMagingPotion(1348)
                        && !JobAction.isElementalMagingPotion(2529),
                "Only the twelve elemental-maging potions must enter the potion path");
        check(JobAction.isWeaponMagingJob(JobConstant.JOB_FM_DAGUE)
                        && JobAction.isWeaponMagingJob(JobConstant.JOB_SM_BATON)
                        && !JobAction.isWeaponMagingJob(JobConstant.JOB_CORDOMAGE)
                        && !JobAction.isWeaponMagingJob(JobConstant.JOB_COSTUMAGE),
                "Elemental potions must be restricted to weapon-maging professions");
        check(JobAction.isMagingLevelSufficient(99, 199)
                        && !JobAction.isMagingLevelSufficient(99, 200)
                        && JobAction.isMagingLevelSufficient(100, 200),
                "A mage must only modify items within twice their profession level");
        Map<Integer, Integer> next = JobAction.nextMagingIngredients(
                77, elementalPotion.getGuid(), 3);
        check(next.get(77) == 1 && next.get(elementalPotion.getGuid()) == 2,
                "One elemental potion must be consumed and its remaining selection rebuilt");
        check(!JobAction.nextMagingIngredients(77,
                        elementalPotion.getGuid(), 1)
                        .containsKey(elementalPotion.getGuid()),
                "An exhausted elemental-potion selection must stop repeating");
    }

    private static void magingCopiesStacksBeforeMutationAndCommitsOnce() {
        ObjectTemplate weaponTemplate = installTemplate(993_020,
                Constant.ITEM_TYPE_DAGUES, "");
        GameObject weaponStack = item(995_047, weaponTemplate.getId(), 3,
                Constant.ITEM_POS_NO_EQUIPED);
        weaponStack.getStats().addOneStat(Constant.STATS_ADD_FORC, 10);
        weaponStack.getTxtStat().put(Constant.STATS_CHANGE_BY, "original");
        weaponStack.setPuit(10);
        weaponStack.getStats().addOneStat(970, 1);

        check(JobAction.hasObvijevanAttachment(weaponStack),
                "Forgemaging must detect persisted living-object metadata");
        weaponStack.getStats().getEffects().remove(970);

        GameObject detached = JobAction.createDetachedMagingCopy(weaponStack);
        check(detached != null && detached != weaponStack
                        && detached.getGuid() == -1 && detached.getQuantity() == 1,
                "Forgemaging must prepare one detached item from a weapon stack");
        detached.getStats().addOneStat(Constant.STATS_ADD_FORC, -4);
        detached.getTxtStat().put(Constant.STATS_CHANGE_BY, "mage");
        detached.setPuit(JobAction.remainingMagingWell(detached.getPuit(), 3));

        check(weaponStack.getQuantity() == 3
                        && weaponStack.getStats().get(Constant.STATS_ADD_FORC) == 10
                        && "original".equals(weaponStack.getTxtStat().get(
                        Constant.STATS_CHANGE_BY))
                        && weaponStack.getPuit() == 10,
                "A maging outcome must never mutate every item in the source stack");
        check(detached.getStats().get(Constant.STATS_ADD_FORC) == 6
                        && detached.getPuit() == 7,
                "The detached result must hold the final stats and one well debit");
        Map<Integer, String> preservedText = new HashMap<>(weaponStack.getTxtStat());
        Map<Integer, Integer> preservedSoul = new HashMap<>();
        preservedSoul.put(42, 3);
        List<String> preservedSpells = new ArrayList<>();
        preservedSpells.add("119#1#0#0#0d0+1");
        JobAction.restoreMagingMetadata(detached, preservedText,
                preservedSoul, preservedSpells, "mage");
        check("mage".equals(detached.getTxtStat().get(Constant.STATS_CHANGE_BY))
                        && detached.getSoulStat().get(42) == 3
                        && detached.getSpellStats().equals(preservedSpells),
                "Maging must preserve soul and class-spell metadata across stat reparsing");
        check(JobAction.remainingMagingWell(10, 12) == 0
                        && JobAction.remainingMagingWell(0, 3) == 0,
                "The maging well must be clamped without a second subtraction");
        check(JobAction.magingWellAfterStatLoss(7, 0) == 7
                        && JobAction.magingWellAfterStatLoss(0, -4.2f) == 5,
                "Stat loss must preserve the remaining well and only add surplus loss");
        check(JobAction.createDetachedMagingCopy(null) == null,
                "A missing source must fail before any debit");
        check(!JobAction.hasNeutralMagingDamage(weaponStack),
                "An elemental potion must reject a weapon without neutral damage");
        weaponStack.getEffects().add(new SpellEffect(100,
                "1;2;-1;-1;0;1d2+0", 0, 1));
        check(JobAction.hasNeutralMagingDamage(weaponStack),
                "Neutral weapon damage must be eligible for an elemental potion");
        check(!JobAction.persistNewMagingResult(new RejectingObjectData(), detached),
                "A failed result insert must abort the maging commit");
    }

    private static void magingJetThresholdsUseTheActualPercentage() {
        check(!JobAction.isMagingJetAbovePercent(65, 100, 65),
                "A jet exactly at a strict maging threshold must not exceed it");
        for (int actualJet = 66; actualJet <= 99; actualJet++)
            check(JobAction.isMagingJetAbovePercent(actualJet, 100, 65),
                    "A 66-99% jet must not collapse to zero: " + actualJet);

        check(!JobAction.isMagingJetAbovePercent(80, 100, 80)
                        && JobAction.isMagingJetAbovePercent(81, 100, 80),
                "The 80% maging threshold must preserve its strict boundary");
        check(!JobAction.isMagingJetAbovePercent(85, 100, 85)
                        && JobAction.isMagingJetAbovePercent(86, 100, 85),
                "The 85% maging threshold must preserve its strict boundary");
        check(!JobAction.isMagingJetAbovePercent(99, 0, 65),
                "A missing maximum jet must never satisfy a percentage threshold");
    }

    private static void secureMagingNeverReportsAnUncommittedAttemptAsSuccessful()
            throws Exception {
        ObjectTemplate weaponTemplate = installTemplate(993_021,
                Constant.ITEM_TYPE_DAGUES, "");
        GameObject forgedObject = item(-995_049, weaponTemplate.getId(), 1,
                Constant.ITEM_POS_NO_EQUIPED);
        forgedObject.getStats().addOneStat(Constant.STATS_ADD_FORC, 12);
        Job mageJob = new Job(JobConstant.JOB_FM_DAGUE, "", "", "");
        JobStat mageStat = new JobStat(3, mageJob, 100, 0);
        Context crafter = context(mageStat);
        Context receiver = context(null, forgedObject);
        JobAction action = mageStat.getJobActionBySkill(1);
        Map<Player, java.util.ArrayList<Couple<Integer, Integer>>> selected =
                new HashMap<>();
        selected.put(crafter.player, new java.util.ArrayList<>());
        java.util.ArrayList<Couple<Integer, Integer>> receiverItems =
                new java.util.ArrayList<>();
        receiverItems.add(new Couple<>(forgedObject.getGuid(), 1));
        selected.put(receiver.player, receiverItems);

        check(action != null, "The secure-maging test action must exist");
        JobAction.CraftExecution execution = action.executePublicCraft(
                crafter.player, receiver.player, selected);
        boolean legacySuccess = action.craftPublicMode(
                crafter.player, receiver.player, selected);

        check(!execution.isCompleted() && !execution.isSuccess(),
                "A secure maging attempt without a rune must remain invalid");
        check(!legacySuccess,
                "The legacy secure-craft result must not turn an invalid attempt into success");
        check(receiver.player.getItems().get(forgedObject.getGuid()) == forgedObject
                        && forgedObject.getQuantity() == 1
                        && forgedObject.getStats().get(Constant.STATS_ADD_FORC) == 12,
                "An invalid secure maging attempt must not consume or mutate its item");
        check(crafter.stub.writes().stream().noneMatch(
                        JobCraftChecks::isSuccessfulSecureCraftPacket)
                        && receiver.stub.writes().stream().noneMatch(
                        JobCraftChecks::isSuccessfulSecureCraftPacket),
                "Secure maging must not announce success before a committed mutation");
    }

    private static boolean isSuccessfulSecureCraftPacket(Object packet) {
        String value = String.valueOf(packet);
        return value.startsWith("EcK;") || value.startsWith("ErKO+");
    }

    private static void publicCraftExecutionDistinguishesInvalidFailureAndSuccess()
            throws Exception {
        GameObject invalidIngredient = item(-995_048, INGREDIENT_TEMPLATE_ID, 5,
                Constant.ITEM_POS_NO_EQUIPED);
        Context invalidCrafter = context(new JobStat(4, normalJob(), 100, 0),
                invalidIngredient);
        Context invalidReceiver = context(null);
        JobAction invalidAction = invalidCrafter.player.getMetierBySkill(
                NORMAL_SKILL_ID).getJobActionBySkill(NORMAL_SKILL_ID);
        Map<Player, java.util.ArrayList<Couple<Integer, Integer>>> invalid =
                secureSelection(invalidCrafter.player, invalidReceiver.player,
                        invalidIngredient, 1);

        JobAction.CraftExecution rejected = invalidAction.executePublicCraft(
                invalidCrafter.player, invalidReceiver.player, invalid);
        check(!rejected.isCompleted() && !rejected.isSuccess()
                        && rejected.getExperience() == 0
                        && invalidIngredient.getQuantity() == 5,
                "An invalid secure recipe must not consume items or become a paid failure");

        check(rejected.getClass() == JobAction.CraftExecution.class,
                "Secure craft callers must receive the typed execution result");
    }

    private static void postCommitNotificationFailuresPreserveTheExecutionOutcome() {
        JobAction.CraftExecution committed = JobAction.CraftExecution.completed(
                true, 42);
        JobAction.CraftExecution preserved = JobAction.preserveCommittedExecution(
                committed, () -> {
                    throw new IllegalStateException("disconnected client");
                });
        check(preserved == committed && preserved.isCompleted()
                        && preserved.isSuccess() && preserved.getExperience() == 42,
                "A notification failure after commit must not become an invalid, refunded craft");

        boolean propagated = false;
        try {
            JobAction.preserveCommittedExecution(
                    JobAction.CraftExecution.invalid(), () -> {
                        throw new IllegalStateException("pre-commit failure");
                    });
        } catch (IllegalStateException expected) {
            propagated = true;
        }
        check(propagated,
                "A failure before commit must remain visible to the secure-craft caller");
    }

    private static Map<Player, java.util.ArrayList<Couple<Integer, Integer>>>
    secureSelection(Player crafter, Player receiver, GameObject ingredient,
                    int quantity) {
        Map<Player, java.util.ArrayList<Couple<Integer, Integer>>> selected =
                new HashMap<>();
        java.util.ArrayList<Couple<Integer, Integer>> crafterItems =
                new java.util.ArrayList<>();
        crafterItems.add(new Couple<>(ingredient.getGuid(), quantity));
        selected.put(crafter, crafterItems);
        selected.put(receiver, new java.util.ArrayList<>());
        return selected;
    }

    private static void legacyIntegerCraftPayloadsAreIgnoredSafely()
            throws Exception {
        GameObject ingredient = item(995_050, INGREDIENT_TEMPLATE_ID, 3,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = context(null, ingredient);
        context.player.setExchangeAction(new ExchangeAction<>(
                ExchangeAction.CRAFTING, Integer.valueOf(NORMAL_SKILL_ID)));

        context.client.parsePacket("EMO+" + ingredient.getGuid() + "|1");
        context.client.parsePacket("EK");
        context.client.parsePacket("EL");
        context.client.parsePacket("EMR2");

        check(context.player.getExchangeAction().getValue() instanceof Integer,
                "Legacy malformed craft state must be ignored without a ClassCastException");
    }

    private static void signatureRunesMustBeSingleAndLevelOneHundred()
            throws Exception {
        installTemplate(7508, Constant.ITEM_TYPE_RESSOURCE, "");
        GameObject ingredient = item(995_060, INGREDIENT_TEMPLATE_ID, 4,
                Constant.ITEM_POS_NO_EQUIPED);
        GameObject signatures = item(995_061, 7508, 3,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(100, ingredient, signatures);
        JobAction action = currentAction(context.player);
        action.ingredients.put(ingredient.getGuid(), 1);
        action.ingredients.put(signatures.getGuid(), 2);

        action.craft(false);

        check(ingredient.getQuantity() == 4 && signatures.getQuantity() == 3,
                "Multiple signature runes must reject the craft without consumption");
        check(context.stub.writes().stream().anyMatch(packet ->
                        "EcEI".equals(String.valueOf(packet))),
                "An invalid signature quantity must report EI");
    }

    private static void etherealRepairRulesAreCanonical() throws Exception {
        int[] skills = {142, 143, 144, 145, 146, 147, 148, 149};
        int[] types = {
                Constant.ITEM_TYPE_DAGUES, Constant.ITEM_TYPE_HACHE,
                Constant.ITEM_TYPE_MARTEAU, Constant.ITEM_TYPE_EPEE,
                Constant.ITEM_TYPE_PELLE, Constant.ITEM_TYPE_BATON,
                Constant.ITEM_TYPE_BAGUETTE, Constant.ITEM_TYPE_ARC
        };
        for (int i = 0; i < skills.length; i++)
            check(JobAction.repairWeaponType(skills[i]) == types[i],
                    "Repair skill must target its matching weapon type: " + skills[i]);
        check(JobAction.isCompatibleRepairPotion(142, 2529)
                        && JobAction.isCompatibleRepairPotion(146, 2541)
                        && !JobAction.isCompatibleRepairPotion(147, 2529)
                        && JobAction.isCompatibleRepairPotion(147, 2539)
                        && JobAction.isCompatibleRepairPotion(149, 2543),
                "Metal and wood repair potions must remain in their weapon families");
        check(JobAction.restoredDurability(8, 10, 3, 1) == 10,
                "Repair durability must be capped to the weapon maximum");
        check(JobAction.restoredDurability(10, 10, 3, 1) == -1,
                "A fully repaired weapon must not consume a potion");
        check(JobAction.repairSucceeds(10, 54)
                        && !JobAction.repairSucceeds(10, 55)
                        && JobAction.repairSucceeds(20, 100),
                "Repair chance must use the ordinary two-slot craft curve");
        check(actionById(JobConstant.getPosActionsToJob(
                        JobConstant.JOB_F_DAGUE, 9), 142) == null
                        && actionById(JobConstant.getPosActionsToJob(
                        JobConstant.JOB_F_DAGUE, 10), 142) != null,
                "Ethereal repair must unlock at job level 10");

        ObjectTemplate weaponTemplate = installTemplate(993_010,
                Constant.ITEM_TYPE_DAGUES, "32c#a#1#0#0");
        installTemplate(2529, Constant.ITEM_TYPE_FM_POTION, "2be#1#3#0#1d3+0");
        GameObject weapon = item(995_070, weaponTemplate.getId(), 1,
                Constant.ITEM_POS_NO_EQUIPED);
        weapon.getTxtStat().put(Constant.STATS_RESIST, "5");
        GameObject potions = item(995_071, 2529, 3,
                Constant.ITEM_POS_NO_EQUIPED);
        Job repairJob = new Job(JobConstant.JOB_F_DAGUE, "", "", "");
        JobStat repairStat = new JobStat(2, repairJob, 10, 0);
        Context context = context(repairStat, weapon, potions);
        check(context.player.useCraftSkill(142, 3),
                "A level-10 smith must be able to open ethereal repair");
        JobAction action = currentAction(context.player);
        action.ingredients.put(weapon.getGuid(), 1);
        action.ingredients.put(potions.getGuid(), 2);

        action.craft(false);

        check(potions.getQuantity() == 3
                        && "5".equals(weapon.getTxtStat().get(Constant.STATS_RESIST)),
                "One repair attempt must require exactly one selected potion");

        action.ingredients.put(weapon.getGuid(), 1);
        action.ingredients.put(potions.getGuid(), 1);
        weapon.getTxtStat().put(Constant.STATS_OWNER_1, "nobody");
        action.craft(false);
        check(potions.getQuantity() == 3,
                "Repair execution must revalidate attached weapons before consumption");

        weapon.getTxtStat().remove(Constant.STATS_OWNER_1);
        weapon.setQuantity(2);
        action.ingredients.put(weapon.getGuid(), 1);
        action.ingredients.put(potions.getGuid(), 1);
        action.craft(false);
        check(potions.getQuantity() == 3
                        && "5".equals(weapon.getTxtStat().get(Constant.STATS_RESIST)),
                "A stacked weapon must not let one potion repair every unit");
    }

    private static void cancellingASessionClearsItsState() throws Exception {
        GameObject ingredient = item(995_080, INGREDIENT_TEMPLATE_ID, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(10, ingredient);
        JobAction action = currentAction(context.player);
        action.ingredients.put(ingredient.getGuid(), 1);
        action.lastCraft.put(ingredient.getGuid(), 1);

        action.cancelCraft();
        context.player.setExchangeAction(null);

        check(action.getJobCraft() == null && action.ingredients.isEmpty()
                        && action.lastCraft.isEmpty(),
                "Leaving a craft must invalidate timers and clear session ingredients");
        check(!context.player.isAway(),
                "Clearing the exchange must release the player's away state");
    }

    private static void stoppedAndStaleCraftsSendOnlyTheirTerminalPacket()
            throws Exception {
        GameObject ingredient = item(995_075, INGREDIENT_TEMPLATE_ID, 2,
                Constant.ITEM_POS_NO_EQUIPED);
        Context context = normalContext(10, ingredient);
        JobAction action = currentAction(context.player);
        Unsafe unsafe = unsafe();
        JobCraft craft = (JobCraft) unsafe.allocateInstance(JobCraft.class);
        setObject(unsafe, craft, JobCraft.class, "jobAction", action);
        action.setJobCraft(craft);
        action.broken = true;

        Method checkMethod = JobCraft.class.getDeclaredMethod(
                "check", Player.class, int.class);
        checkMethod.setAccessible(true);
        Method endMethod = JobCraft.class.getDeclaredMethod("end", boolean.class);
        endMethod.setAccessible(true);
        int beforeStop = context.stub.writeCount();
        check(!(Boolean) checkMethod.invoke(craft, context.player, 3),
                "A stopped repeat must fail its next ownership check");
        endMethod.invoke(craft, false);
        check(context.stub.writes().subList(beforeStop, context.stub.writeCount())
                        .stream().anyMatch(packet -> "Ea2".equals(String.valueOf(packet))),
                "A stopped repeat must send Ea2");
        check(context.stub.writes().subList(beforeStop, context.stub.writeCount())
                        .stream().noneMatch(packet -> "Ea1".equals(String.valueOf(packet))),
                "A stopped repeat must not also claim normal completion with Ea1");

        JobCraft stale = (JobCraft) unsafe.allocateInstance(JobCraft.class);
        setObject(unsafe, stale, JobCraft.class, "jobAction", action);
        action.setJobCraft(stale);
        action.cancelCraft();
        int beforeStale = context.stub.writeCount();
        endMethod.invoke(stale, true);
        check(context.stub.writeCount() == beforeStale,
                "A stale callback must not emit a terminal packet after cancellation");
    }

    private static Context normalContext(int level, GameObject... items)
            throws Exception {
        JobStat stat = new JobStat(1, normalJob(), level, 0);
        Context context = context(stat, items);
        check(context.player.useCraftSkill(NORMAL_SKILL_ID, 99),
                "Test craft exchange failed to open");
        return context;
    }

    private static Job normalJob() {
        return new Job(NORMAL_JOB_ID, "",
                NORMAL_SKILL_ID + ";" + RESULT_TEMPLATE_ID,
                "7008;" + NORMAL_SKILL_ID);
    }

    private static Context context(JobStat stat, GameObject... items)
            throws Exception {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        Map<Integer, GameObject> inventory = new HashMap<>();
        for (GameObject item : items)
            inventory.put(item.getGuid(), item);
        Map<Integer, JobStat> jobs = new HashMap<>();
        if (stat != null)
            jobs.put(0, stat);
        setObject(unsafe, player, Player.class, "objects", inventory);
        setObject(unsafe, player, Player.class, "_metiers", jobs);
        setObject(unsafe, player, Player.class, "_storeItems", new HashMap<>());
        setObject(unsafe, player, Player.class, "stats", new Stats());

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        Account account = (Account) unsafe.allocateInstance(Account.class);
        int accountId = nextAccountId++;
        setObject(unsafe, account, Account.class, "id", accountId);
        account.setGameClient(client);
        World.world.addAccount(account);

        setObject(unsafe, player, Player.class, "_accID", accountId);
        setObject(unsafe, client, GameClient.class, "account", account);
        setObject(unsafe, client, GameClient.class, "player", player);
        player.setOnline(true);
        return new Context(player, client, stub);
    }

    private static JobAction currentAction(Player player) {
        ExchangeAction<?> exchange = player.getExchangeAction();
        return exchange != null && exchange.getValue() instanceof JobAction
                ? (JobAction) exchange.getValue() : null;
    }

    private static JobAction actionById(Iterable<JobAction> actions, int id) {
        for (JobAction action : actions)
            if (action.getId() == id)
                return action;
        return null;
    }

    private static ObjectTemplate installTemplate(int id, int type, String stats) {
        ObjectTemplate template = new ObjectTemplate(id, stats, "Craft test " + id,
                type, 1, 1, 1, 0, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);
        return template;
    }

    private static GameObject item(int guid, int template, int quantity,
                                   int position) {
        return new GameObject(guid, template, quantity, position, "", 0);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setObject(Unsafe unsafe, Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        long offset = unsafe.objectFieldOffset(field);
        if (field.getType() == int.class)
            unsafe.putInt(target, offset, (Integer) value);
        else
            unsafe.putObject(target, offset, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class Context {
        private final Player player;
        private final GameClient client;
        private final IoSessionStub stub;

        private Context(Player player, GameClient client, IoSessionStub stub) {
            this.player = player;
            this.client = client;
            this.stub = stub;
        }
    }

    private static final class RejectingObjectData extends ObjectData {
        private RejectingObjectData() {
            super(null);
        }

        @Override
        public boolean insert(GameObject entity) {
            return false;
        }
    }
}
