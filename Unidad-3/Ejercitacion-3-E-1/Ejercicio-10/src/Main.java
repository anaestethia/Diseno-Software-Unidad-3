import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

enum Jurisdiction {
    AR, US
}

record InvoiceRequest(Jurisdiction jurisdiction, String invoiceId, String taxpayerId,
                      String authorizationCode, double amount, String description) {
    InvoiceRequest {
        if (jurisdiction == null) {
            throw new IllegalArgumentException("La jurisdicción es obligatoria.");
        }
        if (invoiceId == null || !invoiceId.matches("[A-Z0-9-]{3,30}")) {
            throw new IllegalArgumentException("Identificador de factura inválido.");
        }
        if (taxpayerId == null || taxpayerId.isBlank()
                || authorizationCode == null || authorizationCode.isBlank()
                || description == null || description.isBlank()
                || description.chars().anyMatch(character ->
                        (character < 0x20 && character != '\n' && character != '\r' && character != '\t')
                                || character == 0xFFFE || character == 0xFFFF)
                || !Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Faltan datos fiscales obligatorios.");
        }
    }
}

final class FiscalContext {
    private final Jurisdiction jurisdiction;
    private final String taxpayerId;
    private final String authorizationCode;
    private final byte[] signingKey;

    FiscalContext(Jurisdiction jurisdiction, String taxpayerId, String authorizationCode,
                  byte[] signingKey) {
        if (jurisdiction == null || taxpayerId == null || taxpayerId.isBlank()
                || authorizationCode == null || authorizationCode.isBlank()
                || signingKey == null || signingKey.length < 32) {
            throw new IllegalArgumentException("Contexto fiscal incompleto o clave insuficiente.");
        }
        this.jurisdiction = jurisdiction;
        this.taxpayerId = taxpayerId;
        this.authorizationCode = authorizationCode;
        this.signingKey = signingKey.clone();
    }

    Jurisdiction jurisdiction() { return jurisdiction; }
    String taxpayerId() { return taxpayerId; }
    String authorizationCode() { return authorizationCode; }
    byte[] signingKey() { return signingKey.clone(); }
}

record FiscalDocument(String jurisdiction, String invoiceId, String authorizationCode,
                      String digitalSeal, String xml) {
    String summary() {
        return "Factura " + invoiceId + " (" + jurisdiction + ") | autorización="
                + authorizationCode + " | sello HMAC-SHA256=" + digitalSeal;
    }
}

abstract class FiscalCreator {
    private final Jurisdiction jurisdiction;

    FiscalCreator(Jurisdiction jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    protected abstract void validateTaxpayerId(String taxpayerId);
    protected abstract void validateAuthorizationCode(String authorizationCode);

    public final FiscalDocument create(InvoiceRequest request, FiscalContext context) {
        if (request == null || context == null) {
            throw new IllegalArgumentException("Solicitud y contexto fiscal son obligatorios.");
        }
        if (request.jurisdiction() != jurisdiction || context.jurisdiction() != jurisdiction) {
            throw new IllegalArgumentException("La jurisdicción de solicitud y contexto no coincide.");
        }
        if (!request.taxpayerId().equals(context.taxpayerId())
                || !request.authorizationCode().equals(context.authorizationCode())) {
            throw new IllegalArgumentException("Los datos fiscales no coinciden con el contexto autorizado.");
        }
        validateTaxpayerId(request.taxpayerId());
        validateAuthorizationCode(request.authorizationCode());
        return buildSignedDocument(request, context);
    }

    private FiscalDocument buildSignedDocument(InvoiceRequest request, FiscalContext context) {
        String xml = "<Comprobante pais=\"" + jurisdiction + "\" id=\"" + request.invoiceId()
                + "\"><Contribuyente>" + request.taxpayerId() + "</Contribuyente><Autorizacion>"
                + request.authorizationCode() + "</Autorizacion><Importe>"
                + String.format(java.util.Locale.ROOT, "%.2f", request.amount())
                + "</Importe><Detalle>" + escapeXml(request.description()) + "</Detalle></Comprobante>";
        String seal = sign(xml, context.signingKey());
        return new FiscalDocument(jurisdiction.name(), request.invoiceId(),
                request.authorizationCode(), seal, xml);
    }

    private static String sign(String payload, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo firmar el comprobante.", ex);
        }
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

final class ArgentinaFiscalCreator extends FiscalCreator {
    ArgentinaFiscalCreator() { super(Jurisdiction.AR); }

    protected void validateTaxpayerId(String id) {
        if (!id.matches("\\d{2}-\\d{8}-\\d")) {
            throw new IllegalArgumentException("CUIT argentino inválido.");
        }
    }

    protected void validateAuthorizationCode(String code) {
        if (!code.matches("\\d{14}")) {
            throw new IllegalArgumentException("El CAE argentino debe contener 14 dígitos.");
        }
    }
}

final class UsFiscalCreator extends FiscalCreator {
    UsFiscalCreator() { super(Jurisdiction.US); }

    protected void validateTaxpayerId(String id) {
        if (!id.matches("\\d{2}-\\d{7}")) {
            throw new IllegalArgumentException("Identificador fiscal estadounidense inválido.");
        }
    }

    protected void validateAuthorizationCode(String code) {
        if (!code.matches("[A-Z0-9]{8,20}")) {
            throw new IllegalArgumentException("Código de autorización estadounidense inválido.");
        }
    }
}

final class FiscalCreatorRegistry {
    private final Map<Jurisdiction, FiscalCreator> creators = Map.of(
            Jurisdiction.AR, new ArgentinaFiscalCreator(),
            Jurisdiction.US, new UsFiscalCreator());

    FiscalCreator creatorFor(String countryCode) {
        final Jurisdiction jurisdiction;
        try {
            jurisdiction = Jurisdiction.valueOf(countryCode);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Jurisdicción no registrada.", ex);
        }
        FiscalCreator creator = creators.get(jurisdiction);
        if (creator == null) {
            throw new IllegalArgumentException("No existe una factoría para esa jurisdicción.");
        }
        return creator;
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        byte[] key = "clave-demostracion-de-32-bytes-minimo".getBytes(StandardCharsets.UTF_8);
        FiscalContext context = new FiscalContext(Jurisdiction.AR, "20-12345678-9",
                "12345678901234", key);
        InvoiceRequest request = new InvoiceRequest(Jurisdiction.AR, "FC-A-0001",
                "20-12345678-9", "12345678901234", 1250.75, "Servicio <industrial> & soporte");
        FiscalCreator creator = new FiscalCreatorRegistry().creatorFor("AR");
        FiscalDocument document = creator.create(request, context);
        System.out.println("Comprobante validado y firmado: " + document.summary());
        System.out.println("XML listo para transmisión: " + document.xml());

        try {
            creator.create(new InvoiceRequest(Jurisdiction.AR, "FC-A-0002",
                    "20-12345678-9", "12345678901234", 100, "Solicitud"),
                    new FiscalContext(Jurisdiction.AR, "20-12345678-9", "",
                            new byte[32]));
        } catch (IllegalArgumentException ex) {
            System.out.println("Contexto incompleto rechazado: " + ex.getMessage());
        }
        try {
            new FiscalCreatorRegistry().creatorFor("AR;DROP TABLE");
        } catch (IllegalArgumentException ex) {
            System.out.println("Configuración de jurisdicción rechazada: " + ex.getMessage());
        }

        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            java.util.List<Future<FiscalDocument>> tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> workers.submit(
                            () -> creator.create(new InvoiceRequest(Jurisdiction.AR,
                                    "FC-A-" + String.format("%04d", index + 10),
                                    "20-12345678-9", "12345678901234", 100 + index, "Concurrente"),
                                    context)))
                    .toList();
            for (Future<FiscalDocument> task : tasks) {
                task.get();
            }
            System.out.println("Emisiones concurrentes verificadas: " + tasks.size());
        } finally {
            workers.shutdown();
        }
    }
}
