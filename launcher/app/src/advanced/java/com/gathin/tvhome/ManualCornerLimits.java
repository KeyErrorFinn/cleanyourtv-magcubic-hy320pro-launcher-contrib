package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded extraction of only the two fields used by the stock Application. Never writes config. */
final class ManualCornerLimits {
    static int[] read() throws IOException {
        File file = new File("/oem/shortcuts.config");
        if (!file.exists()) file = new File("/system/shortcuts.config");
        if (!file.isFile() || file.length() > 65536) throw new IOException("Reviewed corner configuration unavailable");
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length > 65536) throw new IOException("Configuration exceeded bound");
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }
    static int[] parse(String text) throws IOException {
        if (text.length() > 65536) throw new IOException("Configuration exceeded bound");
        String[] keys = {"manualKeystoneWidth", "manualKeystoneHeight"};
        int[] result = new int[2];
        for (int i = 0; i < keys.length; i++) {
            Matcher matcher = Pattern.compile("(?m)^\\s*[\"']?" + keys[i] + "[\"']?\\s*:\\s*(\\d+)\\s*[,}]?\\s*(?://[^\\r\\n]*)?$").matcher(text);
            if (!matcher.find()) throw new IOException("Missing configured manual-corner limit");
            String value = matcher.group(1);
            if (matcher.find() || !"1000".equals(value)) throw new IOException("Ambiguous or unreviewed manual-corner limit");
            result[i] = 1000;
        }
        return result;
    }
}
