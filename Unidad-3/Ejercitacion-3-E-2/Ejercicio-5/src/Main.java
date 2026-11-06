import java.util.EnumMap;
import java.util.Map;

enum Difficulty {
    EASY(1.0, 0.8),
    NORMAL(1.4, 1.0),
    HARD(2.0, 1.3);

    private final double healthScale;
    private final double damageScale;

    Difficulty(double healthScale, double damageScale) {
        this.healthScale = healthScale;
        this.damageScale = damageScale;
    }

    double healthScale() {
        return healthScale;
    }

    double damageScale() {
        return damageScale;
    }
}

enum EnemyType {
    SOLDIER,
    DRONE
}

interface Enemy {
    String name();

    int health();

    int attackDamage();

    int speed();

    String initialTactic();
}

final class Soldier implements Enemy {
    private final int health;
    private final int damage;

    Soldier(Difficulty difficulty) {
        health = (int) Math.round(100 * difficulty.healthScale());
        damage = (int) Math.round(18 * difficulty.damageScale());
    }

    public String name() { return "Soldado raso"; }
    public int health() { return health; }
    public int attackDamage() { return damage; }
    public int speed() { return 4; }
    public String initialTactic() { return "avanzar en cobertura"; }
}

final class CombatDrone implements Enemy {
    private final int health;
    private final int damage;

    CombatDrone(Difficulty difficulty) {
        health = (int) Math.round(60 * difficulty.healthScale());
        damage = (int) Math.round(25 * difficulty.damageScale());
    }

    public String name() { return "Dron de combate"; }
    public int health() { return health; }
    public int attackDamage() { return damage; }
    public int speed() { return 9; }
    public String initialTactic() { return "patrullar desde el aire"; }
}

interface EnemyCreator {
    Enemy create(Difficulty difficulty);
}

final class SoldierCreator implements EnemyCreator {
    @Override
    public Enemy create(Difficulty difficulty) {
        return new Soldier(difficulty);
    }
}

final class DroneCreator implements EnemyCreator {
    @Override
    public Enemy create(Difficulty difficulty) {
        return new CombatDrone(difficulty);
    }
}

final class EnemyFactory {
    private final Map<EnemyType, EnemyCreator> creators = new EnumMap<>(EnemyType.class);

    EnemyFactory() {
        creators.put(EnemyType.SOLDIER, new SoldierCreator());
        creators.put(EnemyType.DRONE, new DroneCreator());
    }

    Enemy create(EnemyType type, Difficulty difficulty) {
        EnemyCreator creator = creators.get(type);
        if (creator == null || difficulty == null) {
            throw new IllegalArgumentException("Tipo o dificultad no válidos.");
        }
        return creator.create(difficulty);
    }
}

public class Main {
    public static void main(String[] args) {
        EnemyFactory factory = new EnemyFactory();
        show(factory.create(EnemyType.SOLDIER, Difficulty.NORMAL));
        show(factory.create(EnemyType.DRONE, Difficulty.HARD));
    }

    private static void show(Enemy enemy) {
        System.out.printf("%s | salud=%d daño=%d velocidad=%d | táctica: %s%n",
                enemy.name(), enemy.health(), enemy.attackDamage(), enemy.speed(),
                enemy.initialTactic());
    }
}
