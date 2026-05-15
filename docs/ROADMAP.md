# Smokeless Roadmap

> Smokeless is a substance-use ledger and cessation companion. The app is
> standalone-functional; the strategic arc is to join the **Bios ecosystem**
> as a producer of discrete substance-use events on the metric bus.

---

## Current State

**Capture surface:**
- Tap-to-log smoking events (single substance, currently tobacco)
- Tap-to-log cravings (discrete events, timestamp-only)
- Home-screen widget for one-tap logging
- Smoke-free interval countdown with breathing-orb animation
- Per-period statistics (today, week, month) and clean-streak tracking
- Cost-per-cigarette and money-saved derivations

**Data layer:**
- Room database, two entities ([SmokingSession.kt](../app/src/main/java/com/smokless/smokeless/data/entity/SmokingSession.kt),
  [Craving.kt](../app/src/main/java/com/smokless/smokeless/data/entity/Craving.kt))
- Each is timestamp + autogen id. No substance discriminator on the row
  today — the app is single-substance scoped to tobacco.
- CSV / JSON import path for migrating from other trackers

**UI:**
- Single Compose-flavored activity stack (`MainActivity`, `SettingsActivity`)
- Score adapter + chart data computed from session/craving streams

---

## Phase 1: Bios companion integration [PLANNED]

> Wire Smokeless into the Bios metric bus as a producer of substance-use
> events. This is the bridge that makes Smokeless useful beyond its own
> screen — Bios's cross-correlation engine can use tobacco-use and craving
> events to attribute RHR/HRV/SpO2/sleep deviations and surface the
> recovery trajectory during cessation.
>
> **Pairs with:** [Bios ROADMAP §7.7](../../Bios/docs/ROADMAP.md) and
> [Bios ECOSYSTEM_BOUNDARIES.md](../../Bios/docs/ECOSYSTEM_BOUNDARIES.md).

### 1.1 ContentProvider client for Bios writes

Add a thin `BiosClient` module that resolves the
`content://com.bios.app.health/companion/{metric_type}` URI and inserts
event values. Pattern mirrors the W2F → Bios writer.

- Detect Bios installation at runtime (package + signature check)
- Resolve the companion-write URI for `tobacco_use` and `tobacco_craving`
- On successful local insert into `smoking_sessions` / `cravings`, fire a
  parallel `ContentResolver.insert(uri, ContentValues)` with
  `value = 1.0` and `timestamp = event.timestamp`
- Best-effort, fire-and-forget: if Bios is absent or returns
  `SecurityException`, log locally and continue. Smokeless remains
  fully functional standalone.

**Acceptance:** With Bios installed and granted the companion permission,
every Smokeless log creates a matching reading in Bios. With Bios absent,
Smokeless behaves identically to today.

### 1.2 User-facing opt-in

Bios integration is **opt-out by default** — Smokeless does not push
anything to Bios until the user enables it in Settings. Mirrors how
Virgil treats outbound metric writes (see Virgil/docs/ECOSYSTEM.md).

- Settings → "Bios integration" toggle (default off)
- Status row: "Connected to Bios" / "Bios not installed" / "Not enabled"
- Tapping the row links to Bios's privacy dashboard if installed
- Off-switch wipes any cached Bios credentials immediately

### 1.3 Backfill writer

For users with existing Smokeless history who enable Bios integration
later, provide a one-shot backfill that walks the local session/craving
tables and pushes timestamps to Bios. Idempotency by `(metric_type,
timestamp)` is Bios's concern; Smokeless writes once per event.

**Acceptance:** Backfill of N events takes < 5s for N < 10,000 (typical
Smokeless history). Resumable if interrupted.

---

## Phase 2: Multi-substance schema [PLANNED]

> Today the `smoking_sessions` row has no substance field; the app is
> tobacco-scoped by convention. The roadmap to multi-substance support is
> driven by existing issues
> ([#3 marijuana](https://github.com/thdelmas/Smokeless/issues/3),
> [#6 alcohol](https://github.com/thdelmas/Smokeless/issues/6),
> [#7 opioid](https://github.com/thdelmas/Smokeless/issues/7),
> [#8 cocaine](https://github.com/thdelmas/Smokeless/issues/8),
> [#9 gambling](https://github.com/thdelmas/Smokeless/issues/9),
> [#10 pornography](https://github.com/thdelmas/Smokeless/issues/10),
> [#13 custom](https://github.com/thdelmas/Smokeless/issues/13)).

### 2.1 Substance enum on the entity

Add a `Substance` enum with `TOBACCO`, `CANNABIS`, `ALCOHOL`, ...,
`CUSTOM`. Migrate existing rows to `TOBACCO`. The substance field
becomes the partition key for all per-period statistics and UI.

### 2.2 Per-substance reasoning

The cessation framing (clean streak, money saved, life-back math) is
tobacco-specific in its current copy. Per-substance copy and per-substance
goals.

### 2.3 Bios key expansion (paired with Bios §7.7)

Once Smokeless emits cannabis events, the Bios companion-write URI
whitelists `cannabis_use` and `cannabis_craving`. Per the Bios YAGNI
rule, the keys exist as `MetricType` entries from day one but the URI
whitelist only opens when there's a real producer. Smokeless coordinates
with Bios on the URI-whitelist PR.

**Behavioral domains** (#9 gambling, #10 pornography) do not get Bios keys
in their current scope — they are not physiological signals and Bios is
silent about behavioral categories it can't measure from wearable data.
Smokeless still tracks them locally; they just don't cross the metric bus.

### 2.4 Caffeine + alcohol on the metric bus (hoist from W2F)

Audit finding: tobacco and cannabis are on the bus, but caffeine and
alcohol — the other two psychoactives — are not. W2F's `FuelLog`
already captures caffeine events locally (for the `fuel_gap` mood
signal); per the
[Bios "case study: nutrition in W2F" rule](../../Bios/docs/ECOSYSTEM_BOUNDARIES.md),
the second consumer must hoist the keys to the canonical bus rather
than keep parallel private tables.

Two consumers now exist:

1. Bios's cross-correlation engine (caffeine confounds HRV / sleep
   latency / RHR; alcohol confounds RHR / HRV / sleep architecture / skin
   temperature).
2. Smokeless's substance-use ledger surface (parity with tobacco /
   cannabis: history, widget, cessation trajectory).

**New keys** (paired with Bios `INTAKE` domain whitelist extension):

- `CAFFEINE_USE` — discrete caffeine consumption event. Timestamp +
  opaque event-id. No dose, no source.
- `CAFFEINE_CRAVING` — discrete craving event, same shape.
- `ALCOHOL_USE` — discrete alcohol consumption event.
- `ALCOHOL_CRAVING` — discrete craving event.

**Ownership.** Smokeless is the sole writer; the
`com.smokless.smokeless` package gets all four keys whitelisted on the
companion-write URI. W2F drops its caffeine write and reads from Bios
via the consumer API to derive `fuel_gap`. Tracks
[Smokeless #6 (alcohol)](https://github.com/thdelmas/Smokeless/issues/6).

**Substance enum extension.** `Substance` gains `CAFFEINE` and `ALCOHOL`
alongside the existing `TOBACCO` and `CANNABIS`. Per-substance
statistics, history, and widget already exist after Phase 2.1; this is
data-plane scope only.

**Caveat — cessation framing.** Caffeine and alcohol have different
cessation semantics than tobacco/cannabis (many users titrate rather
than abstain). Cessation copy is gated per-substance; defaulting to
neutral "log only" mode for the new pair until per-substance reasoning
copy ships.

---

## Phase 3: Cessation intelligence [PLANNED]

> Use Bios reads to surface the cessation recovery trajectory. The new
> `ConditionPattern` proposed in [Bios §7.7](../../Bios/docs/ROADMAP.md)
> (cessation recovery signal) is information that should also surface
> *inside* Smokeless's UI.

### 3.1 Bios read URI consumer

Read RHR, HRV, SpO2, and sleep-efficiency baselines from Bios via the
existing read URI (no new contract needed — same pattern W2F uses).
Surface a "since you stopped" panel during sustained abstinence:

- "RHR has trended down 4 bpm over 14 days" — data statement, no praise
- "HRV is back within your personal baseline" — data statement, no praise

**Content policy:** matches Bios's "never evaluate the person" rule
(Phase 3.6 in Bios ROADMAP). No "great job," no streak gamification.
Smokeless's own cessation UI can keep streak counters; the Bios-derived
panel is data-only.

### 3.2 Craving correlate surfaces

When craving rate spikes, check the Bios read URI for the most likely
correlate (sleep efficiency, HRV depression). Surface as a non-modal
hint: "Cravings have clustered today. Sleep efficiency was 78% last
night (your baseline: 86%)." Data, not advice.

---

## Non-negotiable principles

1. **Standalone-functional.** Bios integration is additive. Smokeless must
   work fully without Bios installed.
2. **Opt-in writes.** No data leaves Smokeless's local DB without an
   explicit user toggle.
3. **Local-first.** No cloud, no accounts, no telemetry. Same posture as
   the rest of the Bios ecosystem.
4. **Never evaluate the person.** Cessation framing inside Smokeless is
   allowed (streaks, money-saved); Bios-derived panels stay data-only.
5. **Timestamp-only on the metric bus.** No dose, brand, method, or
   location ever crosses the URI. The event is the signal.

---

## Cross-references

- [Bios ROADMAP §7.7](../../Bios/docs/ROADMAP.md) — substance-use companion plan
- [Bios ECOSYSTEM_BOUNDARIES.md](../../Bios/docs/ECOSYSTEM_BOUNDARIES.md) — Smokeless's row
- [Bios CONSUMER_API.md](../../Bios/docs/CONSUMER_API.md) — companion-write URI contract
- [docs/ECOSYSTEM.md](ECOSYSTEM.md) — Smokeless's metric-bus contract
