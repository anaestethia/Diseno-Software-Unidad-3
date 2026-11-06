import java.util.Map;

interface DatabaseConnection {
    String testQuery();
}

final class SqliteConnection implements DatabaseConnection {
    private final String databaseFile;

    SqliteConnection(String databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    public String testQuery() {
        return "SQLite (" + databaseFile + "): SELECT 1 -> OK";
    }
}

final class PostgresConnection implements DatabaseConnection {
    private final String host;
    private final int port;
    private final String database;

    PostgresConnection(String host, int port, String database) {
        this.host = host;
        this.port = port;
        this.database = database;
    }

    @Override
    public String testQuery() {
        return "PostgreSQL (" + host + ":" + port + "/" + database + "): SELECT 1 -> OK";
    }
}

abstract class ConnectionCreator {
    protected abstract DatabaseConnection createConnection();

    public final DatabaseConnection connect() {
        return createConnection();
    }
}

final class SqliteCreator extends ConnectionCreator {
    private final String databaseFile;

    SqliteCreator(String databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    protected DatabaseConnection createConnection() {
        return new SqliteConnection(databaseFile);
    }
}

final class PostgresCreator extends ConnectionCreator {
    private final String host;
    private final int port;
    private final String database;

    PostgresCreator(String host, int port, String database) {
        this.host = host;
        this.port = port;
        this.database = database;
    }

    @Override
    protected DatabaseConnection createConnection() {
        return new PostgresConnection(host, port, database);
    }
}

final class ConnectionCreatorSelector {
    private final Map<String, ConnectionCreator> creators;

    ConnectionCreatorSelector() {
        creators = Map.of(
                "sqlite", new SqliteCreator("datos.db"),
                "postgres", new PostgresCreator("db.local", 5432, "inventario"));
    }

    ConnectionCreator select(String engine) {
        ConnectionCreator creator = creators.get(engine.toLowerCase());
        if (creator == null) {
            throw new IllegalArgumentException("Motor no soportado: " + engine);
        }
        return creator;
    }
}

final class DatabaseClient {
    void runTest(ConnectionCreator creator) {
        System.out.println(creator.connect().testQuery());
    }
}

public class Main {
    public static void main(String[] args) {
        ConnectionCreatorSelector selector = new ConnectionCreatorSelector();
        DatabaseClient client = new DatabaseClient();
        client.runTest(selector.select("sqlite"));
        client.runTest(selector.select("postgres"));
    }
}
