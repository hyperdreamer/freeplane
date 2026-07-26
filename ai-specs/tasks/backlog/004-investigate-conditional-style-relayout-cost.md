# Task: Investigate conditional style relayout cost
- **Task Identifier:** 2026-07-26-conditional-style
- **Scope:** Investigate the relayout cost added by
  `filter_any_text` conditional styles, measure where
  `getTransformedObjectNoThrow(...)` time is spent, and identify one or
  more safe change candidates or a justified no-fix result. Record the
  evidence and recommended next step as the task deliverable. Exclude
  product changes from this task.
- **Motivation:** The reported map pays a large relayout penalty from
  one text-based conditional style, but the obvious cache experiments
  did not help. Further work needs a separate evidence-backed
  investigation instead of speculative code changes.
- **Constraints:**
  - Do not ship product, test, build, config, runtime, or coupled
    documentation changes from this task.
  - If a candidate improvement needs a durable design or policy
    decision, stop and route that follow-up explicitly.
