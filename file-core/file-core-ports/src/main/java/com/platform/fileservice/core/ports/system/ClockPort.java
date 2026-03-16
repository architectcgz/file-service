package com.platform.fileservice.core.ports.system;

import java.time.Instant;

/**
 * Port for time access to keep application logic deterministic.
 */
public interface ClockPort {

    Instant now();
}
