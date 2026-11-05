var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Font = Java.type("java.awt.Font");
var TT = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Coll = Java.type("java.util.Collections");
var f = new Font("SansSerif", Font.PLAIN, 12);
function measureBody(body, wrap) {
  var source = wrap ? "\\text{" + body + "}" : body;
  var title = TT.of(Coll.singletonList(new TT.Math(source, body)));
  var m = new TM(); m.beginPass(1);
  var x = m.measure(title, f);
  return String(x.availability()) + " " + x.width().toFixed(2);
}
var samples = [
  "Theorem: $ON$ is well-ordered by $\\in$",
  "Remark: by K\\\"onig's Theorem",
  "$\\mathfrak{F}:=\\{\\mathscr{A}\\in\\mathcal{P}(\\mathcal{P}(X)):\\ \\mathscr{A}\\ \\text{is a filter}\\}$",
  "$\\beta=\\mathrm{type}(\\{1\\}\\times\\beta)=\\mathrm{type}(\\{1\\}\\times\\beta)$",
  "Remark: Every thing in our universe $V$ must be a set."
];
samples.forEach(function(b) {
  print("BODY   : " + b);
  print("  bare : " + measureBody(b, false));
  print("  \\text: " + measureBody(b, true));
});
