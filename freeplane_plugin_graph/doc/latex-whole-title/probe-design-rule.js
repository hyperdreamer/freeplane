var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Font = Java.type("java.awt.Font");
var TT = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Coll = Java.type("java.util.Collections");
var f = new Font("SansSerif", Font.PLAIN, 12);
var bodies = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8"));
function hasTextOutsideMath(title) {
  var runs = title.runs();
  for (var i = 0; i < runs.size(); i++) {
    var r = runs.get(i);
    if (r.getClass().getSimpleName() === "Plain" && r.text().trim().length > 0) return true;
  }
  return false;
}
var rendered = 0, fits = 0, degraded = 0, wrapped = 0;
bodies.forEach(function(b) {
  var mixed = hasTextOutsideMath(TTP.parse(b, false));
  if (mixed) wrapped++;
  var src = mixed ? "\\text{" + b + "}" : b;
  var t = TT.of(Coll.singletonList(new TT.Math(src, b)));
  var m = new TM(); m.beginPass(1);
  var x = m.measure(t, f);
  if (String(x.availability()) === "RENDERED") { rendered++; if (x.width() <= 200) fits++; } else degraded++;
});
print("design rule (wrap only mixed bodies): rendered=" + rendered + " fits200=" + fits + " degraded=" + degraded + " wrapped=" + wrapped);
