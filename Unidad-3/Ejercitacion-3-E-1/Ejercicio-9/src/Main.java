import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

enum ConnectorState { DISCONNECTED, CONNECTED, CLOSED }

record ConnectorConfig(String endpoint, String clientId, String credential, int timeoutMillis) {
    ConnectorConfig {
        if (endpoint == null || clientId == null || clientId.isBlank()
                || credential == null || credential.isBlank()
                || timeoutMillis < 100 || timeoutMillis > 30_000) {
            throw new IllegalArgumentException("Configuración o credenciales IoT inválidas.");
        }
        if (!clientId.matches("[A-Za-z0-9._:-]{1,64}") || credential.length() > 512
                || credential.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Identidad o credencial de red inválida.");
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Endpoint IoT inválido.", ex);
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65_535))) {
            throw new IllegalArgumentException("El endpoint debe tener host y no incluir usuario ni fragmento.");
        }
        boolean secureScheme = switch (uri.getScheme() == null ? "" : uri.getScheme().toLowerCase()) {
            case "mqtts", "https", "coaps" -> true;
            default -> false;
        };
        if (!secureScheme) {
            throw new IllegalArgumentException("Se requiere MQTT/TLS, HTTPS o CoAP seguro.");
        }
    }
}

record ConnectorMetrics(long created, long connected, long reconnects, long messages) { }

interface IoTConnector extends AutoCloseable {
    String protocol();
    ConnectorState state();
    void connect();
    void reconnect();
    void publish(String payload);
    ConnectorMetrics metrics();
    @Override
    void close();
}

final class ProtocolConnector implements IoTConnector {
    private final String protocol;
    private final ConnectorConfig config;
    private final AtomicReference<ConnectorState> state =
            new AtomicReference<>(ConnectorState.DISCONNECTED);
    private final AtomicLong messages = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();

    ProtocolConnector(String protocol, ConnectorConfig config) {
        this.protocol = protocol;
        this.config = config;
    }

    public String protocol() { return protocol; }
    public ConnectorState state() { return state.get(); }

    public synchronized void connect() {
        if (!state.compareAndSet(ConnectorState.DISCONNECTED, ConnectorState.CONNECTED)) {
            throw new IllegalStateException("El conector " + protocol + " no está desconectado.");
        }
    }

    public synchronized void reconnect() {
        if (state.get() == ConnectorState.CLOSED) {
            throw new IllegalStateException("No se puede reconectar un canal cerrado.");
        }
        state.set(ConnectorState.DISCONNECTED);
        connect();
        reconnects.incrementAndGet();
    }

    public synchronized void publish(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("La telemetría no puede estar vacía.");
        }
        if (state.get() != ConnectorState.CONNECTED) {
            throw new IllegalStateException("El canal " + protocol + " no está conectado.");
        }
        messages.incrementAndGet();
    }

    public ConnectorMetrics metrics() {
        return new ConnectorMetrics(1, state.get() == ConnectorState.CONNECTED ? 1 : 0,
                reconnects.get(), messages.get());
    }

    public synchronized void close() {
        state.set(ConnectorState.CLOSED);
    }

    @Override
    public String toString() {
        return protocol + " -> " + config.endpoint();
    }
}

@FunctionalInterface
interface ConnectorFactory {
    IoTConnector create(ConnectorConfig config) throws Exception;
}

final class ConnectorInitializationException extends RuntimeException {
    ConnectorInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

abstract class SecureProtocolFactory implements ConnectorFactory {
    private final String protocol;
    private final String requiredScheme;

    SecureProtocolFactory(String protocol, String requiredScheme) {
        this.protocol = protocol;
        this.requiredScheme = requiredScheme;
    }

    @Override
    public final IoTConnector create(ConnectorConfig config) {
        try {
            if (!URI.create(config.endpoint()).getScheme().equalsIgnoreCase(requiredScheme)) {
                throw new IllegalArgumentException("El endpoint no corresponde a " + protocol + ".");
            }
            return new ProtocolConnector(protocol, config);
        } catch (Exception ex) {
            throw new ConnectorInitializationException(
                    "No se pudo inicializar " + protocol + ": " + ex.getMessage(), ex);
        }
    }
}

final class MqttConnectorFactory extends SecureProtocolFactory {
    MqttConnectorFactory() { super("MQTT", "mqtts"); }
}

final class HttpConnectorFactory extends SecureProtocolFactory {
    HttpConnectorFactory() { super("HTTP", "https"); }
}

final class CoapConnectorFactory extends SecureProtocolFactory {
    CoapConnectorFactory() { super("COAP", "coaps"); }
}

final class ConnectorRegistry {
    private final Map<String, ConnectorFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> initializations = new ConcurrentHashMap<>();

    void register(String protocol, ConnectorFactory factory) {
        if (protocol == null || !protocol.matches("[A-Z][A-Z0-9_-]{1,15}") || factory == null) {
            throw new IllegalArgumentException("Registro de protocolo inválido.");
        }
        if (factories.putIfAbsent(protocol, factory) != null) {
            throw new IllegalStateException("Ya existe una factoría para " + protocol + ".");
        }
        initializations.putIfAbsent(protocol, new AtomicLong());
    }

    IoTConnector create(String protocol, ConnectorConfig config) {
        ConnectorFactory factory = factories.get(protocol);
        if (factory == null) {
            throw new IllegalArgumentException("Protocolo no registrado: " + protocol);
        }
        try {
            IoTConnector connector = factory.create(config);
            connector.connect();
            initializations.get(protocol).incrementAndGet();
            return connector;
        } catch (Exception ex) {
            if (ex instanceof ConnectorInitializationException initializationException) {
                throw initializationException;
            }
            throw new ConnectorInitializationException(
                    "No se pudo inicializar " + protocol + ": " + ex.getMessage(), ex);
        }
    }

    long initializationCount(String protocol) {
        AtomicLong count = initializations.get(protocol);
        return count == null ? 0 : count.get();
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        ConnectorRegistry registry = new ConnectorRegistry();
        registry.register("MQTT", new MqttConnectorFactory());
        registry.register("COAP", new CoapConnectorFactory());

        IoTConnector mqtt = registry.create("MQTT",
                new ConnectorConfig("mqtts://broker.example:8883", "sensor-1", "token-demo", 5000));
        System.out.println("Canal listo: " + mqtt + " | estado=" + mqtt.state());

        // HTTP se incorpora en tiempo de ejecución sin modificar el registro ni su consumidor.
        registry.register("HTTP", new HttpConnectorFactory());
        IoTConnector http = registry.create("HTTP",
                new ConnectorConfig("https://telemetry.example/api", "sensor-2", "token-demo", 3000));
        IoTConnector coap = registry.create("COAP",
                new ConnectorConfig("coaps://gateway.example:5684", "sensor-3", "token-demo", 5000));

        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                final int reading = index;
                tasks.add(workers.submit(() -> mqtt.publish("lectura-" + reading)));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            workers.shutdown();
        }
        mqtt.reconnect();
        http.publish("temperatura=21.4");
        coap.publish("presion=1008");
        System.out.println("Métricas MQTT: " + mqtt.metrics());
        System.out.println("Factorías HTTP registradas y operativas: "
                + registry.initializationCount("HTTP") + " | " + http.state());
        System.out.println("Métricas CoAP: " + coap.metrics());

        try {
            ConnectorConfig insecure = new ConnectorConfig(
                    "mqtt://broker.example:1883", "sensor-4", "token-demo", 1000);
            registry.create("MQTT", insecure);
        } catch (IllegalArgumentException ex) {
            System.out.println("Configuración insegura rechazada: " + ex.getMessage());
        }
        registry.register("FAIL", config -> {
            throw new IllegalStateException("handshake TLS fallido");
        });
        try {
            registry.create("FAIL", new ConnectorConfig(
                    "https://telemetry.example/api", "sensor-5", "token-demo", 3000));
        } catch (ConnectorInitializationException ex) {
            System.out.println("Fallo de inicialización controlado: " + ex.getMessage());
        }
        mqtt.close();
        http.close();
        coap.close();
    }
}
