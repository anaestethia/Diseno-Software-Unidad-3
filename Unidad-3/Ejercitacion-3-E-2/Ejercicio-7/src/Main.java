interface UiComponent {
    String render();
    int width();
    int height();
}

interface Interactive {
    void interact();
}

interface UiFactory {
    UiComponent createButton(String label);
    UiComponent createLabel(String text);
}

final class Button implements UiComponent, Interactive {
    private final String label;
    private final int width;
    private final int height;

    Button(String label, int width, int height) {
        this.label = label;
        this.width = width;
        this.height = height;
    }

    public String render() { return "Botón[" + label + "] " + width + "x" + height; }
    public int width() { return width; }
    public int height() { return height; }
    public void interact() { System.out.println("Evento click: " + label); }
}

final class Label implements UiComponent, Interactive {
    private final String text;
    private final int width;
    private final int height;

    Label(String text, int width, int height) {
        this.text = text;
        this.width = width;
        this.height = height;
    }

    public String render() { return "Etiqueta[" + text + "] " + width + "x" + height; }
    public int width() { return width; }
    public int height() { return height; }
    public void interact() { System.out.println("Evento selección: " + text); }
}

abstract class ScreenFactory implements UiFactory {
    private final String mode;
    private final int componentWidth;
    private final int buttonHeight;
    private final int labelHeight;

    ScreenFactory(String mode, int componentWidth, int buttonHeight, int labelHeight) {
        this.mode = mode;
        this.componentWidth = componentWidth;
        this.buttonHeight = buttonHeight;
        this.labelHeight = labelHeight;
    }

    final String mode() { return mode; }
    final int componentWidth() { return componentWidth; }
    final int buttonHeight() { return buttonHeight; }
    final int labelHeight() { return labelHeight; }
}

final class DesktopFactory extends ScreenFactory {
    DesktopFactory() { super("Escritorio", 180, 44, 32); }

    public UiComponent createButton(String label) {
        return new Button(label, componentWidth(), buttonHeight());
    }

    public UiComponent createLabel(String text) {
        return new Label(text, componentWidth(), labelHeight());
    }
}

final class MobileFactory extends ScreenFactory {
    MobileFactory() { super("Móvil", 320, 52, 40); }

    public UiComponent createButton(String label) {
        return new Button(label, componentWidth(), buttonHeight());
    }

    public UiComponent createLabel(String text) {
        return new Label(text, componentWidth(), labelHeight());
    }
}

public class Main {
    public static void main(String[] args) {
        renderMode("Escritorio", new DesktopFactory());
        renderMode("Móvil", new MobileFactory());
    }

    private static void renderMode(String name, UiFactory factory) {
        UiComponent button = factory.createButton("Guardar");
        UiComponent label = factory.createLabel("Perfil");
        System.out.println(name + ": " + button.render() + " | " + label.render());
        ((Interactive) button).interact();
        ((Interactive) label).interact();
    }
}
