[![CodeFactor](https://www.codefactor.io/repository/github/shyyyuki/regionbridge/badge)](https://www.codefactor.io/repository/github/shyyyuki/regionbridge)

# RegionBridge

A unified thread-safety API for **Spigot, Paper, Purpur, and Folia**.

## Requirements

| | Minimum |
|---|---|
| Spigot / Paper / Purpur | 1.20 |
| Folia | 1.20.1 |
| Java | 17 |

## Structure

```text
regionbridge/
├─ regionbridge-api      Interfaces: RegionExecutor, PlatformAdapter, TaskHandle, Platform
├─ regionbridge-core     RegionBridge facade, AdapterExecutor, Debug/Tracing, platform detection
├─ regionbridge-spigot   SpigotAdapter  -> BukkitScheduler (1 Main Thread)
├─ regionbridge-paper    PaperAdapter   -> Spigot compatibility + teleportAsync
├─ regionbridge-purpur   PurpurAdapter  -> extends Paper (Purpur does not change the threading model)
├─ regionbridge-folia    FoliaAdapter   -> Global/Region/Entity/AsyncScheduler
└─ regionbridge-plugin   Loader plugin (Shadow JAR containing everything) + /regionbridge
```

**Initialization flow:**

`RegionBridge.forPlugin(plugin)` -> `PlatformDetector` detects the platform (Folia > Purpur > Paper > Spigot) -> the appropriate adapter is loaded via Reflection.

Folia classes are never loaded on Spigot or Paper.

## Installation (Server)

1. Download `RegionBridge-1.0.0.jar` from the [Releases](../../releases) page.
2. Put it into your server's `plugins` folder.
3. Restart the server.

There is no configuration and there are no further dependencies. Every plugin that uses RegionBridge requires this JAR on the server.

## Building

```bash
./gradlew build
# Output: regionbridge-plugin/build/libs/RegionBridge-1.0.0.jar
```

## Adding RegionBridge as a Dependency (Developers)

RegionBridge is published through [JitPack](https://jitpack.io). Use `compileOnly` / `provided`: the classes are supplied at runtime by the RegionBridge plugin on the server, so they must not be shaded into your plugin.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.ShyyYuki.RegionBridge:regionbridge-core:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.ShyyYuki.RegionBridge:regionbridge-core:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.ShyyYuki.RegionBridge</groupId>
        <artifactId>regionbridge-core</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

`regionbridge-core` already pulls in `regionbridge-api` transitively. If you only need the interfaces, depend on `regionbridge-api` instead.

### Using the JAR directly

If you prefer not to use a repository, place the JAR in your project and reference it:

```kotlin
compileOnly(files("libs/RegionBridge-1.0.0.jar"))
```

### plugin.yml

Declare the dependency and mark your plugin as Folia compatible:

```yaml
depend: [RegionBridge]
folia-supported: true
```

## Using RegionBridge in Your Plugin

```java
public final class MyPlugin extends JavaPlugin {

    private RegionBridge bridge;

    @Override
    public void onEnable() {
        bridge = RegionBridge.forPlugin(this);

        bridge.entity(player).execute(() -> player.sendMessage("Hello!"));

        bridge.location(location).execute(
            () -> location.getBlock().setType(Material.STONE)
        );

        bridge.global().execute(
            () -> Bukkit.broadcastMessage("Global")
        );

        // Async -> Region
        bridge.async().execute(() -> {
            var data = loadFromDatabase();
            bridge.entity(player).execute(
                () -> player.sendMessage("Loaded: " + data)
            );
        });

        // Futures
        bridge.entity(player).supply(() -> player.getHealth())
            .thenAccept(hp -> getLogger().info("HP: " + hp));

        // Teleport (including cross-region teleports on Folia)
        bridge.teleport(player, target);
    }
}
```

`execute` runs inline if the current thread already owns the target context; `schedule` always schedules a new task.

If an entity is no longer available, the `retired` callback is triggered or the future completes exceptionally with an `EntityRetiredException`.

## Additional APIs

```java
// Chunks: async loading plus execution in the owning region
bridge.chunkAt(world, chunkX, chunkZ).run(chunk -> { /* safe access */ });

// Safe world operations across regions
SafeWorld safeWorld = bridge.world(world);
safeWorld.setBlock(location, Material.STONE);
safeWorld.fill(cornerA, cornerB, Material.AIR);
safeWorld.getNearbyPlayers(location, 50).thenAccept(players -> { });

// Cross-region flows
bridge.flow(bridge.entity(a), () -> a.getHealth())
      .then(bridge.entity(b), hp -> { b.setHealth(hp); return hp; })
      .thenRun(bridge.global(), hp -> Bukkit.broadcastMessage("Done"))
      .onError(Throwable::printStackTrace);

// Read from many entities in different regions
bridge.mapEntities(players, Player::getHealth);

// Per-chunk data and per-entity data
ChunkLocal<MyData> chunkData = bridge.chunkLocal(MyData::new).cleanOnUnload(this);
ConcurrentEntityMap<MyData> entityData = bridge.<MyData>entityMap().autoClean(this, 1200L);
```

`ChunkLocal` stores data per chunk, not per Folia region, because Folia regions merge and split dynamically and are not exposed by the API.

## Debugging

```text
/regionbridge debug off|warn|strict
```

- **warn**: Violations of `bridge.assertOwned(...)`, slow tasks (>50 ms), and blocking waits on tick threads are logged with stack traces.
- **strict**: Violations throw an `IllegalStateException`.
- Exceptions thrown by tasks include the stack trace of the task's scheduling location as a *suppressed exception*.
- `bridge.await(future, timeoutMs)` only blocks with a timeout and reports potential deadlock risks.

## Publishing (Maintainers)

1. Push the project to GitHub (keep `.idea/`, `.gradle/` and `build/` in `.gitignore`).
2. Make sure `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are committed.
3. Add a `jitpack.yml` to the repository root:

   ```yaml
   jdk:
     - openjdk17
   ```

4. Create a GitHub Release with a tag such as `1.0.0` and attach `RegionBridge-1.0.0.jar`.
5. Open `https://jitpack.io/#ShyyYuki/RegionBridge`, select the tag, and click **Get it** to trigger the first build.
