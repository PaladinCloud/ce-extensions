package com.paladincloud.common.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

public class TimeHelper {

    private static final DateTimeFormatter zeroMinuteDateFormat = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:00Z");
    private static final DateTimeFormatter discoveryDateFormat = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ssZ");
    private static final DateTimeFormatter yearMonthDayFormat = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd");

    private TimeHelper() {
    }

    public static String formatYearMonthDay() {
        return yearMonthDayFormat.format(ZonedDateTime.now());
    }

    public static ZonedDateTime parseDiscoveryDate(String time) {
        return ZonedDateTime.parse(time, discoveryDateFormat);
    }

    public static ZonedDateTime parseISO860Date(String time) {
        return ISODateTimeFormat.dateTimeNoMillis().parseDateTime(time).withZone(DateTimeZone.UTC).toGregorianCalendar()
            .toZonedDateTime();
    }

    public static String formatZeroSeconds(ZonedDateTime time) {
        return zeroMinuteDateFormat.format(time);
    }
}
