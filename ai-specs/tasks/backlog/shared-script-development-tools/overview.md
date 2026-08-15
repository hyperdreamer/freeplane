Assuming one developer familiar with Freeplane, including tests and documentation but excluding decision delays:

| Task | Complexity | Rough effort | Main reason |
|---|---|---:|---|
| **001 – Precise Groovy compile locations** | Medium | 2–5 days | Requires structured extraction across Groovy diagnostic types and consistent compile/run tests. |
| **002 – Asynchronous diagnostics** | Very high | 3–6+ weeks | Cross-thread context propagation, output routing, callback attribution, class-loader retention, cache identity, and two delivery front ends. |
| **003 – Throwaway evaluation** | Medium | 3–6 days | Mostly reuse of existing compile/run infrastructure, with careful isolation from persistent code state and permissions. |
| **004 – Approved existing-script invocation** | High, low confidence | 1–3 weeks | Approval scope, script identity/hash invalidation, authorization, audit behavior, and invocation semantics remain undecided. |
| **005 – Continuation after settling** | Very high, low confidence | 2–5+ weeks | EDT orchestration is feasible, but “settled” has no defined universal condition; cancellation, timeout, and two-phase lifecycle are substantial. |
| **006 – Visible UI image capture** | High | 1–2 weeks | Swing capture is straightforward, but component resolution, overlays/popups, image limits, and equivalent LangChain/MCP image transport are not. |

Likely order from least to most difficult: **001 → 003 → 006 → 004 → 005 → 002**.