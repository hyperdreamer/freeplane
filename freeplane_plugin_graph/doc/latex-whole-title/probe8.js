var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Font = Java.type("java.awt.Font");
var TT = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Coll = Java.type("java.util.Collections");
var f = new Font("SansSerif", Font.PLAIN, 12);
var titleList = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8"));
function stats(wrap) {
  var ok = 0, fits = 0, fail = 0;
  titleList.forEach(function(body) {
    var source = wrap ? "\\text{" + body + "}" : body;
    var title = TT.of(Coll.singletonList(new TT.Math(source, body)));
    var m = new TM(); m.beginPass(1);
    var x = m.measure(title, f);
    if (String(x.availability()) === "RENDERED") { ok++; if (x.width() <= 200) fits++; } else fail++;
  });
  return "rendered=" + ok + " fits200=" + fits + " degraded=" + fail;
}
print("bare body      : " + stats(false));
print("\\text{-wrapped  : " + stats(true));
