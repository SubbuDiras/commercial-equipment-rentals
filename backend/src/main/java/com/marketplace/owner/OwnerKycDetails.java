package com.marketplace.owner;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The KYC details an owner submitted. Aadhaar is only ever the last 4 digits. Admins see the full bank account number
 * (they need it to set up payouts); the owner sees it masked like the Aadhaar number.
 */
public record OwnerKycDetails(
        String phone,
        String pan,
        String gstin,
        String aadhaarLast4,
        String bankName,
        String bankAccountType,
        String bankAccountNumber,
        String bankIfsc,
        String bankAccountHolderName
) {
    /** Reads the columns selected by {@link #COLUMNS}. */
    static OwnerKycDetails from(ResultSet resultSet, boolean maskAccountNumber) throws SQLException {
        String accountNumber = resultSet.getString("bank_account_number");
        return new OwnerKycDetails(
                resultSet.getString("phone"),
                resultSet.getString("pan"),
                resultSet.getString("gstin"),
                resultSet.getString("aadhaar_last4"),
                resultSet.getString("bank_name"),
                resultSet.getString("bank_account_type"),
                maskAccountNumber ? mask(accountNumber) : accountNumber,
                resultSet.getString("bank_ifsc"),
                resultSet.getString("bank_account_holder_name"));
    }

    /** Select list for {@link #from}; expects owner_profiles as op, users as u and organizations as o. */
    static final String COLUMNS = """
            u.phone, o.pan, o.gstin, op.aadhaar_last4, op.bank_name, op.bank_account_type,
            op.bank_account_number, op.bank_ifsc, op.bank_account_holder_name""";

    private static String mask(String accountNumber) {
        return accountNumber == null || accountNumber.length() <= 4
                ? accountNumber
                : "•".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
