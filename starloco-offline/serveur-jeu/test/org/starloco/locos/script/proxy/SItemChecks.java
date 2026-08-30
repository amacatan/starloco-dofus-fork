package org.starloco.locos.script.proxy;

public final class SItemChecks {
    private SItemChecks() {
    }

    public static void run() {
        long timestamp = 1_728_888_777_666L;

        check(Long.valueOf(timestamp).equals(
                        SItem.parseDateStatTS(Long.toString(timestamp))),
                "A raw item date must be parsed as epoch milliseconds");
        check(Long.valueOf(timestamp).equals(
                        SItem.parseDateStatTS("#0#0#" + timestamp)),
                "A persisted item date must keep its epoch milliseconds");
        check(Long.valueOf(timestamp).equals(
                        SItem.parseDateStatTS("325#0#0#" + timestamp)),
                "A complete legacy date stat must keep its epoch milliseconds");

        check(SItem.parseDateStatTS(null) == null,
                "A missing item date must return nil to Lua");
        check(SItem.parseDateStatTS("") == null,
                "An empty item date must return nil to Lua");
        check(SItem.parseDateStatTS("#0#0") == null,
                "A truncated item date must return nil to Lua");
        check(SItem.parseDateStatTS("#0#0#invalid") == null,
                "A malformed item date must return nil to Lua");
        check(SItem.parseDateStatTS("9223372036854775808") == null,
                "An overflowing item date must return nil to Lua");
        check(SItem.parseDateStatTS("0") == null,
                "A zero item date must return nil to Lua");
        check(SItem.parseDateStatTS("-1") == null,
                "A negative item date must return nil to Lua");
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
