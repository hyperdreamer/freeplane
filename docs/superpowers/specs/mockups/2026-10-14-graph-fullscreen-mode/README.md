# Mockup provenance — graph full-screen presentation mode (2026-10-14)

These HTML fragments are the exact screens presented to the user through the brainstorming
visual companion (browser) on 2026-10-14. They are content fragments: the companion server
wraps them in its frame template (theme CSS and selection helper). To re-render one, start the
companion server with `~/.pi/agent/skills/brainstorming/scripts/start-server.sh --project-dir <dir>`
and copy the file into the returned `screen_dir` as a new file name.

| File | Question | User choice |
| --- | --- | --- |
| `presentation-chrome.html` | What remains visible in presentation mode? | **A — Canvas only** (menu, toolbar, sidebar, settings, status all hidden) |
| `viewport-on-entry.html` | What does F11 do to zoom/position? | **A — Keep the exact view** (same center and zoom; exit returns precisely) |
| `exit-hint.html` | Show an exit hint on entry? | **A — Fade after ~3 s** (shown on every entry) |

Earlier clarifying answers (terminal): presentation semantics **B** (screen-fill *and* hide chrome),
controls **F11 toggle + Esc exit + View-menu check item**, no toolbar button.

Note on wording: the accepted option-A caption "exit returns precisely to the previous view" means
the view is preserved *through* the transition — including any pan/zoom performed while in
presentation mode — per the normative contract in the design (§3, §4.6).
