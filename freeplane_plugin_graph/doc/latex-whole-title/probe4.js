var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Font = Java.type("java.awt.Font");
var TT = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Coll = Java.type("java.util.Collections");
var raw = new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8");
var titles = JSON.parse(raw);
var f = new Font("SansSerif", Font.PLAIN, 12);
var rendered = 0, degraded = 0, overCap = 0, fits = 0;
var failures = [];
var over = [];
titles.forEach(function(body) {
  var run = new TT.Math(body, "\\latex " + body);
  var title = TT.of(Coll.singletonList(run));
  var m = new TM(); m.beginPass(1);
  var measured = m.measure(title, f);
  if (String(measured.availability()) === "RENDERED") {
    rendered++;
    if (measured.width() <= 200) fits++; else { overCap++; if (over.length < 6) over.push(measured.width().toFixed(0) + "px  " + body.substring(0, 60)); }
  } else {
    degraded++; if (failures.length < 8) failures.push(body.substring(0, 60));
  }
});
print("sample=" + titles.length + " rendered=" + rendered + " degraded=" + degraded);
print("rendered within 200px=" + fits + "  rendered over 200px=" + overCap);
print("--- engine failures (sample):"); failures.forEach(function(x){ print("   " + x); });
print("--- rendered but wider than 200px (sample):"); over.forEach(function(x){ print("   " + x); });
