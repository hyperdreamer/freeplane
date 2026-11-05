var Font = Java.type("java.awt.Font");
var TT = Java.type("org.freeplane.plugin.graph.projection.input.TitleText");
var TM = Java.type("org.freeplane.plugin.graph.canvas.title.TitleMeasurer");
var Coll = Java.type("java.util.Collections");
var f = new Font("SansSerif", Font.PLAIN, 12);
["\\latex   $x_2 = 3$", "$x_2 = 3$"].forEach(function(body) {
  var title = TT.of(Coll.singletonList(new TT.Math(body, body)));
  var m = new TM(); m.beginPass(1);
  var measured = m.measure(title, f);
  print("body='" + body + "' availability=" + measured.availability() + " width=" + measured.width().toFixed(2));
});
