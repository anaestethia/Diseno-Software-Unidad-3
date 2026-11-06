interface UiButton {
    String render();
    String click();
}

interface UiTextField {
    String render();
    String type(String text);
}

interface UiFactory {
    UiButton createButton();
    UiTextField createTextField();
    String themeName();
}

final class DarkDesktopFactory implements UiFactory {
    @Override
    public UiButton createButton() {
        return new UiButton() {
            public String render() { return "[Desktop oscuro] [ Botón ]"; }
            public String click() { return "Evento click recibido por botón oscuro."; }
        };
    }

    @Override
    public UiTextField createTextField() {
        return new UiTextField() {
            public String render() { return "[Desktop oscuro] [ Campo de texto ]"; }
            public String type(String text) { return "Texto escrito en campo oscuro: " + text; }
        };
    }

    @Override
    public String themeName() {
        return "Escritorio oscuro";
    }
}

final class MinimalMobileFactory implements UiFactory {
    @Override
    public UiButton createButton() {
        return new UiButton() {
            public String render() { return "[Móvil minimalista] Botón"; }
            public String click() { return "Pulsación táctil recibida por botón minimalista."; }
        };
    }

    @Override
    public UiTextField createTextField() {
        return new UiTextField() {
            public String render() { return "[Móvil minimalista] Campo"; }
            public String type(String text) { return "Entrada móvil recibida: " + text; }
        };
    }

    @Override
    public String themeName() {
        return "Móvil minimalista";
    }
}

final class Screen {
    private final UiButton button;
    private final UiTextField field;
    private final String theme;

    Screen(UiFactory factory) {
        theme = factory.themeName();
        button = factory.createButton();
        field = factory.createTextField();
    }

    void renderAndInteract() {
        System.out.println("Tema activo: " + theme);
        System.out.println(button.render());
        System.out.println(field.render());
        System.out.println(button.click());
        System.out.println(field.type("Hola"));
    }
}

public class Main {
    public static void main(String[] args) {
        new Screen(new DarkDesktopFactory()).renderAndInteract();
        new Screen(new MinimalMobileFactory()).renderAndInteract();
    }
}
