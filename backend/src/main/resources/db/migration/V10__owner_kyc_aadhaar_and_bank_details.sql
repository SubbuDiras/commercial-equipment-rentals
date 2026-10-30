-- Owner KYC: Aadhaar and fuller bank account details.
-- Only the last 4 digits of the Aadhaar number are kept: the full number is checked when the applicant submits it
-- and never stored (UIDAI rules allow storing it only in an approved Aadhaar data vault).
-- Nullable because owners who applied before this change did not provide them.
ALTER TABLE owner_profiles
    ADD COLUMN aadhaar_last4 CHAR(4) NULL,
    ADD COLUMN bank_name VARCHAR(100) NULL,
    ADD COLUMN bank_account_type VARCHAR(10) NULL;
