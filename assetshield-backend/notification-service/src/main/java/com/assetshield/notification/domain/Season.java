package com.assetshield.notification.domain;

import java.time.LocalDate;
import java.time.Month;

/**
 * Ghana seasons for tip targeting. HARMATTAN = Nov-Mar, RAINY = Apr-Jul;
 * other months match only templates with season ANY or NULL.
 */
public enum Season {
    HARMATTAN,
    RAINY,
    ANY;

    public static Season forDate(LocalDate date) {
        Month month = date.getMonth();
        if (month.getValue() >= 11 || month.getValue() <= 3) {
            return HARMATTAN;
        }
        if (month.getValue() >= 4 && month.getValue() <= 7) {
            return RAINY;
        }
        return null; // neither — only ANY/NULL-season templates match
    }
}
