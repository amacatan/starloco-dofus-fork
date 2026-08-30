package org.starloco.locos.script;

import java.util.Collections;

public final class ScriptVMChecks {
    private ScriptVMChecks() {
    }

    public static void run() throws Exception {
        runtimeCallbackFailuresAreIsolated();
        customizedExecutionRemainsStrict();
    }

    private static void runtimeCallbackFailuresAreIsolated() throws Exception {
        ScriptVM vm = new ScriptVM("ScriptVMChecks");
        Object failingCallback = vm.runCustomized(
                "return function() error('intentional callback failure') end",
                Collections.emptyMap())[0];

        Object[] failedResult = vm.call(failingCallback);
        check(failedResult == null,
                "A failed runtime Lua callback must return the safe fallback");

        Object healthyCallback = vm.runCustomized(
                "return function() return 42 end",
                Collections.emptyMap())[0];
        Object[] healthyResult = vm.call(healthyCallback);
        check(healthyResult != null && healthyResult.length == 1
                        && healthyResult[0] instanceof Number
                        && ((Number) healthyResult[0]).longValue() == 42,
                "The Lua VM must remain usable after a failed callback");
    }

    private static void customizedExecutionRemainsStrict() throws Exception {
        ScriptVM vm = new ScriptVM("ScriptVMStrictChecks");
        boolean failed = false;
        try {
            vm.runCustomized("error('intentional strict failure')", Collections.emptyMap());
        } catch (RuntimeException expected) {
            failed = true;
        }

        check(failed,
                "Script loading and administrative execution must still report failures");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
