var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var bodies = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8"));
var CAP = 80;
var Character = Java.type("java.lang.Character");
function normalizeWhitespace(v) {
  var sb = "", pending = false;
  for (var i = 0; i < v.length; ) {
    var cp = v.codePointAt(i);
    if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { pending = sb.length > 0; }
    else { if (pending) { sb += " "; pending = false; } sb += new java.lang.String(Character.toChars(cp)); }
    i += Character.charCount(cp);
  }
  return sb;
}
function displayText(full) {
  var n = full.codePointCount(0, full.length);
  if (n <= CAP) return full;
  var end = full.offsetByCodePoints(0, CAP - 3);
  return full.substring(0, end) + "...";
}
var single = 0, multi = 0, plain = 0;
bodies.forEach(function(b) {
  var full = normalizeWhitespace(b);
  var disp = displayText(full);
  var t = TTP.parse(disp, false);
  var runs = t.runs();
  if (runs.size() === 1 && runs.get(0).getClass().getSimpleName() === "Math") single++;
  else if (runs.size() === 1) plain++;
  else multi++;
});
print("production-path (normalize + cap + inline parse): single-math=" + single + " multi=" + multi + " single-plain=" + plain + " failing=" + (multi + plain));
