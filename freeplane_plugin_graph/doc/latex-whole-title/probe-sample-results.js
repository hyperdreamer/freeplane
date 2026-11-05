var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Font = Java.type("java.awt.Font");
var TT = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Coll = Java.type("java.util.Collections");
var f = new Font("SansSerif", Font.PLAIN, 12);
var bodies = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8"));
var lines = ["body\tbare_avail\tbare_width\twrapped_avail\twrapped_width\tinline_avail\tinline_width"];
function m(body, wrap) {
  var src = wrap ? "\\text{" + body + "}" : body;
  var t = TT.of(Coll.singletonList(new TT.Math(src, body)));
  var mm = new TM(); mm.beginPass(1);
  return mm.measure(t, f);
}
bodies.forEach(function(b) {
  var bare = m(b, false), wrapped = m(b, true);
  var im = new TM(); im.beginPass(1);
  var inl = im.measure(TTP.parse(b, false), f);
  lines.push([b.replace(/\t/g, " "), bare.availability(), bare.width().toFixed(2), wrapped.availability(), wrapped.width().toFixed(2), inl.availability(), inl.width().toFixed(2)].join("\t"));
});
Files.write(Paths.get("/tmp/sample-results.tsv"), lines.join("\n").getBytes("UTF-8"));
print("rows=" + lines.length);
var deg = lines.slice(1).filter(function(l){ return l.indexOf("\tDEGRADED\t") >= 0; });
print("degraded rows=" + deg.length);
deg.slice(0,6).forEach(function(l){ print("  " + l.substring(0, 100)); });
