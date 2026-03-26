# Sword Throw

Sword Throw is a Fabric mod that adds throwable sword gameplay to Minecraft.

## Requirements

- Java 21
- Gradle (or use the included wrapper)
- Minecraft + Fabric Loader versions configured in `gradle.properties`

## Development

Run the client in development mode:

```bash
./gradlew runClient
```

On Windows PowerShell:

```powershell
.\gradlew.bat runClient
```

Build the mod jar:

```bash
./gradlew build
```

Built artifacts are output to `build/libs/`.

## Project Structure

- `src/main/java` - shared mod code
- `src/client/java` - client-only code
- `src/main/resources` - mod metadata and assets

## License

See `LICENSE` for license terms.
