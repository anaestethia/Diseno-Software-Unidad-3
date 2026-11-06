import java.util.Set;
import java.util.TreeSet;

interface UserProfile {
    String role();

    Set<String> permissions();
}

final class StudentProfile implements UserProfile {
    @Override
    public String role() {
        return "Estudiante";
    }

    @Override
    public Set<String> permissions() {
        return Set.of("consultar-cursos", "inscribirse");
    }
}

final class TeacherProfile implements UserProfile {
    @Override
    public String role() {
        return "Docente";
    }

    @Override
    public Set<String> permissions() {
        return Set.of("consultar-cursos", "publicar-material", "calificar");
    }
}

abstract class ProfileCreator {
    public abstract UserProfile createProfile();
}

final class StudentProfileCreator extends ProfileCreator {
    @Override
    public UserProfile createProfile() {
        return new StudentProfile();
    }
}

final class TeacherProfileCreator extends ProfileCreator {
    @Override
    public UserProfile createProfile() {
        return new TeacherProfile();
    }
}

final class RegistrationService {
    UserProfile register(ProfileCreator creator) {
        return creator.createProfile();
    }
}

public class Main {
    public static void main(String[] args) {
        RegistrationService registration = new RegistrationService();
        printProfile(registration.register(new StudentProfileCreator()));
        printProfile(registration.register(new TeacherProfileCreator()));
    }

    private static void printProfile(UserProfile profile) {
        System.out.printf("%s | permisos: %s%n",
                profile.role(), new TreeSet<>(profile.permissions()));
    }
}
