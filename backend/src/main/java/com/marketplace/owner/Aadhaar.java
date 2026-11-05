package com.marketplace.owner;

/** Aadhaar number checks: any 12 digits are accepted (the reviewer checks the number against the uploaded card). */
final class Aadhaar {

    private Aadhaar() {
    }

    static boolean isValid(String number) {
        return number != null && number.matches("[0-9]{12}");
    }

    static String lastFour(String number) {
        return number.substring(number.length() - 4);
    }
}
