package org.starloco.locos.entity.exchange;

import org.starloco.locos.client.Player;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.database.data.login.PlayerData;
import org.starloco.locos.game.world.World;
import org.starloco.locos.game.world.World.Couple;
import org.starloco.locos.job.JobAction;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class CraftSecureChecks {
    private CraftSecureChecks() {
    }

    public static void run() throws Exception {
        paymentMutationsRemainSynchronized();
        playerWalletMutationsAreAtomicAndBounded();
        playerSavesRemainSerializedWithAtomicPayments();
        paymentComponentsAreCappedWithoutBecomingNegative();
        failedCraftPaysOnlyTheGuaranteedAmount();
        successfulCraftPaysBothComponents();
        insufficientFundsNeverCreditTheCrafter();
        reservationsCanBeRefundedAfterAnException();
        overflowingPaymentsAreRejected();
        itemQuantitiesAreCappedToRemainingStock();
        nonPositiveItemQuantitiesAreRejected();
        itemQuantityCappingDoesNotOverflow();
        itemPaymentsAreAggregatedBeforeTransfer();
        invalidItemPaymentAggregatesAreRejected();
        secureItemTransfersReportTechnicalFailures();
        completeItemPaymentsMoveTheOwnedObjectExactlyOnce();
        safeObjectPersistenceReportsFailures();
        ingredientValidationUsesTheCurrentJobAction();
    }

    private static void paymentMutationsRemainSynchronized() throws NoSuchMethodException {
        Method setPayKamas = CraftSecure.class.getDeclaredMethod(
                "setPayKamas", byte.class, long.class);
        Method setPayItems = CraftSecure.class.getDeclaredMethod(
                "setPayItems", byte.class, boolean.class, int.class, int.class);

        check(Modifier.isSynchronized(setPayKamas.getModifiers()),
                "Secure-craft kamas changes must share the apply lock");
        check(Modifier.isSynchronized(setPayItems.getModifiers()),
                "Secure-craft item payments must share the apply lock");
    }

    private static void playerSavesRemainSerializedWithAtomicPayments()
            throws NoSuchMethodException {
        Method update = PlayerData.class.getDeclaredMethod("update", Player.class);
        Method updateKamas = PlayerData.class.getDeclaredMethod(
                "updateKamasAtomically", Player.class, Player.class);

        check(Modifier.isSynchronized(update.getModifiers()),
                "Player snapshots must not overwrite a newer atomic payment");
        check(Modifier.isSynchronized(updateKamas.getModifiers()),
                "Atomic payments must share the PlayerData save lock");
    }

    private static void playerWalletMutationsAreAtomicAndBounded() throws Exception {
        Method addKamas = Player.class.getDeclaredMethod("addKamas", long.class);
        check(Modifier.isSynchronized(addKamas.getModifiers()),
                "Concurrent wallet credits must not overwrite a secure-craft debit");

        Player richest = playerWithKamas(Long.MAX_VALUE);
        check(!richest.addKamas(1) && richest.getKamas() == Long.MAX_VALUE,
                "A wallet credit must not overflow into a negative balance");

        Player empty = playerWithKamas(0);
        check(!empty.addKamas(Long.MIN_VALUE) && empty.getKamas() == 0,
                "A debit at Long.MIN_VALUE must be rejected without overflow");
    }

    private static void paymentComponentsAreCappedWithoutBecomingNegative() {
        long conditionalFirst = CraftSecure.capPaymentComponent(80, 0, 100);
        long guaranteedSecond = CraftSecure.capPaymentComponent(50, conditionalFirst, 100);
        check(conditionalFirst == 80 && guaranteedSecond == 20,
                "A guaranteed payment must use only the balance left by the success payment");

        long guaranteedFirst = CraftSecure.capPaymentComponent(80, 0, 100);
        long conditionalSecond = CraftSecure.capPaymentComponent(50, guaranteedFirst, 100);
        check(guaranteedFirst == 80 && conditionalSecond == 20,
                "A success payment must use only the balance left by the guaranteed payment");

        check(CraftSecure.capPaymentComponent(50, 80, 100) >= 0,
                "Combining payment modes must never produce a negative component");
        check(CraftSecure.capPaymentComponent(
                        Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE) == 0,
                "Payment capping must not overflow at Long.MAX_VALUE");
    }

    private static void failedCraftPaysOnlyTheGuaranteedAmount() throws Exception {
        Player crafter = playerWithKamas(0);
        Player payer = playerWithKamas(100);
        CraftSecure.KamasReservation reservation = CraftSecure.KamasReservation.reserve(
                crafter, payer, 20, 80);

        check(reservation != null && payer.getKamas() == 0,
                "The maximum accepted payment must be reserved before crafting");
        check(reservation.settle(false), "A payment reservation must settle exactly once");
        check(crafter.getKamas() == 20 && payer.getKamas() == 80,
                "A failed craft must pay only the guaranteed component");
        check(crafter.getKamas() + payer.getKamas() == 100,
                "A failed craft must conserve the total kamas amount");
        check(!reservation.settle(false)
                        && crafter.getKamas() == 20 && payer.getKamas() == 80,
                "A settled craft payment must not be payable a second time");
    }

    private static void successfulCraftPaysBothComponents() throws Exception {
        Player crafter = playerWithKamas(0);
        Player payer = playerWithKamas(100);
        CraftSecure.KamasReservation reservation = CraftSecure.KamasReservation.reserve(
                crafter, payer, 20, 80);

        check(reservation != null && reservation.settle(true),
                "A valid successful payment must settle");
        check(crafter.getKamas() == 100 && payer.getKamas() == 0,
                "A successful craft must pay the guaranteed and conditional components");
        check(crafter.getKamas() + payer.getKamas() == 100,
                "A successful craft must conserve the total kamas amount");
    }

    private static void insufficientFundsNeverCreditTheCrafter() throws Exception {
        Player crafter = playerWithKamas(0);
        Player payer = playerWithKamas(99);

        check(CraftSecure.KamasReservation.reserve(crafter, payer, 20, 80) == null,
                "A craft payment larger than the current balance must be rejected");
        check(crafter.getKamas() == 0 && payer.getKamas() == 99,
                "A rejected debit must never be followed by a crafter credit");
    }

    private static void reservationsCanBeRefundedAfterAnException() throws Exception {
        Player crafter = playerWithKamas(0);
        Player payer = playerWithKamas(100);
        CraftSecure.KamasReservation reservation = CraftSecure.KamasReservation.reserve(
                crafter, payer, 20, 80);

        check(reservation != null, "A valid payment must be reservable");
        reservation.refund();
        check(crafter.getKamas() == 0 && payer.getKamas() == 100,
                "An aborted craft must refund the complete reservation");
    }

    private static void overflowingPaymentsAreRejected() throws Exception {
        Player payer = playerWithKamas(Long.MAX_VALUE);
        Player crafter = playerWithKamas(Long.MAX_VALUE - 5);

        check(CraftSecure.KamasReservation.reserve(crafter, payer, 3, 3) == null,
                "A payment that would overflow the crafter balance must be rejected");
        check(CraftSecure.KamasReservation.reserve(crafter, payer, -1, 0) == null,
                "A negative payment component must be rejected");
    }

    private static void itemQuantitiesAreCappedToRemainingStock() {
        check(CraftSecure.capItemQuantity(3, 10, 4) == 3,
                "An item payment within the remaining stock must be accepted unchanged");
        check(CraftSecure.capItemQuantity(8, 10, 4) == 6,
                "An item payment must be capped to the stock not already promised");
        check(CraftSecure.capItemQuantity(1, 10, 10) == 0,
                "No item may be promised once the complete stock is committed");
        check(CraftSecure.capItemQuantity(1, 10, 11) == 0,
                "An inconsistent over-committed stock must not allow another item");
    }

    private static void nonPositiveItemQuantitiesAreRejected() {
        check(CraftSecure.capItemQuantity(0, 10, 0) == 0,
                "A zero item-payment quantity must be rejected");
        check(CraftSecure.capItemQuantity(-1, 10, 0) == 0,
                "A negative item-payment quantity must be rejected");
        check(CraftSecure.capItemQuantity(1, 0, 0) == 0,
                "An empty item stack must not fund a secure craft");
        check(CraftSecure.capItemQuantity(1, -1, 0) == 0,
                "An invalid negative stock must not fund a secure craft");
    }

    private static void itemQuantityCappingDoesNotOverflow() {
        check(CraftSecure.capItemQuantity(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, 0) == Integer.MAX_VALUE,
                "The maximum valid item stack must remain representable");
        check(CraftSecure.capItemQuantity(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE - 1L) == 1,
                "Capping near the integer limit must preserve the exact remaining stock");
        check(CraftSecure.capItemQuantity(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE) == 0,
                "A Long.MAX_VALUE commitment must not overflow into available stock");
    }

    private static void itemPaymentsAreAggregatedBeforeTransfer() {
        ArrayList<Couple<Integer, Integer>> guaranteed = new ArrayList<>();
        guaranteed.add(new Couple<>(10, 2));
        guaranteed.add(new Couple<>(11, 4));
        ArrayList<Couple<Integer, Integer>> conditional = new ArrayList<>();
        conditional.add(new Couple<>(10, 3));

        Map<Integer, Integer> aggregated = CraftSecure.aggregateItemPayments(
                guaranteed, conditional);
        check(aggregated != null && aggregated.size() == 2
                        && aggregated.get(10) == 5 && aggregated.get(11) == 4,
                "Guaranteed and conditional item payments must be combined per GUID before transfer");
    }

    private static void invalidItemPaymentAggregatesAreRejected() {
        ArrayList<Couple<Integer, Integer>> overflow = new ArrayList<>();
        overflow.add(new Couple<>(10, Integer.MAX_VALUE));
        overflow.add(new Couple<>(10, 1));
        check(CraftSecure.aggregateItemPayments(overflow) == null,
                "An overflowing cumulative item payment must be rejected");

        ArrayList<Couple<Integer, Integer>> invalid = new ArrayList<>();
        invalid.add(new Couple<>(10, 0));
        check(CraftSecure.aggregateItemPayments(invalid) == null,
                "A non-positive payment line must invalidate the complete transfer batch");
    }

    private static void secureItemTransfersReportTechnicalFailures()
            throws NoSuchMethodException {
        Method giveObject = PlayerExchange.class.getDeclaredMethod(
                "giveObject", Couple.class, GameObject.class);
        check(giveObject.getReturnType() == boolean.class,
                "Secure crafting must be able to detect a failed item transfer");
    }

    private static void completeItemPaymentsMoveTheOwnedObjectExactlyOnce()
            throws Exception {
        int templateId = 994_900;
        ObjectTemplate template = new ObjectTemplate(templateId, "",
                "Secure payment test", 1, 1, 1, 0, 0,
                "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);
        GameObject payment = new GameObject(994_901, templateId, 3,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
        Player crafter = playerWithInventory(994_902);
        Player payer = playerWithInventory(994_903, payment);
        PlayerExchange exchange = new PlayerExchange(crafter, payer);

        check(exchange.giveObject(new Couple<>(payment.getGuid(), 3), payment),
                "A valid complete-stack payment must report success");
        check(!payer.getItems().containsKey(payment.getGuid())
                        && crafter.getItems().get(payment.getGuid()) == payment,
                "A complete-stack payment must move the same GUID from payer to crafter");
        check(!exchange.giveObject(
                        new Couple<>(payment.getGuid(), 3), payment),
                "The same complete-stack payment must not be transferable twice");
    }

    private static void safeObjectPersistenceReportsFailures()
            throws NoSuchMethodException {
        Method update = ObjectData.class.getDeclaredMethod(
                "updateSafely", GameObject.class);
        Method delete = ObjectData.class.getDeclaredMethod(
                "deleteSafely", GameObject.class);
        check(update.getReturnType() == boolean.class
                        && delete.getReturnType() == boolean.class,
                "Split persistence and clone cleanup must expose their SQL result");
    }

    private static void ingredientValidationUsesTheCurrentJobAction()
            throws NoSuchMethodException {
        Method validation = CraftSecure.class.getDeclaredMethod(
                "hasValidCraftItems", JobAction.class);
        check(validation.getReturnType() == boolean.class,
                "Secure ingredient validation must use the active job action's contextual filter");
    }

    private static Player playerWithKamas(long kamas) throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);

        Field kamasField = Player.class.getDeclaredField("kamas");
        kamasField.setAccessible(true);
        kamasField.setLong(player, kamas);
        return player;
    }

    private static Player playerWithInventory(int id, GameObject... items)
            throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);

        Field idField = Player.class.getDeclaredField("id");
        unsafe.putInt(player, unsafe.objectFieldOffset(idField), id);
        Map<Integer, GameObject> inventory = new HashMap<>();
        for (GameObject item : items)
            inventory.put(item.getGuid(), item);
        Field objectsField = Player.class.getDeclaredField("objects");
        unsafe.putObject(player, unsafe.objectFieldOffset(objectsField), inventory);
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
