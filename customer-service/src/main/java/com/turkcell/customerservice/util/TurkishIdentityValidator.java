package com.turkcell.customerservice.util;

/**
 * TCKN (bireysel, 11 hane) ve VKN (kurumsal, 10 hane) algoritmik dogrulama (FR-01).
 * Sadece format + checksum; kimligin gercekten var olup olmadigi (MERNIS/GIB sorgusu)
 * MVP kapsaminda degildir.
 */
public final class TurkishIdentityValidator {

    private TurkishIdentityValidator() {
    }

    /**
     * TCKN kurallari: 11 hane, ilk hane 0 olamaz;
     * d10 = ((d1+d3+d5+d7+d9)*7 - (d2+d4+d6+d8)) mod 10;
     * d11 = (d1..d10 toplami) mod 10.
     */
    public static boolean isValidTckn(String value) {
        if (value == null || !value.matches("\\d{11}") || value.charAt(0) == '0') {
            return false;
        }
        int[] d = digits(value);
        int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
        int evenSum = d[1] + d[3] + d[5] + d[7];
        int tenth = ((oddSum * 7) - evenSum) % 10;
        if (tenth < 0) {
            tenth += 10;
        }
        if (tenth != d[9]) {
            return false;
        }
        int sumFirstTen = 0;
        for (int i = 0; i < 10; i++) {
            sumFirstTen += d[i];
        }
        return sumFirstTen % 10 == d[10];
    }

    /**
     * VKN kurallari (GIB algoritmasi): 10 hane. Ilk 9 hane icin
     * tmp = (d[i] + (9 - i)) mod 10; tmp==9 ise katki 9, degilse (tmp * 2^(9-i)) mod 9;
     * check = (10 - (toplam mod 10)) mod 10; 10. hane check'e esit olmali.
     */
    public static boolean isValidVkn(String value) {
        if (value == null || !value.matches("\\d{10}")) {
            return false;
        }
        int[] d = digits(value);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int tmp = (d[i] + (9 - i)) % 10;
            if (tmp == 9) {
                sum += 9;
            } else {
                sum += (tmp * ((int) Math.pow(2, 9 - i))) % 9;
            }
        }
        int check = (10 - (sum % 10)) % 10;
        return check == d[9];
    }

    private static int[] digits(String value) {
        int[] d = new int[value.length()];
        for (int i = 0; i < value.length(); i++) {
            d[i] = value.charAt(i) - '0';
        }
        return d;
    }
}
