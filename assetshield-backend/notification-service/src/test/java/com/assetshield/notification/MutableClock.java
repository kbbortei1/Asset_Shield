package com.assetshield.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** Settable test clock (Africa/Accra) — season and delivery-day control. */
public class MutableClock extends Clock {

    private final ZoneId zone = ZoneId.of("Africa/Accra");
    private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());

    public void setInstant(Instant value) {
        instant.set(value);
    }

    /** Convenience: midday Accra time on the given date. */
    public void setDate(int year, int month, int day) {
        instant.set(java.time.LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant());
    }

    public void advance(java.time.Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zoneId) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
