#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
RUN=/tmp/fp-latex-whole-title-acceptance
DISPLAY_NUM=98
DISPLAY=":${DISPLAY_NUM}"
APP_HOME="$RUN/home"
CONFIG_HOME="$RUN/config"
BIN_DIR="$ROOT/BIN"
JAR="$BIN_DIR/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar"
MAP=/home/henry/Documents/999.notebooks/01.math/00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm
WORKSPACE="$RUN/acceptance-empty.fpg"
EVIDENCE_DIR="$ROOT/freeplane_plugin_graph/doc/latex-whole-title"
PNG="$EVIDENCE_DIR/acceptance-graph-workspace.png"
XWD="$RUN/acceptance-graph.xwd"
xvfb_pid=
app_pid=

cleanup() {
    if [ -n "$app_pid" ]; then
        child_pids=$(pgrep -P "$app_pid" || true)
        for pid in $child_pids "$app_pid"; do
            if [ -r "/proc/$pid/environ" ] && tr '\0' '\n' < "/proc/$pid/environ" \
                    | grep -Fqx 'FP_LATEX_ACCEPTANCE=20261105-graph-latex-whole-title-prefix'; then
                kill -TERM "$pid" 2>/dev/null || true
            fi
        done
    fi
    if [ -n "$xvfb_pid" ] && ps -p "$xvfb_pid" -o args= \
            | grep -Fqx "Xvfb $DISPLAY -screen 0 1600x1000x24 -nolisten tcp"; then
        kill -TERM "$xvfb_pid" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p BIN
mkdir -p "$APP_HOME" "$CONFIG_HOME" "$EVIDENCE_DIR"
gradle dist
sha256sum "$JAR"
git rev-parse HEAD

if [ -e "/tmp/.X${DISPLAY_NUM}-lock" ]; then
    printf 'Display %s is already locked\n' "$DISPLAY"
    exit 1
fi
Xvfb "$DISPLAY" -screen 0 1600x1000x24 -nolisten tcp > "$RUN/xvfb.log" 2>&1 &
xvfb_pid=$!
ready=0
for attempt in $(seq 1 50); do
    if DISPLAY="$DISPLAY" xdotool getdisplaygeometry >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 0.1
done
test "$ready" -eq 1

cd "$BIN_DIR"
env FP_LATEX_ACCEPTANCE=20261105-graph-latex-whole-title-prefix \
    DISPLAY="$DISPLAY" HOME="$APP_HOME" XDG_CONFIG_HOME="$CONFIG_HOME" \
    JAVA_HOME="$JAVA_HOME" PATH="$PATH" JAVA_OPTS='-Dorg.freeplane.nosplash=true' \
    ./freeplane.sh > "$RUN/freeplane.log" 2>&1 &
app_pid=$!
main_window=
for attempt in $(seq 1 120); do
    main_window=$(DISPLAY="$DISPLAY" xdotool search --onlyvisible --name 'Freeplane' 2>/dev/null | head -n 1)
    if [ -n "$main_window" ]; then
        break
    fi
    sleep 1
done
test -n "$main_window"

# Open the approved map through the Freeplane File menu.
DISPLAY="$DISPLAY" xdotool mousemove 182 113 click 1
sleep 1
DISPLAY="$DISPLAY" xdotool mousemove 265 352 click 1
sleep 2
DISPLAY="$DISPLAY" xdotool mousemove 1180 578 click 1
DISPLAY="$DISPLAY" xdotool type --clearmodifiers --delay 1 "$MAP"
DISPLAY="$DISPLAY" xdotool mousemove 1353 663 click 1
sleep 8

# Open a new isolated workspace, then add the already-open map.
DISPLAY="$DISPLAY" xdotool mousemove 322 113 click 1
sleep 0.5
DISPLAY="$DISPLAY" xdotool mousemove 401 143 click 1
sleep 4
DISPLAY="$DISPLAY" xdotool mousemove 830 553 click 1
DISPLAY="$DISPLAY" xdotool type --clearmodifiers --delay 1 "$WORKSPACE"
DISPLAY="$DISPLAY" xdotool mousemove 980 636 click 1
sleep 8
DISPLAY="$DISPLAY" xdotool mousemove 69 717 click 1
sleep 2
DISPLAY="$DISPLAY" xdotool mousemove 820 553 click 1
DISPLAY="$DISPLAY" xdotool type --clearmodifiers --delay 1 "$MAP"
DISPLAY="$DISPLAY" xdotool mousemove 978 636 click 1
sleep 12

graph_window=$(DISPLAY="$DISPLAY" xdotool search --onlyvisible --name '^Graph Workspace$' | head -n 1)
test -n "$graph_window"
test "$(DISPLAY="$DISPLAY" xdotool getwindowname "$graph_window")" = 'Graph Workspace'
DISPLAY="$DISPLAY" xdotool windowsize "$graph_window" 1600 1000
sleep 2
DISPLAY="$DISPLAY" xdotool mousemove 800 46 click 1
sleep 2
DISPLAY="$DISPLAY" xwd -id "$graph_window" -out "$XWD"
ffmpeg -y -loglevel error -i "$XWD" -frames:v 1 -update 1 "$PNG"
sha256sum "$JAR" "$MAP" "$PNG"
