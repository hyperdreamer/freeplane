var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Font = Java.type("java.awt.Font");
var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var raw = new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8");
var titles = JSON.parse(raw);
var f = new Font("SansSerif", Font.PLAIN, 12);
var rendered = 0, degraded = 0, fits = 0, overCap = 0;
titles.forEach(function(body) {
  var title = TTP.parse(body, false);
  var m = new TM(); m.beginPass(1);
  var measured = m.measure(title, f);
  if (String(measured.availability()) === "RENDERED") { rendered++; if (measured.width() <= 200) fits++; else overCap++; }
  else degraded++;
});
print("CURRENT INLINE PARSE on same 250 titles: rendered=" + rendered + " (fits 200px: " + fits + ", over: " + overCap + ")  degraded=" + degraded);
