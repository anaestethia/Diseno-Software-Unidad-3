interface NotificationChannel {
    String send(String message);
}

final class EmailNotification implements NotificationChannel {
    private final String recipient;

    EmailNotification(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public String send(String message) {
        return "Correo enviado a " + recipient + ": " + message;
    }
}

final class SmsNotification implements NotificationChannel {
    private final String phoneNumber;

    SmsNotification(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String send(String message) {
        return "SMS enviado a " + phoneNumber + ": " + message;
    }
}

abstract class NotificationCreator {
    protected abstract NotificationChannel createNotification();

    public final String notify(String message) {
        return createNotification().send(message);
    }
}

final class EmailCreator extends NotificationCreator {
    private final String recipient;

    EmailCreator(String recipient) {
        this.recipient = recipient;
    }

    @Override
    protected NotificationChannel createNotification() {
        return new EmailNotification(recipient);
    }
}

final class SmsCreator extends NotificationCreator {
    private final String phoneNumber;

    SmsCreator(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    protected NotificationChannel createNotification() {
        return new SmsNotification(phoneNumber);
    }
}

public class Main {
    public static void main(String[] args) {
        NotificationCreator email = new EmailCreator("ana@example.com");
        NotificationCreator sms = new SmsCreator("+5491100000000");
        System.out.println(email.notify("Su pedido fue confirmado."));
        System.out.println(sms.notify("Su pedido fue despachado."));
    }
}
