# Engineering Philosophy

Principles for how we work on the Vale compiler, distilled across many sessions. Descriptive, not prescriptive — the architect's actual call overrides; this captures patterns so a reader can predict the answer or recognize when they're about to violate one.

## Engineering tendencies to watch

- **Watch for ripple-aversion bias.** The instinct to minimize edit surface ("don't cascade `&mut self`", "don't refactor 9 callers for a sig change") is a real bias, and it pulls against correctness. When ripple-aversion conflicts with the right answer, **surface the choice to the architect** — don't pick the lower-ripple option quietly.

- **Idiomatic Rust > ceremonial Rust.** Plain `HashMap` + `&mut self` is preferred over `Cell<HashMap>` + take/set ceremony. Compile-time borrow checking is preferred over runtime checking. Interior mutability is a slicer/migration bias toward "preserve `&self`" that doesn't match how good Rust gets written.

- **Embed-by-value for small Copy types.** Value types should embed by value in containing enums, not be wrapped in `&'v`. The "minimize edit surface" temptation to leave some variants `&'v` because the active test doesn't exercise them is a real anti-pattern. Only genuinely heap-allocated payloads stay `&'v`; everything Copy-able embeds.

- **Don't avoid hard work for short-term ease.** Given a choice between G1 (easy, partial fix) and G2 (harder, more correct), pick G2. Corners cut now leave debt that's harder to clean up later than doing it right the first time.

- **Push back when something looks off.** Questions like "why is there a `Cell` here?" or "why are we disabling all of Guardian on this?" are the way to surface design issues someone has rationalized over. Give the honest answer — including "I picked the smaller diff to minimize ripple" if that's the truth. Then accept the redirect.

## Meta-rules

- **Catch tendencies and codify them.** When a recurring bias is surfaced (ripple-aversion, no-simplifications, position-correctness, etc.), add a new ≤25-word rule to the relevant guide so future readers (or future sessions of the same person) don't repeat it. Watch for moments where the conversation surfaces a principle worth recording.

- **Brevity in rule additions; longer is fine when asked.** Default to ≤25-word rules; ask before adding anything longer. Sections explicitly approved as longer (background, roadmap, philosophy) are the exception.
