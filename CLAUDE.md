# Protectyons

Voxel-change protection enforcement mod for a modular Minecraft project (Minecraft Java Edition). Part of a suite of intercommunicating mods that expose public APIs so other mods — the user's own and third parties' — can integrate.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/protectyons-context), not part of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's cloned as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/protectyons-context.git context
```

Before searching source for architecture, ownership boundaries, API shape, or "why does this work this way," check `context/` first. It's maintained specifically to answer those questions cheaply:

- `context/design-document.md` — the authoritative design spec. Start here for anything about intended shape or scope.
- `context/decisions.md` — dated log of decisions made during implementation that extend or override the design document, with rationale. Check this for anything that looks like it contradicts design-document.md — the doc should already reflect the current decision, but this explains why.
- `context/classes/` — one short markdown file per implemented class: public surface, key state, collaborators. Read the relevant file here before opening the actual source, and before editing a class update its file to match.

**Keep this system current as you work:**
- When a design decision is made that conflicts with or is absent from design-document.md, update design-document.md directly and add a dated entry to decisions.md explaining the change.
- When a class is added or its public surface changes, add or update its file in `context/classes/`.
- Don't let source and these docs drift — treat updating them as part of finishing the change, not optional cleanup.
- `context/` has its own git history, independent of this repo's commits. Commit and push changes there separately (`git -C context add . && git -C context commit -m "..." && git -C context push`) — editing the files alone doesn't back them up.

## Status

Scaffolded 2026-09-26 (see [context/decisions.md](context/decisions.md)):
- Loader: **NeoForge**
- Minecraft version: **26.1.2**
- Java: **JDK 25** (standalone Eclipse Temurin, JAVA_HOME set) — same toolchain as the rest of the suite
- Group ID: `com.github.cerealklla.protectyons` / Mod ID: `protectyons`
- **Cartographyr is a REQUIRED dependency** (`compileOnly`, `type="required"` in `neoforge.mods.toml`, mirroring Settlemynts' own pattern) — this mod's entire purpose is enforcing Cartographyr's `ProtectionLevel` field, so there's no meaningful standalone mode.
- Project structure copied from Settlemynts' MDK setup (`build.gradle`, `settings.gradle`, wrapper).

**Milestone 1: voxel-change enforcement implemented 2026-09-26** (see [context/decisions.md](context/decisions.md)) — the mod's entire v1 scope, per the user's own spec:
- `enforcement.ProtectedRange` — pure, unit-tested geometry: given a `ProtectionLevel` and a column's surface height, decides whether a specific block Y is inside that level's protected range. `NoVoxelChangeFullHeight` protects every Y unconditionally; `NoVoxelChangeAlongSurfaceAndUp` protects from `(surface − 5)` up (leaving natural caves/underground mining below that band untouched, per the user's explicit spec). Any other `ProtectionLevel` (including `Unprotected`, or an unrecognized third-party one like a future Religyons `consecrated_ground`) is never protected by this class — enforcing those isn't Protectyons' job.
- `enforcement.ProtectionExemptBlocks` — trees/leaves/saplings/crops/flowers/snow/anything vanilla's own `BlockTags.REPLACEABLE` covers (tall grass, ferns, dead bush, vines, etc.) are always exempt from protection, regardless of `ProtectionLevel` — matches the user's explicit "trees, leaves, plants, snow, seedlings, crops etc can still be destroyed/placed" instruction. Only solid-block creation/destruction is ever actually blocked. **Not unit-tested** — needs a live `BlockState`/tag registry, same limitation as other live-state checks elsewhere in the suite.
- `enforcement.VoxelProtectionListener` — the actual enforcement: cancels `BreakBlockEvent` (block-break attempts) and `BlockEvent.EntityPlaceEvent` filtered to `Player` (block-place attempts) whenever the target position falls inside a `LifecycleState.REALIZED` Cartographyr entity's protected range, using `Cartography.getEntitiesAt`/`Heightmap.Types.WORLD_SURFACE`. Only `REALIZED` entities are considered — a `PLANNED` settlement has nothing built yet to protect, and a `RETIRED`/`ABANDONED`/`DESTROYED` one is no longer active (this mod is itself a concrete instance of the "position-query consumers must filter lifecycle state themselves" principle flagged in Cartographyr's own decisions.md, 2026-09-26).
- **Deliberately narrow v1 scope**: only player-driven break/place is covered. Explosions, fire spread, fluid flow, sculk spread, and other non-player world changes are **not** addressed — flagged as a real, easy-to-extend gap, not silently dropped.
- `./gradlew build`/`test` green (5 new `ProtectedRangeTest` cases). Boot-smoke-test and live in-game confirmation still pending.

Next: boot-smoke-test the full four-mod (now five-mod) suite via `sync-mods.sh`, then a live `runClient` pass — place a settlement (Settlemynts), confirm digging/building inside its footprint is blocked at/above the surface band but caving underneath it still works, and confirm chopping trees/breaking crops/plants inside the same footprint is never blocked.

See [context/classes/](context/classes/) for per-class reference.
