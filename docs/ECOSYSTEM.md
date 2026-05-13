# Smokeless Ecosystem

How Smokeless relates to the Bios suite (Bios, W2F, Virgil, Fil,
SoulRadio). Smokeless is a **companion** in the Bios sense: it owns a
specialized capture surface (substance-use and craving event logging)
that Bios cannot reasonably own, and it pushes computed events back to
Bios's metric bus for cross-correlation.

This document mirrors the structure of
[W2F/docs/ECOSYSTEM.md](../../W2F/docs/ECOSYSTEM.md) and
[Virgil/docs/ECOSYSTEM.md](../../Virgil/docs/ECOSYSTEM.md).

---

## Smokeless's domain

**Owns:**
- Tap-to-log capture surface for use and craving events
- Per-substance ledger (currently tobacco; cannabis + others on roadmap)
- Cessation UX: streak counters, smoke-free interval, money-saved math,
  breathing-orb during cravings
- Home-screen widget for one-tap logging

**Reads from Bios:** (Phase 3, future)
- Resting HR, HRV, SpO2, sleep efficiency, skin temperature baselines
- Used to surface the cessation recovery trajectory in Smokeless's own UI

**Writes to Bios:** (Phase 1, planned)
- `tobacco_use` — discrete tobacco consumption event, timestamp-only
- `tobacco_craving` — discrete craving event, timestamp-only
- `cannabis_use` — reserved, ships when Smokeless adds the cannabis substance type
- `cannabis_craving` — reserved, ships with cannabis

All writes are opt-in (Settings toggle, default off).

---

## What Smokeless deliberately does not own

- **Physiological metrics.** Smokeless never reads sensors directly. RHR,
  HRV, sleep, etc. come from Bios via the read URI when Smokeless needs
  them to surface a recovery panel.
- **Mood/bipolar inference.** W2F's territory. Substance use is *correlated*
  with mood drift but Smokeless does not attempt to infer mood.
- **Behavioral addictions on the metric bus.** Issues #9 (gambling) and
  #10 (pornography) may be supported locally for tracking but do not get
  Bios metric keys — those are not wearable-derivable physiological
  signals (see Bios ECOSYSTEM_BOUNDARIES.md rule).
- **Clinical advice.** Smokeless can show "you have not used in 14 days"
  and the Bios-derived data trajectory. It cannot say "you should keep
  going" or "take supplement X." Information only.

---

## Reserved metric keys (paired with Bios §7.7)

Canonical keys to be added to Bios's
[`MetricType`](../../Bios/android/app/src/main/java/com/bios/app/model/Enums.kt)
enum. New `MetricDomain.INTAKE` and `MetricUnit.EVENT` are introduced for
this purpose (event-shaped: value is always `1.0`, the timestamp is the
signal).

| Key | Status | Description |
|---|---|---|
| `tobacco_use` | Initial — ships with Phase 1 | Discrete tobacco-consumption event. Timestamp + opaque event-id only. |
| `tobacco_craving` | Initial — ships with Phase 1 | Discrete craving event. Same shape. |
| `cannabis_use` | Reserved — ships with Phase 2 | Discrete cannabis-consumption event. Form (joint/vape/edible) is Smokeless-local; Bios sees the event only. |
| `cannabis_craving` | Reserved — ships with Phase 2 | Discrete craving event. Same shape. |

### What is NEVER on the metric bus

- Brand, product, vendor identifiers
- Dose, quantity, concentration
- Location, GPS, place name
- Method (cigarette / vape / joint / edible) — form lives in Smokeless's
  local DB only
- Cost or money-saved math
- Cessation streak counters or goal state
- Photographs, notes, or free-text annotations

The metric bus carries the event existence and its timestamp. That is
sufficient for Bios's cross-correlation engine. Everything else is
Smokeless-private.

---

## Read URI consumption (Phase 3)

Smokeless reads from Bios via the standard
[`BiosHealthProvider`](../../Bios/docs/CONSUMER_API.md) read URI. No new
contract needed — same pattern W2F uses to read sleep/HRV.

**What Smokeless reads:**
- 14-day rolling baselines for `resting_heart_rate`, `heart_rate_variability`,
  `blood_oxygen`, `sleep_duration`, `sleep_efficiency`, `skin_temperature`
- Today's deviations from those baselines

**Why:**
- During sustained abstinence (>72h), surface the recovery trajectory
  (RHR ↓, HRV ↑) as data statements, not praise
- During craving spikes, surface the most-likely correlate (typically
  sleep deficit) as a non-modal hint, not advice

---

## Write contract

```
URI:     content://com.bios.app.health/companion/{metric_type}
Method:  ContentResolver.insert(uri, values)
Values:
  value     (Double, required)   always 1.0 for event-shaped keys
  timestamp (Long, optional)     epoch ms, defaults to now
```

Returns: row URI on success, throws `SecurityException` if:
- Smokeless lacks the companion permission (signature-perm)
- The `metric_type` is not whitelisted
- Bios is not installed

Smokeless's `BiosClient` swallows all three failures silently — local
logging continues. Status surfaces in Settings, not as a notification.

---

## Cross-references

- [Bios ROADMAP §7.7](../../Bios/docs/ROADMAP.md) — substance-use companion plan
- [Bios ECOSYSTEM_BOUNDARIES.md](../../Bios/docs/ECOSYSTEM_BOUNDARIES.md) — companion table
- [Bios CONSUMER_API.md](../../Bios/docs/CONSUMER_API.md) — full URI contract
- [W2F/docs/ECOSYSTEM.md](../../W2F/docs/ECOSYSTEM.md) — sibling companion (mental health)
- [Virgil/docs/ECOSYSTEM.md](../../Virgil/docs/ECOSYSTEM.md) — sibling companion (safety)
- [ROADMAP.md](ROADMAP.md) — Smokeless's own phases
