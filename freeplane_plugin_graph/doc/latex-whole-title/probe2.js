var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var TM  = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var TT  = Java.type("org.freeplane.plugin.graph.canvas.title.TitleTruncation");
var Metrics = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMetrics");
var Font = Java.type("java.awt.Font");
var FRC = Metrics.SCREEN_FRC;
var f = new Font("SansSerif", Font.PLAIN, 12);

function w(s) { return f.getStringBounds(s, FRC).getWidth(); }

[ "Theorem: $ON$ is well-ordered by $\\in$",
  "Theorem: $N$ is well-ordered by $\\in$",
  "Theorem: Equivalence of $\\leqslant$ and $\\subseteq$" ].forEach(function(text) {
  var title = TTP.parse(text, false);
  var m = new TM(); m.beginPass(1);
  var measured = m.measure(title, f);
  var src = title.plainSource();
  var deg = TT.degradedPlainText(title);
  print(text);
  print("  typeset width      = " + measured.width().toFixed(2) + "  (cap 200 -> overflow " + (measured.width()-200).toFixed(2) + ")");
  print("  plainSource width  = " + w(src).toFixed(2) + "  src='" + src + "'");
  print("  degraded(raw) width= " + w(deg).toFixed(2) + "  deg='" + deg + "'");
  print("  truncated(raw,200) = '" + TT.truncated(deg, f, 200.0) + "'");
  print("");
});
