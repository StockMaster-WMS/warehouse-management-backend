package com.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class CodeGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");

    private CodeGenerator() {}

    /**
     * Format: {PREFIX}-{yyMMddHHmmssSSS}-{RANDOM_4_HEX}
     * Example: SP-260319230512345-9F3A
     */
    public static String generate(String prefix) {
        String p = normalizePrefix(prefix);

        String time = LocalDateTime.now().format(FORMATTER);

        String random = String.format("%04X",
                ThreadLocalRandom.current().nextInt(0, 65536));

        return p + "-" + time + "-" + random;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) return "";
        return prefix.trim().toUpperCase(Locale.ROOT)
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}