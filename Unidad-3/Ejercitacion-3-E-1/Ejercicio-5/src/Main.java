import java.util.Map;

record CharacterStats(String name, int health, int attack, int defense, String weapon) {
    CharacterStats {
        if (name == null || weapon == null || health <= 0 || attack <= 0 || defense < 0) {
            throw new IllegalArgumentException("Estadísticas de personaje inválidas.");
        }
    }

    String summary() {
        return name + " | vida=" + health + ", ataque=" + attack + ", defensa=" + defense
                + ", arma=" + weapon;
    }
}

interface CharacterCreator {
    CharacterStats create();
}

final class WarriorCreator implements CharacterCreator {
    @Override
    public CharacterStats create() {
        return new CharacterStats("Guerrero", 150, 25, 20, "Espada de acero");
    }
}

final class MageCreator implements CharacterCreator {
    @Override
    public CharacterStats create() {
        return new CharacterStats("Mago", 90, 38, 8, "Bastón arcano");
    }
}

final class ArcherCreator implements CharacterCreator {
    @Override
    public CharacterStats create() {
        return new CharacterStats("Arquero", 110, 30, 12, "Arco largo");
    }
}

final class CharacterWorkshop {
    private final Map<String, CharacterCreator> creators = Map.of(
            "guerrero", new WarriorCreator(),
            "mago", new MageCreator(),
            "arquero", new ArcherCreator());

    CharacterStats create(String requestedType) {
        if (requestedType == null) {
            throw new IllegalArgumentException("Debe indicar una clase de personaje.");
        }
        CharacterCreator creator = creators.get(requestedType.toLowerCase());
        if (creator == null) {
            throw new IllegalArgumentException("Clase no disponible: " + requestedType);
        }
        return creator.create();
    }
}

public class Main {
    public static void main(String[] args) {
        CharacterWorkshop workshop = new CharacterWorkshop();
        for (String type : new String[]{"guerrero", "mago", "arquero"}) {
            System.out.println(workshop.create(type).summary());
        }
    }
}
