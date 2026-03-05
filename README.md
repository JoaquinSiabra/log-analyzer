# log-analyzer
Analizador de logs

## Uso

```bash
java -jar logargos.jar [ruta/al/log]
```

Si no se indica la ruta del log, se usa el valor `default.log.path` definido en `src/main/resources/log-analyzer.properties`.

## Helpers

Este repositorio incluye un pequeño helper para ejecutar Maven usando un archivo de configuración local del proyecto que desactiva los mirrors corporativos (útil cuando estás fuera de la red corporativa).

Archivos añadidos:
- `.mvn/no-mirror-settings.xml` — mirrors vacíos para forzar a Maven a usar Central.
- `run-with-maven.cmd` — script helper para Windows que ejecuta el binario de Maven que instalaste en `C:\Softdesarrollo\apache-maven-3.9.x\apache-maven-3.9.0` (ajusta `MAVEN_BIN_PATH` si es necesario).

Uso (cmd.exe de Windows):

- Ejecución rápida usando tu Maven 3.9 instalado (ruta explícita):

```cmd
"C:\Softdesarrollo\apache-maven-3.9.x\apache-maven-3.9.0\bin\mvn" -s ".mvn/no-mirror-settings.xml" -f "%CD%\pom.xml" test
```

- O usa el script proporcionado (desde la raíz del proyecto):

```cmd
run-with-maven.cmd -f "%CD%\pom.xml" test
```

Notas:
- El archivo de configuración proporcionado solo desactiva los mirrors; tu CI corporativo aún debería usar la configuración corporativa.
- Si la ruta de instalación de Maven es diferente, edita `run-with-maven.cmd` y actualiza la variable `MAVEN_BIN_PATH`.

## Maven / Java (solo este proyecto)

Este repo incluye **Maven Wrapper** (`mvnw.cmd`) y está configurado para usar **JDK 21** en `C:\Program Files\Java\jdk-21.0.1` **solo para este proyecto** (sin tocar variables globales).

Ejecutar tests (Windows cmd.exe):

```cmd
cd C:\Users\JoaquínAntonioSiabra\Documents\GitHub\log-analyzer
mvnw.cmd -s ".mvn\no-mirror-settings.xml" test
```

Ver versión de Maven/Java:

```cmd
mvnw.cmd -v
```

## Windows: autoejecutable (incluye Java)

Este proyecto puede empaquetarse como una app de Windows que **incluye su propio runtime de Java** (no necesitas Java instalado en el PC destino).

Requisitos en la máquina de build:
- Windows
- **JDK** 16+ (recomendado 21) con `jpackage.exe` disponible
  - O bien configura `JAVA_HOME`, o bien asegúrate de que `jpackage.exe` está en el `PATH`.

Generación:
```cmd
cd /d C:\Users\JoaquínAntonioSiabra\Documents\GitHub\log-analyzer
bin\package-win.cmd
```

Salida:
- `target\dist\LogArgos\LogArgos.exe`

Distribución:
- Puedes comprimir la carpeta `target\dist\LogArgos` y copiarla a cualquier PC con Windows.

Notas:
- El empaquetado usa `jpackage --type app-image` para evitar dependencias extra (MSI/WiX).
- Si quieres un instalador MSI en el futuro, se puede añadir como paso opcional.