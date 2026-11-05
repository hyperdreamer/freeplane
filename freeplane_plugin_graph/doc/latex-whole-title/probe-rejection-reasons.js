// Records, for every sampled body, whether TeXFormula constructs it and how tall its icon is,
// so a DEGRADED measurement is attributed to a real cause instead of being inferred.
// Run: jjs -J-Djava.awt.headless=true -cp <plugin jar>:<jlatexmath jar> probe-rejection-reasons.js
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var TF = Java.type("org.scilab.forge.jlatexmath.TeXFormula");
var TC = Java.type("org.scilab.forge.jlatexmath.TeXConstants");
var bodies = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("titles-sample.json")), "UTF-8"));
var lines = ["body\tconstruction\ticon_width\ticon_height"];
bodies.forEach(function (b) {
  try {
    var formula = new TF(b);
    var icon = formula.createTeXIcon(TC.STYLE_TEXT, 12);
    lines.push([b.replace(/\t/g, " "), "OK", icon.getIconWidth(), icon.getIconHeight()].join("\t"));
  } catch (e) {
    lines.push([b.replace(/\t/g, " "), "REJECTED: " + String(e).replace(/\s+/g, " ").substring(0, 140), "", ""].join("\t"));
  }
});
Files.write(Paths.get("rejection-reasons.tsv"), lines.join("\n").getBytes("UTF-8"));
print("rows=" + lines.length);
var tall = lines.slice(1).filter(function (l) { return /OK\t[0-9]+\t(4[0-9]|[5-9][0-9])/.test(l); });
print("icons 40px or taller=" + tall.length);
tall.slice(0, 4).forEach(function (l) { print("  " + l.substring(0, 90)); });
