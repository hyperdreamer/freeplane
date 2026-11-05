var TTP = Java.type("org.freeplane.plugin.graph.projection.input.TitleTextParser");
var TM  = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var TT  = Java.type("org.freeplane.plugin.graph.canvas.title.TitleTruncation");
var Metrics = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMetrics");
var Font = Java.type("java.awt.Font");

var texts = [
  "Theorem: $ON$ is well-ordered by $\\in$",
  "Theorem: $N$ is well-ordered by $\\in$",
  "Theorem: $\\bigcup\\alpha\\leqslant\\alpha$",
  "Theorem: $\\leqslant$",
  "Theorem: $\\bigcup\\alpha\\leslant\\alpha$"
];

var fonts = [ new Font("Dialog", Font.PLAIN, 12), new Font("SansSerif", Font.PLAIN, 12) ];

fonts.forEach(function(f) {
  print("=== font " + f.getFamily() + " " + f.getSize2D());
  texts.forEach(function(text) {
    var title = TTP.parse(text, false);
    var runs = title.runs();
    var desc = [];
    for (var i = 0; i < runs.size(); i++) {
      var r = runs.get(i);
      desc.push(r.getClass().getSimpleName() + "(" + (r.getClass().getSimpleName() === "Math" ? r.source() : r.text()) + ")");
    }
    var m = new TM();
    m.beginPass(1);
    var measured = m.measure(title, f);
    var icons = m.iconSource();
    print("  " + text);
    print("    runs=" + desc.join(" | "));
    print("    availability=" + measured.availability() + " width=" + measured.width().toFixed(2)
        + " height=" + measured.height().toFixed(2));
    print("    builds=" + icons.buildCount() + " failures=" + icons.failureCount()
        + " degradedText='" + TT.degradedPlainText(title) + "'");
    print("    fits vertical200=" + (measured.width() <= 200) + " horizontal130=" + (measured.width() <= 130)
        + " diagonal150=" + (measured.width() <= 150));
  });
});
