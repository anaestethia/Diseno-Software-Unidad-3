import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

interface AlertChannel {
    void send(String message) throws IOException;
}

final class ConsoleAlertChannel implements AlertChannel {
    @Override
    public void send(String message) {
        System.out.println("[CONSOLA] " + message);
    }
}

final class FileAlertChannel implements AlertChannel {
    private final Path logFile;

    FileAlertChannel(Path logFile) {
        this.logFile = logFile;
    }

    @Override
    public void send(String message) throws IOException {
        Files.createDirectories(logFile.getParent());
        Files.writeString(
                logFile,
                "[ARCHIVO] " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}

abstract class AlertChannelCreator {
    public abstract AlertChannel createChannel();
}

final class ConsoleAlertCreator extends AlertChannelCreator {
    @Override
    public AlertChannel createChannel() {
        return new ConsoleAlertChannel();
    }
}

final class FileAlertCreator extends AlertChannelCreator {
    private final Path logFile;

    FileAlertCreator(Path logFile) {
        this.logFile = logFile;
    }

    @Override
    public AlertChannel createChannel() {
        return new FileAlertChannel(logFile);
    }
}

final class MonitoringAlerts {
    private final AlertChannel channel;

    MonitoringAlerts(AlertChannelCreator creator) {
        this.channel = creator.createChannel();
    }

    void notifyAdministrator(String message) throws IOException {
        channel.send(message);
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        Path log = Paths.get("captura", "alertas.log");
        new MonitoringAlerts(new ConsoleAlertCreator())
                .notifyAdministrator("CPU al 92% en servidor-01");
        new MonitoringAlerts(new FileAlertCreator(log))
                .notifyAdministrator("Disco al 88% en servidor-02");
        System.out.println("Registro persistido en " + log);
    }
}
