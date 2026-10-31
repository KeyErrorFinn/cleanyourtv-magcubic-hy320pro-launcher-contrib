package com.gathin.tvhome;

import java.util.Objects;

/** Only these two owner-approved Global keys may use the development permission. */
final class AutomaticTimeControl {
    enum Key {
        TIME("auto_time"), ZONE("auto_time_zone");
        final String setting;
        Key(String setting) { this.setting = setting; }
    }
    interface Backend {
        String read(Key key);
        boolean write(Key key, String value);
    }
    interface Journal {
        void capture(Key key, String original);
        boolean recorded(Key key);
        String original(Key key);
    }
    private final Backend backend;
    private final Journal journal;
    AutomaticTimeControl(Backend backend, Journal journal) {
        this.backend = backend;
        this.journal = journal;
    }
    void set(Key key, String reviewed, boolean enabled) {
        change(key, reviewed, enabled ? "1" : "0", false);
    }
    void restore(Key key, String reviewed) {
        if (!journal.recorded(key)) throw new IllegalStateException("No original value recorded");
        change(key, reviewed, journal.original(key), true);
    }
    private void change(Key key, String reviewed, String value, boolean restoring) {
        if (!Objects.equals(reviewed, backend.read(key)))
            throw new IllegalStateException("This setting changed. Reopen it and try again.");
        if (Objects.equals(reviewed, value)) return;
        // Durable original first. Failed capture must prevent the provider write.
        if (!restoring) journal.capture(key, reviewed);
        if (!backend.write(key, value) || !Objects.equals(value, backend.read(key)))
            throw new IllegalStateException("The projector did not confirm this change. Reopen to check its state.");
    }
}
