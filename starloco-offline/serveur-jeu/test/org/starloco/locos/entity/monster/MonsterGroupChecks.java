package org.starloco.locos.entity.monster;

import java.util.*;

public final class MonsterGroupChecks {

    private MonsterGroupChecks() {}

    public static void run() {
        String grades1 = "50@0;0;0;0;0;0;0|60@0;0;0;0;0;0;0|70@0;0;0;0;0;0;0|80@0;0;0;0;0;0;0|90@0;0;0;0;0;0;0";
        String gradesMomi = "68@0;0;0;0;0;0;0|76@0;0;0;0;0;0;0|84@0;0;0;0;0;0;0|92@0;0;0;0;0;0;0|100@0;0;0;0;0;0;0";
        String gradesDemi = "70@0;0;0;0;0;0;0|80@0;0;0;0;0;0;0|90@0;0;0;0;0;0;0|100@0;0;0;0;0;0;0|110@0;0;0;0;0;0;0";
        String stats = "0,0,0,0,0|0,0,0,0,0|0,0,0,0,0|0,0,0,0,0|0,0,0,0,0";
        String statsInfos = "0;0;0;1";
        String pdvs = "100|100|100|100|100";
        String pts = "3;3|3;3|3;3|3;3|3;3";
        String inits = "100|100|100|100|100";
        String exps = "10|10|10|10|10";

        Monster regular1 = new Monster(668, 100, 0, "", grades1, "", stats, statsInfos, pdvs, pts, inits, 0, 0, exps, 1, true, 0, false, false);
        Monster mominotor = new Monster(831, 101, 0, "", gradesMomi, "", stats, statsInfos, pdvs, pts, inits, 0, 0, exps, 1, true, 0, true, false);
        Monster deminoboule = new Monster(832, 102, 0, "", gradesDemi, "", stats, statsInfos, pdvs, pts, inits, 0, 0, exps, 1, true, 0, true, false);

        List<MonsterGrade> possibles = new ArrayList<>();
        for (MonsterGrade g : regular1.getGrades().values()) possibles.add(g);
        possibles.add(mominotor.getGrades().get(1));
        possibles.add(deminoboule.getGrades().get(1));

        int mominotorCount = 0;
        int deminobouleCount = 0;

        for (int i = 0; i < 200; i++) {
            MonsterGroup group = new MonsterGroup(i + 1, 0, possibles, null, 100, 8, null);
            int momisOnGroup = 0;
            int deminisOnGroup = 0;

            for (MonsterGrade mg : group.getMobs().values()) {
                if (mg.getTemplate().getId() == 831) momisOnGroup++;
                if (mg.getTemplate().getId() == 832) deminisOnGroup++;
            }

            if (momisOnGroup > 1) {
                throw new AssertionError("Mob group contained more than 1 Mominotor: " + momisOnGroup);
            }
            if (deminisOnGroup > 1) {
                throw new AssertionError("Mob group contained more than 1 D?minoboule: " + deminisOnGroup);
            }
            if (momisOnGroup > 0 && deminisOnGroup > 0) {
                throw new AssertionError("Mob group contained both Mominotor and D?minoboule!");
            }

            if (momisOnGroup > 0) mominotorCount++;
            if (deminisOnGroup > 0) deminobouleCount++;
        }

        if (mominotorCount > 100) {
            throw new AssertionError("Mominotor appeared too frequently: " + mominotorCount + "/200");
        }
        if (deminobouleCount > 100) {
            throw new AssertionError("D?minoboule appeared too frequently: " + deminobouleCount + "/200");
        }
        System.out.println("MonsterGroupChecks: OK (Mominotor in " + mominotorCount + "/200 groups, D?minoboule in " + deminobouleCount + "/200 groups)");
    }
}
