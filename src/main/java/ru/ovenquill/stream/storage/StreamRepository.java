package ru.ovenquill.stream.storage;

import ru.ovenquill.stream.model.StreamRecord;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StreamRepository implements AutoCloseable {
    private final Connection connection;

    public StreamRepository(Path file) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing from the plugin JAR", exception);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS streamers (
                      uuid TEXT PRIMARY KEY NOT NULL,
                      last_name TEXT NOT NULL,
                      url TEXT,
                      platform TEXT,
                      active INTEGER NOT NULL DEFAULT 0,
                      started_at INTEGER,
                      ended_at INTEGER,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS admin_audit (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      subject_uuid TEXT NOT NULL,
                      subject_name TEXT,
                      action TEXT NOT NULL,
                      actor TEXT,
                      details TEXT,
                      created_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public synchronized Optional<StreamRecord> find(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM streamers WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public synchronized List<StreamRecord> findActive() throws SQLException {
        List<StreamRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM streamers WHERE active=1 ORDER BY started_at, uuid"); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) records.add(map(rs));
        }
        return records;
    }

    public synchronized void saveLink(UUID uuid, String name, String url, String platform) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO streamers(uuid,last_name,url,platform,active,updated_at) VALUES(?,?,?,?,0,?)
                ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name,url=excluded.url,
                  platform=excluded.platform,updated_at=excluded.updated_at
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setString(3, url);
            statement.setString(4, platform);
            statement.setLong(5, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public synchronized boolean activate(UUID uuid, String name, Instant startedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE streamers SET last_name=?,active=1,started_at=?,ended_at=NULL,updated_at=?
                WHERE uuid=? AND active=0 AND url IS NOT NULL
                """)) {
            statement.setString(1, name);
            statement.setLong(2, startedAt.toEpochMilli());
            statement.setLong(3, startedAt.toEpochMilli());
            statement.setString(4, uuid.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean deactivate(UUID uuid, Instant endedAt, String action, String actor) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE streamers SET active=0,ended_at=?,updated_at=? WHERE uuid=? AND active=1")) {
            update.setLong(1, endedAt.toEpochMilli());
            update.setLong(2, endedAt.toEpochMilli());
            update.setString(3, uuid.toString());
            if (update.executeUpdate() != 1) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement audit = connection.prepareStatement("""
                    INSERT INTO admin_audit(subject_uuid,action,actor,created_at) VALUES(?,?,?,?)
                    """)) {
                audit.setString(1, uuid.toString());
                audit.setString(2, action);
                audit.setString(3, actor);
                audit.setLong(4, endedAt.toEpochMilli());
                audit.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static StreamRecord map(ResultSet rs) throws SQLException {
        return new StreamRecord(UUID.fromString(rs.getString("uuid")), rs.getString("last_name"),
                rs.getString("url"), rs.getString("platform"), rs.getBoolean("active"),
                instant(rs, "started_at"), instant(rs, "ended_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    @Override public synchronized void close() throws SQLException { connection.close(); }
}

