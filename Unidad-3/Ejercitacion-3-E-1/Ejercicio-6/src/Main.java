import java.util.Map;

enum PaymentMethod {
    CREDIT_CARD, BANK_TRANSFER, DIGITAL_WALLET
}

record Order(PaymentMethod method, double amount, String reference) {
    Order {
        if (method == null || reference == null || reference.isBlank()
                || !Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("La orden no es válida.");
        }
    }
}

record Receipt(String reference, PaymentMethod method, double requestedAmount,
               double chargedAmount, String transactionId) {
    String summary() {
        return "Comprobante " + transactionId + " | orden=" + reference + " | método=" + method
                + " | base=" + String.format(java.util.Locale.ROOT, "%.2f", requestedAmount)
                + " | cobrado=" + String.format(java.util.Locale.ROOT, "%.2f", chargedAmount);
    }
}

interface PaymentGateway {
    Receipt charge(Order order);
}

abstract class PaymentCreator {
    private final double minimumAmount;

    PaymentCreator(double minimumAmount) {
        this.minimumAmount = minimumAmount;
    }

    protected abstract PaymentGateway createGateway();

    public final Receipt process(Order order) {
        if (order.amount() < minimumAmount) {
            throw new IllegalArgumentException("El monto mínimo para " + order.method()
                    + " es " + minimumAmount + ".");
        }
        return createGateway().charge(order);
    }
}

final class CreditCardCreator extends PaymentCreator {
    CreditCardCreator() {
        super(1.00);
    }

    @Override
    protected PaymentGateway createGateway() {
        return order -> receipt(order, 0.03);
    }

    private static Receipt receipt(Order order, double fee) {
        return new Receipt(order.reference(), order.method(), order.amount(),
                order.amount() * (1 + fee), "TARJ-" + order.reference());
    }
}

final class BankTransferCreator extends PaymentCreator {
    BankTransferCreator() {
        super(10.00);
    }

    @Override
    protected PaymentGateway createGateway() {
        return order -> new Receipt(order.reference(), order.method(), order.amount(),
                order.amount(), "TRANS-" + order.reference());
    }
}

final class DigitalWalletCreator extends PaymentCreator {
    DigitalWalletCreator() {
        super(0.50);
    }

    @Override
    protected PaymentGateway createGateway() {
        return order -> new Receipt(order.reference(), order.method(), order.amount(),
                order.amount() * 1.015, "WALLET-" + order.reference());
    }
}

final class PaymentService {
    private final Map<PaymentMethod, PaymentCreator> creators = Map.of(
            PaymentMethod.CREDIT_CARD, new CreditCardCreator(),
            PaymentMethod.BANK_TRANSFER, new BankTransferCreator(),
            PaymentMethod.DIGITAL_WALLET, new DigitalWalletCreator());

    Receipt pay(Order order) {
        PaymentCreator creator = creators.get(order.method());
        if (creator == null) {
            throw new IllegalArgumentException("Método de pago no soportado.");
        }
        return creator.process(order);
    }
}

public class Main {
    public static void main(String[] args) {
        PaymentService service = new PaymentService();
        for (PaymentMethod method : PaymentMethod.values()) {
            System.out.println(service.pay(new Order(method, 100.00, "ORD-" + (method.ordinal() + 1)))
                    .summary());
        }
        try {
            service.pay(new Order(PaymentMethod.BANK_TRANSFER, 5.00, "ORD-LOW"));
        } catch (IllegalArgumentException ex) {
            System.out.println("Pago rechazado antes de crear la pasarela: " + ex.getMessage());
        }
    }
}
