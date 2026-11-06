# Ejercicio 1 - Generador de perfiles

Ejemplo de Factory Method para perfiles de estudiante y docente. El servicio de registro recibe un creador abstracto; cada creador concreto instancia su perfil, que publica un rol y permisos inmutables.

## Compilar y ejecutar

Desde esta carpeta, con JDK 17 o posterior:

```powershell
javac -encoding UTF-8 -d out src\Main.java
java -cp out Main
```

No requiere dependencias externas. `out` contiene únicamente los `.class` locales de la compilación; no hace falta incluirlo en el repositorio.

## Evidencia

Salida real de `Main`, con los roles y permisos creados por ambas factorías:

![Captura de salida del ejercicio 1](captura/salida.png)
