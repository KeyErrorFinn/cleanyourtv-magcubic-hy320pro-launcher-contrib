package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Bounded, read-only subset of the exact firmware INI contract. Never returns a default. */
final class MirrorField {
    enum Path {
        OEM("/oem/panel_config.ini"), RESERVE("/Reserve0/panel_config.ini");
        final String filename;
        Path(String filename) { this.filename = filename; }
    }
    static final int LIMIT = 32768;
    static final class Value {
        final Path path;
        final int stored;
        // Digest excludes only the target line: native putl may canonicalize it/remove its comment.
        final String surroundings;
        Value(Path path, int stored, String surroundings) {
            if (path == null || stored < 0 || stored > 3 || surroundings == null
                    || !surroundings.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid present field");
            this.path = path; this.stored = stored; this.surroundings = surroundings;
        }
        Value mode(int stored) { return new Value(path, stored, surroundings); }
        boolean same(Value other) {
            return other != null && path == other.path && stored == other.stored && surroundings.equals(other.surroundings);
        }
    }
    interface Source {
        boolean exists(Path path) throws IOException;
        byte[] read(Path path) throws IOException;
    }
    static final Source FILES = new Source() {
        public boolean exists(Path path) throws IOException {
            try {
                BasicFileAttributes a = Files.readAttributes(java.nio.file.Paths.get(path.filename),
                        BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!a.isRegularFile()) throw new IOException("Fixed panel path is not a regular file");
                return true;
            } catch (NoSuchFileException absent) { return false; }
        }
        public byte[] read(Path path) throws IOException {
            try (FileInputStream in = new FileInputStream(path.filename);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[512]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > LIMIT) throw new IOException("Panel file exceeds bound");
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        }
    };
    static Value read(Source source) throws Exception {
        Path path = source.exists(Path.OEM) ? Path.OEM : Path.RESERVE;
        if (!source.exists(path)) throw new IOException("No fixed panel file");
        byte[] bytes = source.read(path);
        Value value = parse(path, bytes);
        // Reopen, reselect, and compare bytes. No cache, silent fallback or stale file descriptor.
        Path again = source.exists(Path.OEM) ? Path.OEM : Path.RESERVE;
        if (again != path || !Arrays.equals(bytes, source.read(path)))
            throw new IOException("Panel file/path changed during read");
        return value;
    }
    static Value parse(Path path, byte[] bytes) throws Exception {
        if (path == null || bytes.length == 0 || bytes.length > LIMIT) throw new IOException("Panel bounds");
        for (byte b : bytes) {
            int c = b & 255;
            if (c != 9 && c != 10 && c != 13 && c < 32) throw new IOException("Control byte in panel input");
        }
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        String section = ""; int sections = 0, fields = 0, value = -1, begin = -1, end = -1;
        int lcdFields = 0;
        int at = 0;
        while (at < text.length()) {
            int nl = text.indexOf('\n', at);
            int next = nl < 0 ? text.length() : nl + 1;
            String raw = text.substring(at, nl < 0 ? next : nl);
            if (raw.length() > 510 || raw.indexOf('\r') >= 0 && !raw.endsWith("\r"))
                throw new IOException("Ambiguous native line boundary");
            String line = raw.trim();
            if (!line.isEmpty() && !line.startsWith(";") && !line.startsWith("#")) {
                if (line.startsWith("[")) {
                    if (!line.matches("\\[[A-Za-z0-9_ .-]+\\]")) throw new IOException("Malformed section");
                    section = line.substring(1, line.length()-1).trim();
                    if (section.equalsIgnoreCase("PanelSetting") && ++sections != 1)
                        throw new IOException("Duplicate PanelSetting section");
                } else {
                    int sep = line.indexOf('='); if (sep < 0) sep = line.indexOf(':');
                    String key = (sep < 0 ? line : line.substring(0, sep)).trim();
                    if (key.equalsIgnoreCase("LcdType") && section.equalsIgnoreCase("PanelSetting")) {
                        if (++lcdFields != 1 || sep < 0 || !line.substring(sep+1).trim().matches("1(?:[ \\t]*(?:[;#].*)?)"))
                            throw new IOException("Unsupported/ambiguous native LcdType branch");
                    }
                    if (key.equalsIgnoreCase("MirrorMode")) {
                        if (!section.equalsIgnoreCase("PanelSetting") || ++fields != 1 || sep < 0)
                            throw new IOException("Ambiguous/foreign MirrorMode");
                        String field = line.substring(sep+1).trim();
                        if (!field.matches("[0-3](?:[ \\t]*(?:[;#].*)?)"))
                            throw new IOException("Malformed MirrorMode; no fallback zero");
                        value = field.charAt(0)-'0'; begin = at; end = next;
                    }
                }
            }
            at = next;
        }
        if (sections != 1 || fields != 1) throw new IOException("Present PanelSetting/MirrorMode required");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (text.substring(0, begin) + "<MirrorMode>\n" + text.substring(end)).getBytes(StandardCharsets.ISO_8859_1));
        StringBuilder hex = new StringBuilder(); for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT,"%02x", b & 255));
        return new Value(path, value, hex.toString());
    }
}
