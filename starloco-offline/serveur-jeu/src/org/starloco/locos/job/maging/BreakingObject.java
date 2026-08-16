package org.starloco.locos.job.maging;

import org.starloco.locos.game.world.World.Couple;

import java.util.ArrayList;
import java.util.List;

public class BreakingObject {

    private ArrayList<Couple<Integer, Integer>> objects = new ArrayList<>();
    private int count = 0;
    private boolean stop = false;
    private boolean running = false;

    public synchronized void setCount(int count) {
        this.count = count;
    }

    public synchronized int getCount() {
        return count;
    }

    public synchronized void setStop(boolean stop) {
        this.stop = stop;
    }

    public synchronized boolean isStop() {
        return stop;
    }

    public synchronized void setObjects(List<Couple<Integer, Integer>> objects) {
        this.objects = copy(objects);
    }

    public synchronized ArrayList<Couple<Integer, Integer>> getObjects() {
        return copy(objects);
    }

    public synchronized boolean hasObjects() {
        return !objects.isEmpty();
    }

    public synchronized int size() {
        return objects.size();
    }

    public synchronized int getSelectedQuantity(int guid) {
        Couple<Integer, Integer> couple = search(guid);
        return couple == null ? 0 : couple.second;
    }

    public synchronized ArrayList<Couple<Integer, Integer>> snapshotObjects() {
        return copy(objects);
    }

    public synchronized void clearObjects() {
        objects.clear();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void setRunning(boolean running) {
        this.running = running;
    }

    public synchronized int addObject(int id, int quantity) {
        Couple<Integer, Integer> couple = this.search(id);

        if (couple == null) {
            this.objects.add(new Couple<>(id, quantity));
            return quantity;
        } else {
            couple.second += quantity;
            return couple.second;
        }
    }

    public synchronized int removeObject(int id, int quantity) {
        Couple<Integer, Integer> couple = this.search(id);

        if (couple != null) {
            if (quantity > couple.second) {
                this.objects.remove(couple);
                return 0;
            } else {
                couple.second -= quantity;
                if (couple.second <= 0) {
                    this.objects.remove(couple);
                    return 0;
                }
                return couple.second;
            }
        }
        return 0;
    }

    private Couple<Integer, Integer> search(int id) {
        for (Couple<Integer, Integer> couple : this.objects)
            if (couple.first == id)
                return couple;
        return null;
    }

    private static ArrayList<Couple<Integer, Integer>> copy(
            List<Couple<Integer, Integer>> source) {
        ArrayList<Couple<Integer, Integer>> copy = new ArrayList<>();
        if (source == null)
            return copy;
        for (Couple<Integer, Integer> couple : source) {
            if (couple != null)
                copy.add(new Couple<>(couple.first, couple.second));
        }
        return copy;
    }
}
