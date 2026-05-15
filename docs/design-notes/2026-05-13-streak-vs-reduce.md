# Streak-resets vs reduce-don't-quit: the retention bug

**Date:** 2026-05-13
**Status:** Design observation — feeds an open issue.

---

## The tension

Smokeless's stated philosophy is **reduce, don't quit** (repo tagline: *"You don't want to quit it? Then reduce it."*).

The app's main feedback mechanism is the **clean streak** (current + best), reset to `00:00:00` the moment a smoke is logged ([UI-UX.md §7.1](../audits/UI-UX.md), line 505 / 515).

These contradict each other:

- **The philosophy** says: progress is gradient. Smoking less = winning.
- **The mechanic** says: progress is binary. Any smoke = back to zero.

A user who is reducing (e.g. went from 20/day → 8/day → 3/day) gets the same `00:00:00` slap as someone who chained two packs. The data they actually need — **the downward trend** — is buried under the streak-reset narrative.

## Why this drives drop-out

Drop-out hypothesis (matches stated observation that "people drop out from using the app too frequently"):

1. User installs Smokeless with reduce intent.
2. First smoke happens (statistically inevitable — cold turkey collapses on Day 1 ~80% of the time).
3. Clean-streak hard-resets. Visual + emotional signal: *you failed.*
4. User feels shame → associates the app with shame → opens it less → eventually uninstalls.
5. Counter-intuitively, the more honest the user is about logging smokes, the more punished they feel.

This is a **structural retention bug**, not a polish bug. The existing UI-UX audit (`docs/audits/UI-UX.md`) identifies retention concerns but frames them as **onboarding** and **accessibility** issues; it doesn't surface the streak/philosophy mismatch as a root cause.

## What the philosophy actually wants surfaced

If reduce-don't-quit is the real thesis, the dominant feedback signal should track **reduction**, not abstinence:

- **Smokes per day, 7-day rolling average** — slope = progress, regardless of zero days.
- **Reduction velocity** — "you're smoking 40% less than 30 days ago" (data, not praise).
- **Best week** instead of "best streak" — captures the trough of a reducing trend.
- **Cravings resisted ratio** — already logged (`Craving.kt`); ratio of cravings-to-smokes is the cleanest reduce signal in the existing data.

Clean-streak isn't wrong, but it should be **a secondary metric for users who choose to track it**, not the hero card. The hero card should reward the philosophy the app preaches.

## Constraints (carry over from existing principles)

From [ROADMAP.md §"Non-negotiable principles"](../ROADMAP.md):

- **Never evaluate the person.** Reduction metrics are data, not praise. No "great job," no shame.
- **Local-first.** No telemetry to validate the retention hypothesis externally — observation is anecdotal + drop-off rate visible only via Play Store stats.
- **Standalone-functional.** Any new metric must compute from local data (smoke + craving timestamps).

## Proposed direction (not committed)

Three options, in order of invasiveness:

1. **Demote streak, promote trend.** Keep the streak card but move it below a new "Reduction" hero showing 7-day rolling smokes/day + velocity. Cheapest fix. No data migration.
2. **Trend-as-hero.** Replace the streak hero entirely with a reduction-velocity card. Streak survives as a stat card lower in the scroll. Bigger philosophical commitment.
3. **User-selectable mode.** Onboarding asks: "Are you trying to quit, or to reduce?" Quit-mode = streak hero, reduce-mode = trend hero. Most flexible but adds onboarding burden.

(1) probably ships fastest and aligns the hero card with the tagline immediately. (3) is the right long-arc answer once onboarding gets built (currently 4.0/10 per audit).

## Related work

- [audits/UI-UX.md §7](../audits/UI-UX.md) — feedback & microinteractions (where the streak-reset animation lives)
- [audits/UI-UX.md §6](../audits/UI-UX.md) — onboarding gap (precondition for option 3)
- [ROADMAP.md Phase 3.1](../ROADMAP.md) — "data statements, no praise" framing (consistent with reduction-as-data)
