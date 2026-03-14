package com.common.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public final class UuidUtils {

    private UuidUtils() {
    }

    public static UUID uuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
