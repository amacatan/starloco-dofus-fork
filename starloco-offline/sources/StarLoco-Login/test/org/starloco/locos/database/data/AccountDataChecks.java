package org.starloco.locos.database.data;

public final class AccountDataChecks {
    private AccountDataChecks() {
    }

    public static void run() {
        String query = AccountData.accountLookupQuery("retro_1419");
        check(query.contains("account = 'retro_1419'"),
                "Account lookup must use exact equality");
        check(!query.toUpperCase().contains(" LIKE "),
                "Underscores in account names must never be interpreted as SQL wildcards");

        String escapedQuery = AccountData.accountLookupQuery("retro_'1419");
        check(escapedQuery.contains("account = 'retro_''1419'"),
                "Legacy account names must not break out of the SQL string literal");

        boolean rejectedNull = false;
        try {
            AccountData.accountLookupQuery(null);
        } catch (IllegalArgumentException expected) {
            rejectedNull = true;
        }
        check(rejectedNull, "A null account lookup must be rejected safely");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
