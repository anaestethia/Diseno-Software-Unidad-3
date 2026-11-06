import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

final class TitleRequest {
    private final String jurisdiction;
    private final String ownerName;
    private final String identityNumber;
    private final String parcelNumber;
    private final String propertyAddress;

    TitleRequest(String jurisdiction, String ownerName, String identityNumber,
            String parcelNumber, String propertyAddress) {
        this.jurisdiction = normalize(jurisdiction);
        this.ownerName = normalize(ownerName);
        this.identityNumber = normalize(identityNumber);
        this.parcelNumber = normalize(parcelNumber);
        this.propertyAddress = normalize(propertyAddress);
    }

    String jurisdiction() { return jurisdiction; }
    String ownerName() { return ownerName; }
    String identityNumber() { return identityNumber; }
    String parcelNumber() { return parcelNumber; }
    String propertyAddress() { return propertyAddress; }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
    }
}

final class PropertyTitle {
    private final String titleId;
    private final String jurisdiction;
    private final String ownerName;
    private final String identityNumber;
    private final String parcelNumber;
    private final String propertyAddress;
    private final String validationCode;
    private final String digitalSignature;

    PropertyTitle(String titleId, String jurisdiction, String ownerName, String identityNumber,
            String parcelNumber, String propertyAddress, String validationCode,
            String digitalSignature) {
        this.titleId = titleId;
        this.jurisdiction = jurisdiction;
        this.ownerName = ownerName;
        this.identityNumber = identityNumber;
        this.parcelNumber = parcelNumber;
        this.propertyAddress = propertyAddress;
        this.validationCode = validationCode;
        this.digitalSignature = digitalSignature;
    }

    String titleId() { return titleId; }
    String jurisdiction() { return jurisdiction; }
    String ownerName() { return ownerName; }
    String parcelNumber() { return parcelNumber; }
    String validationCode() { return validationCode; }
    String digitalSignature() { return digitalSignature; }

    boolean verify(PublicKey publicKey) throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(signedPayload().getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(digitalSignature));
    }

    String signedPayload() {
        return encode(titleId) + encode(jurisdiction) + encode(ownerName) + encode(identityNumber)
                + encode(parcelNumber) + encode(propertyAddress) + encode(validationCode);
    }

    private static String encode(String field) {
        return field.length() + ":" + field;
    }
}

abstract class JurisdictionIssuer {
    private final String code;
    private final Pattern identityRule;
    private final Pattern parcelRule;
    private final KeyPair signingKey;
    private final SecureRandom random = new SecureRandom();

    JurisdictionIssuer(String code, String identityRegex, String parcelRegex) throws Exception {
        this.code = code;
        this.identityRule = Pattern.compile(identityRegex);
        this.parcelRule = Pattern.compile(parcelRegex);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        this.signingKey = generator.generateKeyPair();
    }

    final PropertyTitle issue(TitleRequest request) throws Exception {
        validate(request);
        String titleId = UUID.randomUUID().toString();
        String validationCode = validationCode(request, titleId);
        PropertyTitle unsigned = new PropertyTitle(titleId, code, request.ownerName(),
                request.identityNumber(), request.parcelNumber(), request.propertyAddress(),
                validationCode, "");
        String signature = sign(unsigned);
        return new PropertyTitle(titleId, code, request.ownerName(), request.identityNumber(),
                request.parcelNumber(), request.propertyAddress(), validationCode, signature);
    }

    final PublicKey publicKey() {
        return signingKey.getPublic();
    }

    final String code() {
        return code;
    }

    private void validate(TitleRequest request) {
        if (request == null || !code.equals(request.jurisdiction())) {
            throw new IllegalArgumentException("La solicitud debe indicar la jurisdicción registrada.");
        }
        if (!request.ownerName().matches("[\\p{L}\\p{M} .'-]{2,100}")
                || hasControlCharacters(request.ownerName())) {
            throw new IllegalArgumentException("Nombre del titular inválido.");
        }
        if (!identityRule.matcher(request.identityNumber()).matches()) {
            throw new IllegalArgumentException("Identidad no válida para " + code + ".");
        }
        if (!parcelRule.matcher(request.parcelNumber()).matches()) {
            throw new IllegalArgumentException("Identificador catastral inválido para " + code + ".");
        }
        if (!request.propertyAddress().matches("[\\p{L}\\p{M}0-9 .,'/#-]{5,160}")
                || hasControlCharacters(request.propertyAddress())) {
            throw new IllegalArgumentException("Domicilio registral obligatorio o inválido.");
        }
    }

    private String sign(PropertyTitle title) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKey.getPrivate(), random);
        signer.update(title.signedPayload().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String validationCode(TitleRequest request, String titleId) throws Exception {
        String source = code + "|" + titleId + "|" + request.parcelNumber()
                + "|" + request.identityNumber();
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8));
        return code + "-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
                .substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private static boolean hasControlCharacters(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}

final class ArgentinaIssuer extends JurisdictionIssuer {
    ArgentinaIssuer() throws Exception { super("AR", "[0-9]{7,11}", "[A-Z0-9-]{3,24}"); }
}

final class UruguayIssuer extends JurisdictionIssuer {
    UruguayIssuer() throws Exception { super("UY", "[0-9]{8,12}", "[A-Z0-9-]{3,24}"); }
}

final class PropertyTitleFactory {
    private final Map<String, JurisdictionIssuer> issuers = new ConcurrentHashMap<>();

    void register(JurisdictionIssuer issuer) {
        if (issuer == null) throw new IllegalArgumentException("La jurisdicción es obligatoria.");
        if (issuers.putIfAbsent(issuerCode(issuer), issuer) != null) {
            throw new IllegalArgumentException("Jurisdicción ya registrada.");
        }
    }

    PropertyTitle issue(TitleRequest request) throws Exception {
        if (request == null || request.jurisdiction().isBlank()) {
            throw new IllegalArgumentException("No se emite un título sin jurisdicción.");
        }
        JurisdictionIssuer issuer = issuers.get(request.jurisdiction());
        if (issuer == null) throw new IllegalArgumentException("Jurisdicción no registrada.");
        return issuer.issue(request);
    }

    PublicKey verificationKey(String jurisdiction) {
        JurisdictionIssuer issuer = issuers.get(jurisdiction);
        if (issuer == null) throw new IllegalArgumentException("Jurisdicción no registrada.");
        return issuer.publicKey();
    }

    private static String issuerCode(JurisdictionIssuer issuer) {
        return issuer.code();
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        PropertyTitleFactory factory = new PropertyTitleFactory();
        JurisdictionIssuer argentina = new ArgentinaIssuer();
        factory.register(argentina);
        factory.register(new UruguayIssuer());

        TitleRequest request = new TitleRequest(
                "AR", "Ana Pérez", "30123456", "CATA-2026-001", "Av. Central 123, Córdoba");
        PropertyTitle title = factory.issue(request);
        System.out.println("Título emitido: " + title.titleId());
        System.out.println("Jurisdicción y parcela: " + title.jurisdiction() + " / " + title.parcelNumber());
        System.out.println("Código notarial: " + title.validationCode());
        System.out.println("Firma Ed25519 verificada: "
                + title.verify(factory.verificationKey(title.jurisdiction())));

        try {
            factory.issue(new TitleRequest("AR", "Ana Pérez", "ABC", "", ""));
        } catch (IllegalArgumentException ex) {
            System.out.println("Solicitud inválida rechazada: " + ex.getMessage());
        }
        try {
            factory.issue(new TitleRequest("AR", "Ana Pérez", "30123456",
                    "CATA-2026-002", "<script>alert(1)</script>"));
        } catch (IllegalArgumentException ex) {
            System.out.println("Dirección de inyección rechazada: " + ex.getMessage());
        }

        runConcurrentIssuance(factory);
        System.out.println("Emisiones concurrentes válidas: 8");
        System.out.println("Demo académica: las claves son efímeras; no es un título registral oficial.");
    }

    private static void runConcurrentIssuance(PropertyTitleFactory factory) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final int sequence = i;
                results.add(workers.submit(() -> {
                    TitleRequest request = new TitleRequest("UY", "Luis Silva",
                            "12345678", "UY-" + sequence, "Calle 10 456, Montevideo");
                    PropertyTitle title = factory.issue(request);
                    return title.verify(factory.verificationKey("UY"));
                }));
            }
            for (Future<Boolean> result : results) {
                if (!result.get()) throw new IllegalStateException("Firma no válida.");
            }
        } finally {
            workers.shutdown();
        }
    }
}
