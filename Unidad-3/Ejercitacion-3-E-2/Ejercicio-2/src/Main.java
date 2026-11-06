import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

interface FileStorage {
    void save(String name, String contents) throws IOException;

    String retrieve(String name) throws IOException;

    String provider();
}

final class LocalStorage implements FileStorage {
    private final Path root;

    LocalStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void save(String name, String contents) throws IOException {
        Path file = resolve(name);
        Files.createDirectories(root);
        Files.writeString(file, contents, StandardCharsets.UTF_8);
    }

    @Override
    public String retrieve(String name) throws IOException {
        return Files.readString(resolve(name), StandardCharsets.UTF_8);
    }

    @Override
    public String provider() {
        return "disco local (" + root.getFileName() + ")";
    }

    private Path resolve(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException("Nombre de archivo inválido.");
        }
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("La ruta debe permanecer dentro del repositorio local.");
        }
        return file;
    }
}

final class CloudStorage implements FileStorage {
    private final Map<String, String> remoteObjects = new ConcurrentHashMap<>();

    @Override
    public void save(String name, String contents) {
        remoteObjects.put("bucket-media/" + name, contents);
    }

    @Override
    public String retrieve(String name) {
        return remoteObjects.get("bucket-media/" + name);
    }

    @Override
    public String provider() {
        return "nube (bucket-media)";
    }
}

abstract class StorageCreator {
    public abstract FileStorage createStorage();
}

final class LocalStorageCreator extends StorageCreator {
    private final Path root;

    LocalStorageCreator(Path root) {
        this.root = root;
    }

    @Override
    public FileStorage createStorage() {
        return new LocalStorage(root);
    }
}

final class CloudStorageCreator extends StorageCreator {
    @Override
    public FileStorage createStorage() {
        return new CloudStorage();
    }
}

final class FileService {
    private final FileStorage storage;

    FileService(StorageCreator creator) {
        this.storage = creator.createStorage();
    }

    void archive(String name, String contents) throws IOException {
        storage.save(name, contents);
        System.out.printf("%s | %s -> %s%n", storage.provider(), name, storage.retrieve(name));
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        FileService local = new FileService(
                new LocalStorageCreator(Path.of("captura", "repositorio-local")));
        FileService cloud = new FileService(new CloudStorageCreator());
        local.archive("foto-demo.txt", "foto-de-campus: contenido de demostración");
        cloud.archive("video-demo.txt", "video-de-campus: contenido de demostración");
    }
}
