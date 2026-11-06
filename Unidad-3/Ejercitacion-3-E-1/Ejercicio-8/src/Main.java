import java.util.Map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

record CompressionResult(String archiveName, int sizeBytes, String signature) {
    String summary() {
        return archiveName + " [" + sizeBytes + " bytes; cabecera " + signature + "]";
    }
}

interface Compressor {
    CompressionResult compress(String fileName, String content);
}

final class ZipCompressor implements Compressor {
    public CompressionResult compress(String fileName, String content) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(fileName));
                zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return result(fileName + ".zip", bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el archivo ZIP.", ex);
        }
    }

    private static CompressionResult result(String name, byte[] bytes) {
        return new CompressionResult(name, bytes.length, hexHeader(bytes));
    }

    static String hexHeader(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(Arrays.copyOf(bytes, Math.min(4, bytes.length)));
    }
}

final class TarCompressor implements Compressor {
    public CompressionResult compress(String fileName, String content) {
        byte[] payload = content.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[512];
        put(header, 0, 100, fileName);
        put(header, 100, 8, octal(0644, 7));
        put(header, 108, 8, octal(0, 7));
        put(header, 116, 8, octal(0, 7));
        put(header, 124, 12, octal(payload.length, 11));
        put(header, 136, 12, octal(System.currentTimeMillis() / 1000, 11));
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        put(header, 257, 6, "ustar");
        header[262] = 0;
        put(header, 263, 2, "00");
        long checksum = 0;
        for (byte value : header) {
            checksum += Byte.toUnsignedInt(value);
        }
        put(header, 148, 8, String.format("%06o", checksum) + "\0 ");
        int paddedLength = ((payload.length + 511) / 512) * 512;
        byte[] archive = new byte[512 + paddedLength + 1024];
        System.arraycopy(header, 0, archive, 0, 512);
        System.arraycopy(payload, 0, archive, 512, payload.length);
        return new CompressionResult(fileName + ".tar", archive.length,
                ZipCompressor.hexHeader(archive));
    }

    private static void put(byte[] target, int offset, int width, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, Math.min(width, bytes.length));
    }

    private static String octal(long value, int width) {
        return String.format("%0" + width + "o", value);
    }
}

final class GzipCompressor implements Compressor {
    public CompressionResult compress(String fileName, String content) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(content.getBytes(StandardCharsets.UTF_8));
            }
            byte[] archive = bytes.toByteArray();
            return new CompressionResult(fileName + ".gz", archive.length,
                    ZipCompressor.hexHeader(archive));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el archivo GZ.", ex);
        }
    }
}

abstract class CompressionCreator {
    protected abstract Compressor createCompressor();

    public final CompressionResult compress(String fileName, String content) {
        if (fileName == null || !fileName.matches("[A-Za-z0-9._-]{1,100}")
                || fileName.equals(".") || fileName.equals("..") || content == null) {
            throw new IllegalArgumentException("Nombre de archivo seguro y contenido son obligatorios.");
        }
        return createCompressor().compress(fileName, content);
    }
}

final class ZipCreator extends CompressionCreator {
    protected Compressor createCompressor() { return new ZipCompressor(); }
}

final class TarCreator extends CompressionCreator {
    protected Compressor createCompressor() { return new TarCompressor(); }
}

final class GzipCreator extends CompressionCreator {
    protected Compressor createCompressor() { return new GzipCompressor(); }
}

final class CompressionRegistry {
    private final Map<String, CompressionCreator> creators = Map.of(
            "zip", new ZipCreator(), "tar", new TarCreator(), "gz", new GzipCreator());

    CompressionCreator creatorFor(String format) {
        if (format == null) {
            throw new IllegalArgumentException("Debe indicar un formato.");
        }
        CompressionCreator creator = creators.get(format.toLowerCase());
        if (creator == null) {
            throw new IllegalArgumentException("Formato no soportado: " + format);
        }
        return creator;
    }
}

final class ArchiveService {
    private final CompressionCreator creator;

    ArchiveService(CompressionCreator creator) {
        this.creator = creator;
    }

    void archive(String fileName, String content) {
        System.out.println("Archivo comprimido: " + creator.compress(fileName, content).summary());
    }
}

public class Main {
    public static void main(String[] args) {
        CompressionRegistry registry = new CompressionRegistry();
        for (String format : new String[]{"zip", "tar", "gz"}) {
            new ArchiveService(registry.creatorFor(format)).archive("reporte", "datos de ejemplo");
        }
    }
}
