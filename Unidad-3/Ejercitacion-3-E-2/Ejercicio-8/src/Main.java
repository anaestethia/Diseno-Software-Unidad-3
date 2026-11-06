import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

interface DataImporter {
    Map<String, String> read(String input) throws Exception;
}

interface ImporterCreator {
    DataImporter createImporter();
}

final class CsvImporterCreator implements ImporterCreator {
    public DataImporter createImporter() { return new CsvImporter(); }
}

final class XmlImporterCreator implements ImporterCreator {
    public DataImporter createImporter() { return new XmlImporter(); }
}

final class JsonImporterCreator implements ImporterCreator {
    public DataImporter createImporter() { return new FlatJsonImporter(); }
}

final class CsvImporter implements DataImporter {
    public Map<String, String> read(String input) {
        String[] lines = input.strip().split("\\R");
        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV requiere encabezado y una fila.");
        }
        List<String> headers = parseCsvRow(lines[0]);
        List<String> values = parseCsvRow(lines[1]);
        if (headers.size() != values.size()) {
            throw new IllegalArgumentException("La fila CSV no coincide con sus encabezados.");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            result.put(headers.get(i), values.get(i));
        }
        return result;
    }

    private static List<String> parseCsvRow(String row) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char current = row.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString().strip());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Campo CSV con comillas sin cerrar.");
        }
        fields.add(field.toString().strip());
        return fields;
    }
}

final class XmlImporter implements DataImporter {
    public Map<String, String> read(String input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(input))).getDocumentElement();
        NodeList entries = root.getElementsByTagName("entry");
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String key = entry.getAttribute("key");
            if (key.isBlank() || result.putIfAbsent(key, entry.getTextContent().strip()) != null) {
                throw new IllegalArgumentException("Clave XML vacía o duplicada.");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("XML debe contener elementos entry.");
        }
        return result;
    }
}

final class FlatJsonImporter implements DataImporter {
    public Map<String, String> read(String input) {
        return new FlatJsonParser(input).parse();
    }
}

final class FlatJsonParser {
    private final String source;
    private int index;

    FlatJsonParser(String source) {
        this.source = source;
    }

    Map<String, String> parse() {
        if (source == null) throw invalid("entrada ausente");
        Map<String, String> result = new LinkedHashMap<>();
        whitespace();
        expect('{');
        whitespace();
        if (take('}')) {
            whitespace();
            if (index != source.length()) throw invalid("contenido posterior al objeto");
            return Collections.unmodifiableMap(result);
        }
        do {
            whitespace();
            String key = string();
            whitespace();
            expect(':');
            whitespace();
            String value = peek() == '"' ? string() : scalar();
            if (result.putIfAbsent(key, value) != null) {
                throw invalid("clave duplicada");
            }
            whitespace();
            if (take('}')) {
                whitespace();
                if (index != source.length()) {
                    throw invalid("contenido posterior al objeto");
                }
                return Collections.unmodifiableMap(result);
            }
            expect(',');
        } while (true);
    }

    private String string() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"') {
                return value.toString();
            }
            if (current == '\\') {
                if (index == source.length()) throw invalid("escape incompleto");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"': case '\\': case '/': value.append(escaped); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (index + 4 > source.length()) throw invalid("escape Unicode incompleto");
                        try {
                            value.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw invalid("escape Unicode inválido");
                        }
                        index += 4;
                        break;
                    default: throw invalid("escape inválido");
                }
            } else {
                if (current < 0x20) throw invalid("carácter de control");
                value.append(current);
            }
        }
        throw invalid("cadena sin cerrar");
    }

    private String scalar() {
        int start = index;
        while (index < source.length() && ",}".indexOf(source.charAt(index)) < 0
                && !Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        if (start == index) throw invalid("valor ausente");
        String value = source.substring(start, index);
        if (!value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
                && !List.of("true", "false", "null").contains(value)) {
            throw invalid("solo se admiten valores escalares");
        }
        return value;
    }

    private char peek() {
        if (index >= source.length()) throw invalid("fin inesperado");
        return source.charAt(index);
    }

    private void expect(char expected) {
        if (!take(expected)) throw invalid("se esperaba '" + expected + "'");
    }

    private boolean take(char expected) {
        if (index < source.length() && source.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void whitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
    }

    private IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("JSON inválido en posición " + index + ": " + reason);
    }
}

final class ImporterRegistry {
    private final Map<String, ImporterCreator> creators = Map.of(
            "csv", new CsvImporterCreator(),
            "xml", new XmlImporterCreator(),
            "json", new JsonImporterCreator());

    DataImporter createFor(String extension) {
        if (extension == null) throw new IllegalArgumentException("Formato obligatorio.");
        ImporterCreator creator = creators.get(extension.toLowerCase(Locale.ROOT));
        if (creator == null) throw new IllegalArgumentException("Formato no registrado: " + extension);
        return creator.createImporter();
    }
}

final class ImportService {
    private final ImporterRegistry registry;

    ImportService(ImporterRegistry registry) {
        this.registry = registry;
    }

    Map<String, String> importData(String format, String contents) throws Exception {
        return registry.createFor(format).read(contents);
    }
}

public class Main {
    public static void main(String[] args) throws Exception {
        ImportService service = new ImportService(new ImporterRegistry());
        System.out.println("CSV  -> " + service.importData("csv", "id,nombre\n7,Ada"));
        System.out.println("XML  -> " + service.importData("xml",
                "<record><entry key=\"id\">8</entry><entry key=\"nombre\">Lin</entry></record>"));
        System.out.println("JSON -> " + service.importData("json",
                "{\"id\":9,\"nombre\":\"Sam\",\"activo\":true}"));
    }
}
