var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var bodies = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8"));
var single = 0, plain = 0, multi = 0;
bodies.forEach(function(b) {
  var t = TTP.parse(b, false);
  var runs = t.runs();
  if (runs.size() === 1 && runs.get(0).getClass().getSimpleName() === "Math") single++;
  else if (runs.size() === 1) plain++;
  else multi++;
});
print("today's inline parse of the 250 stripped bodies: single-math-run=" + single + " single-plain=" + plain + " multi-run=" + multi);
var dollar = bodies.filter(function(b){ return b.indexOf("$") >= 0; }).length;
print("bodies containing $: " + dollar + " ; without $: " + (bodies.length - dollar));
