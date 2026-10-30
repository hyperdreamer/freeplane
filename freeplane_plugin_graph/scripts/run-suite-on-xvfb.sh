#!/usr/bin/env bash
# Runs the graph module test suite on a dedicated virtual X display.
#
# Why: the focus-dependent window tests need to activate their own frames. On a live desktop they
# fail with "Canvas did not regain focus within 5000 ms" whenever another application holds focus,
# which has nothing to do with the code under test. A private Xvfb display plus a minimal window
# manager gives them a predictable environment, and is what this repository's acceptance documents
# already use.
#
# Usage:
#   freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh
#   freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh -PTestLoggingFull
#
# Any arguments are passed through to the Gradle test invocation.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"

for tool in xvfb-run Xvfb openbox; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "run-suite-on-xvfb: '$tool' is required but was not found on PATH." >&2
        echo "Install Xvfb and openbox, or run 'gradle :freeplane_plugin_graph:test' on a quiet desktop." >&2
        exit 2
    fi
done

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
# A headless flag inherited from the caller would silently turn the window tests into skips.
unset JAVA_TOOL_OPTIONS

exec xvfb-run -a -s "-screen 0 1920x1080x24" -- \
    bash -c 'openbox >/dev/null 2>&1 & sleep 3; exec gradle :freeplane_plugin_graph:test --console=plain "$@"' \
    -- "$@"
