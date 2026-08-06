package org.starloco.locos.entity.map;

import org.starloco.locos.client.Player;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public final class TrunkChecks {
    private static final int ITEM_TEMPLATE_ID = 990_004;
    private static final long THREAD_TIMEOUT_MILLIS = 5_000L;

    private TrunkChecks() {
    }

    public static void run() throws Exception {
        ObjectTemplate template = new ObjectTemplate(
                ITEM_TEMPLATE_ID, "", "Test trunk item", Constant.ITEM_TYPE_RESSOURCE,
                1, 1, 1, 1, "", "", 0, 0, 0, 0);
        World.world.addObjTemplate(template);

        withdrawalPrimitivesRemainSynchronized();
        concurrentKamasWithdrawalsNeverExceedTheBalance();
        aWholeObjectCanOnlyBeWithdrawnOnce();
        partialConcurrentWithdrawalsConserveTheStack();
    }

    private static void withdrawalPrimitivesRemainSynchronized() throws NoSuchMethodException {
        Method transferKamas = Trunk.class.getDeclaredMethod(
                "transferKamas", Player.class, long.class);
        Method withdrawObject = Trunk.class.getDeclaredMethod(
                "withdrawObject", int.class, int.class, BiFunction.class);

        check(Modifier.isSynchronized(transferKamas.getModifiers()),
                "Shared trunk kamas must be guarded by the trunk monitor");
        check(Modifier.isSynchronized(withdrawObject.getModifiers()),
                "Shared trunk objects must be guarded by the trunk monitor");
    }

    private static void concurrentKamasWithdrawalsNeverExceedTheBalance() throws Exception {
        Trunk trunk = new Trunk(991_001, 0, 0, 0);
        trunk.setKamas(100L);
        Player firstPlayer = playerWithKamas(0L);
        Player secondPlayer = playerWithKamas(0L);

        ConcurrentResults<Long> results = runConcurrently(
                () -> trunk.transferKamas(firstPlayer, -100L),
                () -> trunk.transferKamas(secondPlayer, -100L));

        long firstWithdrawal = results.first;
        long secondWithdrawal = results.second;
        check(firstWithdrawal + secondWithdrawal == 100L,
                "Two players must not withdraw more kamas than the trunk contains");
        check(Math.min(firstWithdrawal, secondWithdrawal) == 0L
                        && Math.max(firstWithdrawal, secondWithdrawal) == 100L,
                "Only one player can claim a trunk's final kamas balance");
        check(firstPlayer.getKamas() + secondPlayer.getKamas() == 100L,
                "Concurrent trunk withdrawals must conserve the total kamas amount");
        check(trunk.getKamas() == 0L,
                "The trunk must be empty after its complete balance is withdrawn");
    }

    private static void aWholeObjectCanOnlyBeWithdrawnOnce() throws Exception {
        Trunk trunk = new Trunk(991_002, 0, 0, 0);
        GameObject stored = item(201, 1);
        trunk.getObject().put(stored.getGuid(), stored);

        BiFunction<GameObject, Integer, GameObject> unexpectedClone = (source, quantity) -> {
            throw new AssertionError("A complete stack withdrawal must not clone the object");
        };
        ConcurrentResults<Trunk.ObjectWithdrawal> results = runConcurrently(
                () -> trunk.withdrawObject(stored.getGuid(), 1, unexpectedClone),
                () -> trunk.withdrawObject(stored.getGuid(), 1, unexpectedClone));

        int successfulWithdrawals = (results.first == null ? 0 : 1)
                + (results.second == null ? 0 : 1);
        Trunk.ObjectWithdrawal winner = results.first != null ? results.first : results.second;
        check(successfulWithdrawals == 1,
                "The same complete trunk object must not be granted to two players");
        check(winner != null && winner.withdrawn == stored && winner.remaining == null,
                "A complete withdrawal must transfer the original object exactly once");
        check(!trunk.getObject().containsKey(stored.getGuid()),
                "A completely withdrawn object must no longer remain in the trunk");
    }

    private static void partialConcurrentWithdrawalsConserveTheStack() throws Exception {
        Trunk trunk = new Trunk(991_003, 0, 0, 0);
        GameObject stored = item(301, 3);
        trunk.getObject().put(stored.getGuid(), stored);
        AtomicInteger cloneCalls = new AtomicInteger();
        AtomicInteger cloneGuids = new AtomicInteger(400);

        BiFunction<GameObject, Integer, GameObject> cloneFactory = (source, quantity) -> {
            cloneCalls.incrementAndGet();
            return item(cloneGuids.incrementAndGet(), quantity);
        };
        ConcurrentResults<Trunk.ObjectWithdrawal> results = runConcurrently(
                () -> trunk.withdrawObject(stored.getGuid(), 2, cloneFactory),
                () -> trunk.withdrawObject(stored.getGuid(), 2, cloneFactory));

        check(results.first != null && results.second != null,
                "Concurrent partial withdrawals must share all available objects");
        int firstQuantity = results.first.withdrawn.getQuantity();
        int secondQuantity = results.second.withdrawn.getQuantity();
        check(firstQuantity + secondQuantity == 3,
                "Concurrent partial withdrawals must conserve the original stack quantity");
        check(Math.min(firstQuantity, secondQuantity) == 1
                        && Math.max(firstQuantity, secondQuantity) == 2,
                "A final partial request must be capped to the remaining trunk quantity");
        check(results.first.withdrawn.getGuid() != results.second.withdrawn.getGuid(),
                "Concurrent withdrawals must never grant the same object GUID twice");
        check(cloneCalls.get() == 1,
                "Only the withdrawal that splits the stack must create a clone");
        check(!trunk.getObject().containsKey(stored.getGuid()),
                "The trunk must be empty after concurrent withdrawals consume the full stack");
    }

    private static <T> ConcurrentResults<T> runConcurrently(
            Callable<T> firstTask, Callable<T> secondTask) throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<T> firstResult = new AtomicReference<>();
        AtomicReference<T> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread firstThread = worker("trunk-check-1", start, firstTask, firstResult, failure);
        Thread secondThread = worker("trunk-check-2", start, secondTask, secondResult, failure);
        firstThread.start();
        secondThread.start();

        start.await(THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        firstThread.join(THREAD_TIMEOUT_MILLIS);
        secondThread.join(THREAD_TIMEOUT_MILLIS);

        if (firstThread.isAlive() || secondThread.isAlive()) {
            firstThread.interrupt();
            secondThread.interrupt();
            throw new AssertionError("Concurrent trunk checks did not finish before the timeout");
        }
        rethrow(failure.get());
        return new ConcurrentResults<>(firstResult.get(), secondResult.get());
    }

    private static <T> Thread worker(String name, CyclicBarrier start, Callable<T> task,
                                     AtomicReference<T> result,
                                     AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                start.await(THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                result.set(task.call());
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void rethrow(Throwable throwable) throws Exception {
        if (throwable == null)
            return;
        if (throwable instanceof Exception)
            throw (Exception) throwable;
        if (throwable instanceof Error)
            throw (Error) throwable;
        throw new AssertionError(throwable);
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

    private static GameObject item(int guid, int quantity) {
        return new GameObject(guid, ITEM_TEMPLATE_ID, quantity,
                Constant.ITEM_POS_NO_EQUIPED, "", 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class ConcurrentResults<T> {
        private final T first;
        private final T second;

        private ConcurrentResults(T first, T second) {
            this.first = first;
            this.second = second;
        }
    }
}
