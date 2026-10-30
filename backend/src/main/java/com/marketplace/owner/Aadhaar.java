package com.marketplace.owner;

/** Aadhaar number checks. A valid number is 12 digits, does not start with 0 or 1, and passes the Verhoeff checksum. */
final class Aadhaar {

    private static final int[][] MULTIPLY = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 2, 3, 4, 0, 6, 7, 8, 9, 5}, {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7}, {4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, {7, 6, 5, 9, 8, 2, 1, 0, 4, 3}, {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };
    private static final int[][] PERMUTE = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 5, 7, 6, 2, 8, 3, 0, 9, 4}, {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7}, {9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    private Aadhaar() {
    }

    static boolean isValid(String number) {
        if (number == null || !number.matches("[2-9][0-9]{11}")) {
            return false;
        }
        int check = 0;
        for (int i = 0; i < number.length(); i++) {
            int digit = number.charAt(number.length() - 1 - i) - '0';
            check = MULTIPLY[check][PERMUTE[i % 8][digit]];
        }
        return check == 0;
    }

    static String lastFour(String number) {
        return number.substring(number.length() - 4);
    }
}
