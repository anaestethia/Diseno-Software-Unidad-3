import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class ShippingOrder {
    private final double weightKg;
    private final double distanceKm;
    private final int destinationZone;
    private final String pickupBranch;

    ShippingOrder(double weightKg, double distanceKm, int destinationZone, String pickupBranch) {
        this.weightKg = weightKg;
        this.distanceKm = distanceKm;
        this.destinationZone = destinationZone;
        this.pickupBranch = pickupBranch;
    }

    double weightKg() { return weightKg; }
    double distanceKm() { return distanceKm; }
    int destinationZone() { return destinationZone; }
    String pickupBranch() { return pickupBranch; }
}

interface ShippingService {
    String carrier();
    double totalCost();
    String estimatedDelivery();
}

interface ShippingCreator {
    boolean canServe(ShippingOrder order);
    ShippingService create(ShippingOrder order);
}

final class PostalCreator implements ShippingCreator {
    @Override
    public boolean canServe(ShippingOrder order) {
        return order.destinationZone() >= 1 && order.destinationZone() <= 99
                && order.weightKg() <= 20;
    }

    @Override
    public ShippingService create(ShippingOrder order) {
        ShippingRules.validate(order, canServe(order), "Correo postal: zona 1-99 y máximo 20 kg.");
        return new ShippingService() {
            public String carrier() { return "Correo postal"; }
            public double totalCost() {
                return ShippingRules.round(250 + order.weightKg() * 120 + order.distanceKm() * 0.8);
            }
            public String estimatedDelivery() { return "5-8 días hábiles"; }
        };
    }
}

final class ExpressCreator implements ShippingCreator {
    @Override
    public boolean canServe(ShippingOrder order) {
        return order.destinationZone() >= 1 && order.destinationZone() <= 3
                && order.weightKg() <= 30;
    }

    @Override
    public ShippingService create(ShippingOrder order) {
        ShippingRules.validate(order, canServe(order), "Envío expreso: zonas 1-3 y máximo 30 kg.");
        return new ShippingService() {
            public String carrier() { return "Envío expreso privado"; }
            public double totalCost() {
                return ShippingRules.round(500 + order.weightKg() * 220 + order.distanceKm() * 1.6);
            }
            public String estimatedDelivery() { return "1-2 días hábiles"; }
        };
    }
}

final class PickupCreator implements ShippingCreator {
    private static final Pattern BRANCH_CODE = Pattern.compile("[A-Z0-9-]{2,12}");

    @Override
    public boolean canServe(ShippingOrder order) {
        return order.destinationZone() == 0 && order.weightKg() <= 100
                && order.pickupBranch() != null
                && BRANCH_CODE.matcher(order.pickupBranch()).matches();
    }

    @Override
    public ShippingService create(ShippingOrder order) {
        ShippingRules.validate(order, canServe(order), "Retiro: requiere sucursal válida y zona 0.");
        return new ShippingService() {
            public String carrier() { return "Retiro en sucursal " + order.pickupBranch(); }
            public double totalCost() { return 0; }
            public String estimatedDelivery() { return "Disponible en 1 día hábil"; }
        };
    }
}

final class ShippingFactory {
    private final List<ShippingCreator> creators =
            List.of(new PickupCreator(), new ExpressCreator(), new PostalCreator());

    ShippingService create(ShippingOrder order) {
        ShippingRules.validateCommon(order);
        for (ShippingCreator creator : creators) {
            if (creator.canServe(order)) {
                return creator.create(order);
            }
        }
        throw new IllegalArgumentException("Ningún transportista cubre el destino y el peso indicados.");
    }
}

final class ShippingRules {
    private ShippingRules() {}

    static void validateCommon(ShippingOrder order) {
        if (order == null || !Double.isFinite(order.weightKg()) || order.weightKg() <= 0
                || !Double.isFinite(order.distanceKm()) || order.distanceKm() < 0
                || order.destinationZone() < 0) {
            throw new IllegalArgumentException("Peso, distancia o zona de destino inválidos.");
        }
    }

    static void validate(ShippingOrder order, boolean valid, String message) {
        validateCommon(order);
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}

public class Main {
    public static void main(String[] args) {
        ShippingFactory factory = new ShippingFactory();
        show(factory.create(new ShippingOrder(2.5, 120, 2, null)));
        show(factory.create(new ShippingOrder(5.0, 400, 12, null)));
        show(factory.create(new ShippingOrder(3.0, 0, 0, "CENTRO-4")));
        try {
            factory.create(new ShippingOrder(45, 50, 2, null));
        } catch (IllegalArgumentException ex) {
            System.out.println("Destino no elegible: " + ex.getMessage());
        }
    }

    private static void show(ShippingService service) {
        System.out.printf(Locale.ROOT, "%s | costo=$%.2f | entrega=%s%n",
                service.carrier(), service.totalCost(), service.estimatedDelivery());
    }
}
