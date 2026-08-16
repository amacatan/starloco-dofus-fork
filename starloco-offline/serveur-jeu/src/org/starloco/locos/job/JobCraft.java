package org.starloco.locos.job;

import org.starloco.locos.client.Player;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.util.TimerWaiter;

import java.util.concurrent.TimeUnit;

public class JobCraft {

    private final static short CRAFT_TIME = 500;

    private JobAction jobAction;
    private int time = 0;
    private boolean repeatRequested = false;

    JobCraft(JobAction jobAction, Player player) {
        this.jobAction = jobAction;

        TimerWaiter.addNext(() -> {
            boolean shouldRepeat;
            int repetitions;
            synchronized (JobCraft.this) {
                shouldRepeat = repeatRequested;
                repetitions = time;
            }
            if (!jobAction.ownsJobCraft(JobCraft.this))
                return;
            if (shouldRepeat)
                repeat(repetitions, repetitions, player);
            else {
                try {
                    jobAction.craft(false);
                } finally {
                    jobAction.completeSingleCraft(JobCraft.this);
                }
            }
        }, CRAFT_TIME, TimeUnit.MILLISECONDS);
    }

    public JobAction getJobAction() {
        return jobAction;
    }

    public synchronized void setAction(int time) {
        this.time = time;
        this.jobAction.broken = false;
        this.repeatRequested = true;
    }

    private void repeat(final int time1, final int time2, final Player player) {
        if (time2 <= 0) {
            this.end(true);
            return;
        }

        synchronized (this.jobAction) {
            if (!this.jobAction.ownsJobCraft(this))
                return;
            this.jobAction.player = player;
            this.jobAction.isRepeat = true;
        }

        if (!this.check(player, time2)) {
            this.end(false);
        } else if (time2 == 1) {
            this.end(true);
        } else {
            TimerWaiter.addNext(() -> this.repeat(time1, (time2 - 1), player), CRAFT_TIME, TimeUnit.MILLISECONDS);
        }
    }

    private boolean check(final Player player, int time2) {
        synchronized (this.jobAction) {
            if (!this.jobAction.ownsJobCraft(this))
                return false;
            if (this.jobAction.broke || this.jobAction.broken
                    || player.getExchangeAction() == null || !player.isOnline()) {
                if (player.getExchangeAction() == null)
                    this.jobAction.broken = true;
                if (player.isOnline())
                    SocketManager.GAME_SEND_Ea_PACKET(this.jobAction.player,
                            this.jobAction.broken ? "2" : "4");
                return false;
            }
            SocketManager.GAME_SEND_EA_PACKET(this.jobAction.player, String.valueOf(time2));
            try {
                this.jobAction.craft(this.jobAction.isRepeat);
            } catch (RuntimeException exception) {
                this.jobAction.broken = true;
                SocketManager.GAME_SEND_Ec_PACKET(this.jobAction.player, "EI");
                SocketManager.GAME_SEND_Ea_PACKET(this.jobAction.player, "2");
                exception.printStackTrace();
                return false;
            }
            return !this.jobAction.broke && !this.jobAction.broken
                    && (time2 == 1 || this.jobAction.prepareNextRepeat(player));
        }
    }

    private void end(boolean notifyCompletion) {
        synchronized (this.jobAction) {
            if (!this.jobAction.ownsJobCraft(this))
                return;

            if (notifyCompletion)
                SocketManager.GAME_SEND_Ea_PACKET(this.jobAction.player, "1");
            this.jobAction.isRepeat = false;
            this.jobAction.setJobCraft(null);
            if (!this.jobAction.isMaging())
                this.jobAction.ingredients.clear();
        }
    }
}
