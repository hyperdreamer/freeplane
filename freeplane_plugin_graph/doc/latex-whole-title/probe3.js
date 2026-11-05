var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var TT  = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TM  = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Font = Java.type("java.awt.Font");
var Collections = Java.type("java.util.Collections");
var f = new Font("SansSerif", Font.PLAIN, 12);

var bodies = [
  "Theorem: $ON$ is well-ordered by $\\in$",
  "Theorem: $N$ is well-ordered by $\\in$",
  "Theorem: $\\bigcup\\alpha\\leqslant\\alpha$"
];

bodies.forEach(function(body) {
  // whole-title math, as D2 says for a \latex-prefixed node
  var mathRun = new TT.Math(body, "\\latex " + body);
  var title = TT.of(Collections.singletonList(mathRun));
  var m = new TM(); m.beginPass(1);
  var measured = m.measure(title, f);
  print("WHOLE-TITLE MATH body='" + body + "'");
  print("  availability=" + measured.availability() + " width=" + measured.width().toFixed(2));
  var icons = m.iconSource();
  print("  builds=" + icons.buildCount() + " failures=" + icons.failureCount());
  print("");
});
