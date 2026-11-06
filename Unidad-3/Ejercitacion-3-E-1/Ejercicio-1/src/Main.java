interface Document {
    String render();
}

final class PlainTextDocument implements Document {
    @Override
    public String render() {
        return "Documento de texto plano: contenido editable.";
    }
}

final class RichTextDocument implements Document {
    @Override
    public String render() {
        return "Documento enriquecido: contenido editable con formato.";
    }
}

abstract class DocumentCreator {
    protected abstract Document createDocument();

    public final void openDocument() {
        System.out.println(createDocument().render());
    }
}

final class PlainTextCreator extends DocumentCreator {
    @Override
    protected Document createDocument() {
        return new PlainTextDocument();
    }
}

final class RichTextCreator extends DocumentCreator {
    @Override
    protected Document createDocument() {
        return new RichTextDocument();
    }
}

public class Main {
    public static void main(String[] args) {
        DocumentCreator plainText = new PlainTextCreator();
        DocumentCreator richText = new RichTextCreator();
        plainText.openDocument();
        richText.openDocument();
    }
}
