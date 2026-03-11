# Implementing the Version API

The launcher uses `GameApi` to fetch version metadata. A `Game` owns a default API via its
`apiFactory`; an `AccountProvider` can override or decorate that API per-account-type via
`getApiForGame(Game)`.

---

## Type hierarchy

```
GameApi (interface)
 ├── NullGameApi          — null-object singleton; safe empty returns, no network calls
 └── HttpGameApi          — abstract; HttpURLConnection, retry, DoS protection
       └── (your concrete subclass)
```

---

## Implementing HttpGameApi

Extend `HttpGameApi` and override `getAvailableVersions()` and `getVersion(String)`.
Use the protected helpers `fetchText(String)` and `fetchBytes(String)` for HTTP GET calls.

```java
public class MyVersionApi extends HttpGameApi {

    public MyVersionApi() {
        super("https://api.my-game.com");
    }

    @Override
    public List<Version> getAvailableVersions() {
        try {
            String json = fetchText("/v1/versions");
            return parseVersionList(json);
        } catch (IOException e) {
            // Return empty list on network failure — UI degrades gracefully
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Version> getVersion(String id) {
        try {
            String json = fetchText("/v1/versions/" + id);
            return Optional.of(parseVersion(new JsonParser().parse(json).getAsJsonObject()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private List<Version> parseVersionList(String json) {
        JsonArray arr = new JsonParser().parse(json)
                .getAsJsonObject().getAsJsonArray("versions");
        List<Version> result = new ArrayList<>();
        for (JsonElement el : arr) {
            result.add(parseVersion(el.getAsJsonObject()));
        }
        return Collections.unmodifiableList(result);
    }

    private Version parseVersion(JsonObject node) {
        VersionType type = new VersionType(
                node.get("type").getAsString(),
                node.get("typeName").getAsString());
        return new Version(
                node.get("id").getAsString(),
                type,
                node.get("releaseTime").getAsLong());
    }
}
```

### Built-in protections (HttpGameApi)

| Feature           | Behaviour                                                                      |
|-------------------|--------------------------------------------------------------------------------|
| Allowed schemes   | `http` and `https` only — `IllegalArgumentException` otherwise                 |
| Path traversal    | `..` and `.` segments rejected with `MalformedURLException`                    |
| Redirects         | Up to 5 hops; cross-host and non-http/s schemes rejected with `IOException`    |
| Response size cap | Throws `IOException` if response exceeds 10 MiB                                |
| Retry             | 3 attempts, exponential back-off (250 ms → 500 ms → 1 000 ms); 4xx not retried |
| User-Agent        | `ClassicLauncher/<version>` set automatically                                  |

---

## Wiring the API to a Game (recommended)

Set `apiFactory` on the `Game` builder so every account that resolves to this game gets the
right API automatically — no provider override needed:

```java
private static final Game MY_GAME = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.versionFilter("release", "Show release builds")
		.apiFactory(MyVersionApi::new)          // factory called each time createApi() is invoked
		.build();
```

The factory is called lazily by `Game.createApi()`.  `AccountProvider.getApiForGame(Game)` then
delegates to `game.createApi()` by default — no override required in the provider.

---

## Overriding the API per-provider (archival / alternative servers)

If one provider needs a *different* API for the same game (e.g. an archival server with more
versions), override `getApiForGame(Game)` in that provider:

```java
public class ArchivalProvider extends AccountProvider {

    @Override
    public List<Game> getGames() {
        return Collections.singletonList(MY_GAME);
    }

    @Override
    public GameApi getApiForGame(Game game) {
        if ("my_game".equals(game.getGameId())) {
            return new ArchivalVersionApi();   // returns older versions
        }
        return game.createApi();               // fall through for other games
    }

    // ...
}
```

`ProfileEditorDialog` calls `provider.getApiForGame(game)` so the selected account's provider
drives API selection:

1. If the provider overrides `getApiForGame` — its implementation is used.
2. Otherwise — `game.createApi()` is called (the game's configured `apiFactory`).
3. If no factory was set on the `Game` — `NullGameApi.INSTANCE` is returned (empty lists).

---

## Expected JSON schema

The `ProfileEditorDialog` filters the version list based on `VersionFilterOption` entries
defined by the `Game`. Match the `type.id` strings in your API response to the `typeId` values
you passed to `Game.builder(...).versionFilter(typeId, label)`.

A minimal response for a game with `"release"` and `"preview"` filters:

```json
{
  "versions": [
    { "id": "1.5.0",    "type": "release",  "typeName": "Release",  "releaseTime": 1710000000 },
    { "id": "1.5.0-rc", "type": "preview",  "typeName": "Preview",  "releaseTime": 1709900000 }
  ]
}
```

Built-in Minecraft-style defaults (used when the `Game` has no custom `versionFilter` entries):

| `type.id`     | Default checkbox label       |
|---------------|------------------------------|
| `snapshot`    | Enable experimental versions |
| `old_beta`    | Allow old Beta versions      |
| `old_alpha`   | Allow old Alpha versions     |
| anything else | Always shown                 |
