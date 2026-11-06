import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

final class SensorRequest {
    private final int address;
    private final int frequencyHz;
    private final int channel;
    private final boolean hardwareAvailable;

    SensorRequest(int address, int frequencyHz, int channel, boolean hardwareAvailable) {
        this.address = address;
        this.frequencyHz = frequencyHz;
        this.channel = channel;
        this.hardwareAvailable = hardwareAvailable;
    }

    int address() { return address; }
    int frequencyHz() { return frequencyHz; }
    int channel() { return channel; }
    boolean hardwareAvailable() { return hardwareAvailable; }
}

interface SensorProtocolFactory {
    String protocol();
    SensorConnector create(SensorRequest request) throws SensorInitializationException;
}

interface HardwarePort extends AutoCloseable {
    double read() throws Exception;
    void close() throws Exception;
}

interface HardwareBackend {
    HardwarePort open(String protocol, SensorRequest request) throws Exception;
}

final class SimulatedHardwareBackend implements HardwareBackend {
    public HardwarePort open(String protocol, SensorRequest request) {
        if (!request.hardwareAvailable()) {
            throw new IllegalStateException("El dispositivo no respondió al iniciar el puerto.");
        }
        return new HardwarePort() {
            private final AtomicBoolean closed = new AtomicBoolean();

            public double read() {
                if (closed.get()) throw new IllegalStateException("El canal está cerrado.");
                return 18.5 + request.address() / 100.0 + request.channel() / 10.0;
            }

            public void close() {
                closed.set(true);
            }
        };
    }
}

final class SensorConnector implements AutoCloseable {
    private final String protocol;
    private final HardwarePort port;
    private final AtomicBoolean closed = new AtomicBoolean();

    SensorConnector(String protocol, HardwarePort port) {
        this.protocol = protocol;
        this.port = port;
    }

    double readTemperatureCelsius() throws SensorInitializationException {
        if (closed.get()) throw new SensorInitializationException("El conector está cerrado.");
        try {
            return port.read();
        } catch (Exception ex) {
            throw new SensorInitializationException("Fallo al leer por " + protocol + ".", ex);
        }
    }

    public void close() throws SensorInitializationException {
        if (closed.compareAndSet(false, true)) {
            try {
                port.close();
            } catch (Exception ex) {
                throw new SensorInitializationException("Fallo al liberar el canal " + protocol + ".", ex);
            }
        }
    }
}

final class SensorInitializationException extends Exception {
    SensorInitializationException(String message) { super(message); }
    SensorInitializationException(String message, Throwable cause) { super(message, cause); }
}

abstract class AbstractSensorFactory implements SensorProtocolFactory {
    private final HardwareBackend backend;

    AbstractSensorFactory(HardwareBackend backend) {
        this.backend = backend;
    }

    public final SensorConnector create(SensorRequest request) throws SensorInitializationException {
        try {
            validate(request);
            HardwarePort port = backend.open(protocol(), request);
            if (port == null) throw new IllegalStateException("El hardware no devolvió un canal.");
            return new SensorConnector(protocol(), port);
        } catch (Exception ex) {
            if (ex instanceof SensorInitializationException) {
                throw (SensorInitializationException) ex;
            }
            throw new SensorInitializationException(
                    "No se pudo inicializar " + protocol() + ": " + ex.getMessage(), ex);
        }
    }

    abstract void validate(SensorRequest request);
}

final class ModbusFactory extends AbstractSensorFactory {
    ModbusFactory(HardwareBackend backend) { super(backend); }
    public String protocol() { return "MODBUS"; }
    void validate(SensorRequest request) {
        SensorRules.require(request);
        if (request.address() < 1 || request.address() > 247
                || request.channel() < 0 || request.channel() > 65535
                || request.frequencyHz() < 1 || request.frequencyHz() > 1000) {
            throw new IllegalArgumentException("Dirección, registro o frecuencia ModBus fuera de rango.");
        }
    }
}

final class I2cFactory extends AbstractSensorFactory {
    I2cFactory(HardwareBackend backend) { super(backend); }
    public String protocol() { return "I2C"; }
    void validate(SensorRequest request) {
        SensorRules.require(request);
        if (request.address() < 0x08 || request.address() > 0x77
                || request.channel() != 0
                || request.frequencyHz() < 10000 || request.frequencyHz() > 1000000) {
            throw new IllegalArgumentException("Dirección o frecuencia I2C fuera de rango.");
        }
    }
}

final class SpiFactory extends AbstractSensorFactory {
    SpiFactory(HardwareBackend backend) { super(backend); }
    public String protocol() { return "SPI"; }
    void validate(SensorRequest request) {
        SensorRules.require(request);
        if (request.address() < 0 || request.address() > 3
                || request.channel() < 0 || request.channel() > 7
                || request.frequencyHz() < 100000 || request.frequencyHz() > 20000000) {
            throw new IllegalArgumentException("Chip-select, canal o frecuencia SPI fuera de rango.");
        }
    }
}

final class VirtualSensorFactory extends AbstractSensorFactory {
    VirtualSensorFactory(HardwareBackend backend) { super(backend); }
    public String protocol() { return "VIRTUAL"; }
    void validate(SensorRequest request) {
        SensorRules.require(request);
        if (request.address() < 0 || request.address() > 65535
                || request.channel() < 0 || request.channel() > 255
                || request.frequencyHz() < 1 || request.frequencyHz() > 50000000) {
            throw new IllegalArgumentException("Configuración del sensor virtual inválida.");
        }
    }
}

final class SensorFactoryRegistry {
    private final Map<String, SensorProtocolFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, ProtocolMetrics> metrics = new ConcurrentHashMap<>();

    void register(SensorProtocolFactory factory) {
        if (factory == null || factory.protocol() == null || factory.protocol().isBlank()) {
            throw new IllegalArgumentException("La factoría y su protocolo son obligatorios.");
        }
        String key = normalize(factory.protocol());
        if (factories.putIfAbsent(key, factory) != null) {
            throw new IllegalArgumentException("El protocolo ya está registrado: " + key);
        }
        metrics.putIfAbsent(key, new ProtocolMetrics());
    }

    SensorConnector create(String protocol, SensorRequest request) throws SensorInitializationException {
        String key = normalize(protocol);
        SensorProtocolFactory factory = factories.get(key);
        if (factory == null) throw new SensorInitializationException("Protocolo no registrado: " + key);
        ProtocolMetrics counter = metrics.get(key);
        try {
            SensorConnector connector = factory.create(request);
            counter.recordSuccess();
            return connector;
        } catch (Exception ex) {
            counter.recordFailure();
            if (ex instanceof SensorInitializationException) {
                throw (SensorInitializationException) ex;
            }
            throw new SensorInitializationException("Fallo de inicialización de " + key + ".", ex);
        }
    }

    String metric(String protocol) {
        ProtocolMetrics counter = metrics.get(normalize(protocol));
        if (counter == null) throw new IllegalArgumentException("Protocolo no registrado.");
        return counter.summary();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Protocolo obligatorio.");
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

final class ProtocolMetrics {
    private final LongAdder successes = new LongAdder();
    private final LongAdder failures = new LongAdder();

    void recordSuccess() { successes.increment(); }
    void recordFailure() { failures.increment(); }

    String summary() {
        return "creados=" + successes.sum() + ", fallidos=" + failures.sum();
    }
}

final class SensorRules {
    private SensorRules() {}

    static void require(SensorRequest request) {
        if (request == null) throw new IllegalArgumentException("La configuración es obligatoria.");
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        HardwareBackend backend = new SimulatedHardwareBackend();
        SensorFactoryRegistry registry = new SensorFactoryRegistry();
        registry.register(new ModbusFactory(backend));
        registry.register(new I2cFactory(backend));
        registry.register(new SpiFactory(backend));

        readAndClose(registry, "MODBUS", new SensorRequest(17, 100, 4, true));
        readAndClose(registry, "I2C", new SensorRequest(0x48, 100000, 0, true));
        readAndClose(registry, "SPI", new SensorRequest(1, 1000000, 2, true));
        registry.register(new VirtualSensorFactory(backend));
        readAndClose(registry, "VIRTUAL", new SensorRequest(12, 100, 1, true));
        try {
            registry.create("I2C", new SensorRequest(0x48, 100000, 0, false));
        } catch (SensorInitializationException ex) {
            System.out.println("Fallo de hardware controlado: " + ex.getMessage());
        }
        try {
            registry.create("MODBUS", new SensorRequest(0, 100, 4, true));
        } catch (SensorInitializationException ex) {
            System.out.println("Configuración ModBus rechazada: " + ex.getMessage());
        }

        runConcurrentReads(registry);
        System.out.println("Métricas MODBUS: " + registry.metric("MODBUS"));
        System.out.println("Métricas I2C: " + registry.metric("I2C"));
        System.out.println("Métricas SPI: " + registry.metric("SPI"));
        System.out.println("Métricas VIRTUAL: " + registry.metric("VIRTUAL")
                + " (registrado en tiempo de ejecución)");
    }

    private static void readAndClose(SensorFactoryRegistry registry, String protocol,
            SensorRequest request) throws Exception {
        try (SensorConnector connector = registry.create(protocol, request)) {
            System.out.printf(Locale.ROOT, "%s lectura: %.2f °C%n",
                    protocol, connector.readTemperatureCelsius());
        }
    }

    private static void runConcurrentReads(SensorFactoryRegistry registry) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                final int address = 0x40 + i;
                jobs.add(() -> {
                    try (SensorConnector connector = registry.create(
                            "I2C", new SensorRequest(address, 400000, 0, true))) {
                        connector.readTemperatureCelsius();
                        return null;
                    }
                });
            }
            List<Future<Void>> results = workers.invokeAll(jobs);
            for (Future<Void> result : results) result.get();
            System.out.println("Lecturas I2C concurrentes completadas: " + results.size());
        } finally {
            workers.shutdown();
        }
    }
}
