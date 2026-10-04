package cn.pupperclient.utils.misc;

import org.jetbrains.annotations.NotNull;

public class RomanConverter {
    @NotNull
    public static String intToRomanByPlace(int num) {
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

        return thousands[num / 1000] +
            hundreds[(num % 1000) / 100] +
            tens[(num % 100) / 10] +
            ones[num % 10];
    }
}
