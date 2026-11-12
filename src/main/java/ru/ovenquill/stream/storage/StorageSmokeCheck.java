package ru.ovenquill.stream.storage;

import java.nio.file.Path;

/** Runs against the final shaded JAR to verify that SQLite classes and native libraries are packaged correctly. */
public final class StorageSmokeCheck {
    private StorageSmokeCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected database path");
        try (StreamRepository ignored = new StreamRepository(Path.of(args[0]))) {
            System.out.println("SQLITE_SMOKE_OK");
        }
    }
}
