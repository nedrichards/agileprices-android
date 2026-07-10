# Whole-Hour Hint Follow-Up

The current app keeps one cheapest-window source of truth and shows whole-hour hints
only as presentation around that window. This is intentionally light on logic while
the behaviour is tested in real use.

## Concern

The hint can make a minute-precise cheapest window look operationally wider than
the priced window. For example, a run that starts in `4h 32m` and ends in `8h 1m`
may display whole-hour hints as `(4h)` and `(9h)`. That is useful for simple
appliance timers, but it can imply that the rounded window is close enough to the
priced window.

The risk is not uniform. Adjacent Agile slots are often only a few percentage
points apart, so the approximation may be acceptable in practice. It can be wrong
when there is a sharp cliff between slots, especially around unusually cheap,
negative or very expensive half-hours. A naive "similar enough" check would need
to account for:

- Percentage difference from the exact cheapest window.
- Absolute p/kWh difference, because tiny or negative prices make percentages
  misleading.
- Whether the rounded start or end crosses into one or two materially different
  half-hour slots.
- Negative-price cases, where "more usage" can be cheaper rather than more
  expensive.
- Duration, because a 30-minute rounding difference matters differently for a
  30-minute run than for a long overnight run.
- User expectation: the app should not make a convenience timer hint look like a
  separately priced recommendation.

## Options

1. Leave the hint simple and keep watching real-world behaviour.
   This avoids clutter and avoids inventing a fragile similarity metric before
   there is evidence it is needed.

2. Only show whole-hour hints when the rounded envelope stays within the same
   priced half-hour slots as the exact window.
   This is simple, but it hides useful hints in cases where adjacent prices are
   effectively the same.

3. Price the rounded envelope and suppress the hint if it differs too much from
   the exact window.
   This is more defensible, but needs a carefully designed threshold that handles
   near-zero and negative prices without misleading results.

4. Show a qualitative caveat only when needed.
   For example, keep the compact hint normally, but replace it with exact-only
   guidance when the rounded envelope crosses a large price cliff.

5. Drop end rounding and only show a start-timer hint.
   This may better match simple appliance timers, but it loses useful guidance
   for timers configured as "finish by" or for users reasoning about the full
   run window.

## Current Decision

Do not add more logic yet. Keep the one-source-of-truth cheapest window, keep the
whole-hour hints as lightweight presentation and revisit after testing real Agile
price days in the app.
