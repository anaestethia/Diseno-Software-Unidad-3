import java.util.List;
import java.util.Objects;

record FinancialRow(String concept, double amount) {
    FinancialRow {
        if (concept == null || concept.isBlank()) {
            throw new IllegalArgumentException("El concepto es obligatorio.");
        }
    }
}

record FinancialReport(String title, List<FinancialRow> rows) {
    FinancialReport {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio.");
        }
        rows = List.copyOf(Objects.requireNonNull(rows, "Las filas son obligatorias."));
    }
}

interface ReportExporter {
    void validate(FinancialReport report);
    String export(FinancialReport report);
}

final class ReportValidation {
    private ReportValidation() { }

    static void validate(FinancialReport report) {
        Objects.requireNonNull(report, "El informe es obligatorio.");
        if (report.rows().isEmpty()) {
            throw new IllegalArgumentException("El informe debe contener datos.");
        }
        if (report.rows().stream().anyMatch(row -> !Double.isFinite(row.amount()) || row.amount() < 0)) {
            throw new IllegalArgumentException("Los importes deben ser finitos y no negativos.");
        }
    }
}

final class CsvReportExporter implements ReportExporter {
    @Override
    public void validate(FinancialReport report) {
        ReportValidation.validate(report);
    }

    @Override
    public String export(FinancialReport report) {
        validate(report);
        StringBuilder output = new StringBuilder("Concepto,Importe\n");
        for (FinancialRow row : report.rows()) {
            output.append('"').append(row.concept().replace("\"", "\"\""))
                    .append("\",").append(String.format(java.util.Locale.ROOT, "%.2f", row.amount()))
                    .append('\n');
        }
        return output.toString().stripTrailing();
    }
}

final class PdfReportExporter implements ReportExporter {
    @Override
    public void validate(FinancialReport report) {
        ReportValidation.validate(report);
    }

    @Override
    public String export(FinancialReport report) {
        validate(report);
        StringBuilder output = new StringBuilder("PDF | ").append(report.title()).append('\n');
        output.append(String.format("%-22s %12s%n", "Concepto", "Importe"));
        for (FinancialRow row : report.rows()) {
            output.append(String.format(java.util.Locale.ROOT, "%-22s %12.2f%n",
                    row.concept(), row.amount()));
        }
        output.append(String.format(java.util.Locale.ROOT, "%-22s %12.2f",
                "TOTAL", report.rows().stream().mapToDouble(FinancialRow::amount).sum()));
        return output.toString();
    }
}

abstract class ReportCreator {
    protected abstract ReportExporter createExporter();

    public final String generate(FinancialReport report) {
        ReportExporter exporter = createExporter();
        exporter.validate(report);
        return exporter.export(report);
    }
}

final class CsvReportCreator extends ReportCreator {
    @Override
    protected ReportExporter createExporter() {
        return new CsvReportExporter();
    }
}

final class PdfReportCreator extends ReportCreator {
    @Override
    protected ReportExporter createExporter() {
        return new PdfReportExporter();
    }
}

public class Main {
    public static void main(String[] args) {
        FinancialReport report = new FinancialReport("Resumen mensual - Octubre",
                List.of(new FinancialRow("Ventas", 12500.50),
                        new FinancialRow("Servicios", 2300.00),
                        new FinancialRow("Impuestos", 950.25)));
        System.out.println("=== Exportación CSV ===");
        System.out.println(new CsvReportCreator().generate(report));
        System.out.println("=== Exportación PDF (representación) ===");
        System.out.println(new PdfReportCreator().generate(report));
        try {
            new CsvReportCreator().generate(new FinancialReport("Inválido",
                    List.of(new FinancialRow("Ajuste", -1))));
        } catch (IllegalArgumentException ex) {
            System.out.println("Validación previa: " + ex.getMessage());
        }
    }
}
