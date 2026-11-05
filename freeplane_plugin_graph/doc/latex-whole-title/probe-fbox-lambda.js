var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var TF = Java.type("org.scilab.forge.jlatexmath.TeXFormula");
var TC = Java.type("org.scilab.forge.jlatexmath.TeXConstants");
var bodies = JSON.parse(new java.lang.String(Files.readAllBytes(Paths.get("/tmp/titles.json")), "UTF-8"));
var bad = [];
bodies.forEach(function(b) {
  try { var f = new TF(b); }
  catch (e) { bad.push("CONSTRUCT " + b.substring(0, 80) + " || " + String(e).substring(0, 110)); }
});
print("bodies rejected at construction: " + bad.length);
bad.forEach(function(x){ print("  " + x); });
["\\fbox{\\lambda}", "\\Bigg\\{\\fbox{\\lambda^\\theta}\\Bigg\\}", "\\xi: = \\sup\\Bigg\\{\\fbox{\\lambda^\\theta}: \\theta\\in K\\Bigg\\}"].forEach(function(b) {
  try { var f = new TF(b); var icon = f.createTeXIcon(TC.STYLE_TEXT, 12); print("OK   size=(" + icon.getIconWidth() + "x" + icon.getIconHeight() + ")  " + b); }
  catch (e) { print("FAIL " + b + " || " + String(e).substring(0, 110)); }
});
