import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class Invoice {
    private final String number;
    private final List<String> items;
    private final BigDecimal subtotal;
    private final BigDecimal taxRate;

    Invoice(String number, List<String> items, BigDecimal subtotal, BigDecimal taxRate) {
        this.number = number;
        this.items = List.copyOf(items);
        this.subtotal = subtotal;
        this.taxRate = taxRate;
    }

    String number() {
        return number;
    }

    List<String> items() {
        return items;
    }

    BigDecimal subtotal() {
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal tax() {
        return subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal total() {
        return subtotal().add(tax());
    }

    void validate() {
        if (number == null || !number.matches("[A-Z0-9-]{1,24}")) {
            throw new IllegalArgumentException("Número de factura inválido.");
        }
        if (items.isEmpty() || items.stream().anyMatch(item -> item == null || item.isBlank())) {
            throw new IllegalArgumentException("La factura requiere artículos válidos.");
        }
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new IllegalArgumentException("El subtotal debe ser mayor que cero.");
        }
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La tasa de impuesto debe estar entre 0 y 1.");
        }
    }
}

interface InvoiceGenerator {
    String generate(Invoice invoice) throws IOException;
}

abstract class InvoiceGeneratorCreator {
    public final String issue(Invoice invoice) throws IOException {
        if (invoice == null) {
            throw new IllegalArgumentException("La factura es obligatoria.");
        }
        invoice.validate();
        return createGenerator().generate(invoice);
    }

    protected abstract InvoiceGenerator createGenerator();
}

final class StandardPdfCreator extends InvoiceGeneratorCreator {
    private final Path destination;

    StandardPdfCreator(Path destination) {
        this.destination = destination;
    }

    @Override
    protected InvoiceGenerator createGenerator() {
        return new StandardPdfGenerator(destination);
    }
}

final class TicketCreator extends InvoiceGeneratorCreator {
    @Override
    protected InvoiceGenerator createGenerator() {
        return new TicketGenerator();
    }
}

final class StandardPdfGenerator implements InvoiceGenerator {
    private final Path destination;

    StandardPdfGenerator(Path destination) {
        this.destination = destination;
    }

    @Override
    public String generate(Invoice invoice) throws IOException {
        List<String> lines = invoiceLines(invoice);
        Files.createDirectories(destination.getParent());
        Files.write(destination, createPdf(lines).getBytes(StandardCharsets.US_ASCII));
        return "Factura PDF " + invoice.number() + " creada: " + destination
                + " (subtotal $" + invoice.subtotal() + ", impuestos $" + invoice.tax()
                + ", total $" + invoice.total() + ")";
    }

    private static List<String> invoiceLines(Invoice invoice) {
        List<String> lines = new ArrayList<>();
        lines.add("FACTURA ESTANDAR " + invoice.number());
        for (String item : invoice.items()) {
            lines.add("Articulo: " + item);
        }
        lines.add("Subtotal: $" + invoice.subtotal());
        lines.add("Impuestos: $" + invoice.tax());
        lines.add("TOTAL: $" + invoice.total());
        return lines;
    }

    private static String createPdf(List<String> lines) throws IOException {
        StringBuilder text = new StringBuilder("BT /F1 12 Tf 50 790 Td 15 TL\n");
        for (String line : lines) {
            text.append('(').append(escapePdf(line)).append(") Tj T*\n");
        }
        text.append("ET");
        byte[] stream = text.toString().getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(pdf, "%PDF-1.4\n");
        offsets.add(0);
        offsets.add(pdf.size());
        write(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(pdf.size());
        write(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        offsets.add(pdf.size());
        write(pdf, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] "
                + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>\nendobj\n");
        offsets.add(pdf.size());
        write(pdf, "4 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
        pdf.write(stream);
        write(pdf, "\nendstream\nendobj\n");
        offsets.add(pdf.size());
        write(pdf, "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        int xref = pdf.size();
        write(pdf, "xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            write(pdf, String.format("%010d 00000 n %n", offsets.get(i)));
        }
        write(pdf, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return pdf.toString(StandardCharsets.US_ASCII);
    }

    private static String escapePdf(String value) {
        return value.replaceAll("[\\r\\n\\p{Cntrl}]", " ")
                .replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }
}

final class TicketGenerator implements InvoiceGenerator {
    @Override
    public String generate(Invoice invoice) {
        return String.join(System.lineSeparator(),
                "=== TICKET DE CAJA ===",
                "Comprobante: " + invoice.number(),
                "Artículos: " + String.join(", ", invoice.items()),
                "Subtotal: $" + invoice.subtotal(),
                "Impuestos: $" + invoice.tax(),
                "TOTAL: $" + invoice.total());
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        Invoice invoice = new Invoice(
                "FAC-2026-001",
                List.of("Teclado", "Mouse"),
                new BigDecimal("1000.00"),
                new BigDecimal("0.21"));
        String pdf = new StandardPdfCreator(Paths.get("captura", "factura-estandar.pdf"))
                .issue(invoice);
        String ticket = new TicketCreator().issue(invoice);
        System.out.println(pdf);
        System.out.println(ticket);
    }
}
