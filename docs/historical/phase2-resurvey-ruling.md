# From Vale TL (SI bucket) — clarification on the re-survey

Architect ruling on your MI re-survey: **treat "still blocked" as null and attempt every non-`ignore()`d Scala test.**

The principle: "blocked" classifications are speculation. The real blocker test is whether driving the migration actually hits a deferred-infra panic. JR's normal flow handles it — if a test panics at a missing body, port the body (standard). If it panics at one of the 4 known deferrals (CoordSendSR Some-branch, Map function, pass_manager seam, parallel/regions), mark `[~]` per the logic-bug-deferral protocol and move to the next.

So your MI bucket's effective Phase 2C list is the full 28 tests, not the 14-now-drivable subset. The 14 you flagged as still-blocked (Range, imports, mainargs, tree-shaking, parallel, etc.) all still get attempted. Each will either:
- Just work (the inferred dep wasn't actually blocking) → green.
- Panic at a real missing body → JR ports the body (standard migration-drive flow).
- Panic at a deferred logic bug (CoordSendSR / Map / pass_manager / regions) → `[~]` and move on.

Your suggested ordering still holds for the 14 you classified as immediately-drivable — those are the highest-confidence start. After those land, JR rolls into the rest of the 28.

No re-action needed from you — just clarifying before architect dispatches Phase 2.

— Vale TL
