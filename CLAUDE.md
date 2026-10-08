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
- **Scope**: break/place (any entity), explosion block destruction, and Enderman block theft (pickup + place-back) are covered. Fire spread, fluid flow, sculk spread, and other world changes outside those categories are **not** addressed — flagged as a real, easy-to-extend gap, not silently dropped.
- `./gradlew build`/`test` green (5 new `ProtectedRangeTest` cases). Boot-smoke-test and live in-game confirmation still pending.

**First live playtest, same day** (see [context/decisions.md](context/decisions.md)) — real bug found and fixed: tilling dirt/grass inside a protected settlement was incorrectly blocked. `BlockEvent.EntityPlaceEvent` turns out to also fire for in-place tool transformations (hoe-till, axe-strip/scrape/wax-off, shovel-path), not just genuine new placements — confirmed via temporary diagnostic logging after the user correctly pushed back that tilling doesn't create or destroy a solid block. Fixed generically via `VoxelProtectionListener#isInPlaceToolTransformation`, which checks the position's pre-change state (`event.getBlockSnapshot().getState()`) rather than enumerating tool abilities by name. `./gradlew build`/`test` green.

**Second live playtest, 2026-09-27** (see [context/decisions.md](context/decisions.md)) — two more real bugs found and fixed: standing under a tree, the surface-band protection was measured from the top of the tree (leaves/trunk), not the real ground beneath it (`VoxelProtectionListener#findSolidSurfaceY` now walks down from the heightmap value skipping every exempt block, stopping at the first genuinely solid one); sweet berry bushes weren't exempt at all, since the original exemption list only checked narrow tags (`SAPLINGS`/`CROPS`/`FLOWERS`) that don't cover every vanilla plant. Reworked `ProtectionExemptBlocks` to check `instanceof VegetationBlock` (a real vanilla common superclass covering essentially every non-solid plant block at once) instead of chasing individual tags. **A real open question surfaced, not yet resolved**: how ground-level protection should work once an actual multi-story building exists on a plot, where the roof might sit many blocks above the real entrance — the current surface-band model doesn't obviously generalize to that case; revisit once Blueprynts-built structures make it concrete. `./gradlew build`/`test` green.

**Explosion protection added 2026-09-28** (see [context/decisions.md](context/decisions.md)) — real bug report from the live production server: a creeper destroyed protected blocks inside a settlement, which was v1's already-documented explosion gap, not a regression. `VoxelProtectionListener` now also subscribes to `ExplosionEvent.Detonate`, filtering `event.getAffectedBlocks()` through the same `ProtectionExemptBlocks`/`isProtectedAt` checks the break/place handlers already use. `./gradlew build`/`test` green; live re-confirmation (trigger another explosion against a settlement) still pending.

**Enderman block theft blocked, 2026-09-28** (see [context/decisions.md](context/decisions.md)) — user-requested, not a bug report. Place-back now covered exactly (`onEntityPlace`'s `Player`-only filter was dropped — this event was never actually player-specific in vanilla/NeoForge). Pickup has no cancelable event at all in vanilla, so a new `onMobGriefing` handler denies `EntityMobGriefingEvent` for any Enderman standing in a protected zone at the time — an approximation (based on the Enderman's own position, not its randomized ~2-block-away target), chosen over reactively restoring a stolen block after the fact (which would leave a real tick where it's genuinely gone). `./gradlew build`/`test` green; live re-confirmation still pending.

Next: live re-confirmation of the explosion fix, the Enderman fix, and both earlier rounds of fixes, plus the original test plan — place a settlement (Settlemynts), confirm digging/building inside its footprint is blocked at/above the surface band but caving underneath it still works, confirm chopping trees/breaking crops/plants inside the same footprint is never blocked, confirm a creeper/TNT explosion against the settlement now spares protected solid blocks, and confirm an Enderman can't steal or place blocks while inside the settlement.

**Player-permission bypass added, 2026-09-29** (see [context/decisions.md](context/decisions.md), [context/classes/ProtectedAreaRegistry.md](context/classes/ProtectedAreaRegistry.md)) — found via a Blueprynts live bug report (a plot's own Mayor couldn't relocate a Building Supply Box inside their own plot, silently reverted by this mod's own enforcement) and a cross-mod design discussion confirming Cartographyr should stay permission-free. New `permission.ProtectedAreaRegistry` (a separate, `SavedData`-backed registry of area-polygon -> permitted-player-set, keyed by whatever id the registering mod already uses) plus public `api.Protectyons` facade (`registerProtectedArea`/`updatePermittedPlayers`/`unregisterProtectedArea`). `VoxelProtectionListener.isProtectedAt` now checks it first, exempting a permitted player entirely regardless of `ProtectionLevel`. Settlemynts is the first real caller (its own Plots, permitted = Mayor + Town Planners). `./gradlew build` green, **confirmed live, 2026-09-29** — the reported relocation now succeeds.

See [context/classes/](context/classes/) for per-class reference.
