# Farsight dev setup

Shortens the "edit → build → play → log" loop from ~10 minutes to ~30 seconds.

## One-time setup (you do this once)

### 1. Launch the dev client with DevAuth

```bash
cd /d/Farsight
./gradlew runClient
```

Expected first-run behaviour:
1. Gradle downloads Minecraft assets (~200 MB, only the first time).
2. A local MC client window opens.
3. DevAuth prints something like `[DevAuth] Open https://microsoft.com/link in a browser and enter code XXXXXX` in the console — OR automatically opens a Microsoft OAuth browser tab.
4. Sign in with your MC-owning Microsoft account.
5. DevAuth caches the token under `~/.devauth/` (i.e. `C:\Users\<you>\.devauth\`). You won't be prompted again.
6. MC loads into the main menu as your real account.

Close the client after confirming login worked — the credentials are cached.

### 2. Generate Minecraft sources (optional but recommended)

```bash
./gradlew genSources
```

This produces a decompiled MC source jar under `.gradle/caches/fabric-loom/*-sources.jar`. Useful for verifying exact method/field names when writing mixins, avoiding descriptor-drift rebuilds.

## Day-to-day

Once DevAuth is cached, any subsequent `./gradlew runClient` launches MC directly without interaction — takes about 15 seconds from cold. I can drive it from my side now:

- Build + launch: `./gradlew runClient --no-daemon 2>&1 | tee /tmp/mc.log`
- Inspect log in real time, iterate on Farsight code, relaunch.

You can still ship the prod jar to Modrinth-installed clients with:
```bash
./gradlew build
# → build/libs/farsight-<version>.jar
```

## Troubleshooting

- **`runClient` asks for username/password on stdin** → DevAuth isn't on the runtime classpath. Confirm `me.djtheredstoner:DevAuth-fabric` appears in the mod list at launch (search the log for `devauth`).
- **Microsoft login loops forever** → delete `~/.devauth/` and run `runClient` again. Fresh auth flow.
- **Black screen / no world** → this is the vanilla dev env, no pre-existing worlds. Create a new one via the usual New World menu.
