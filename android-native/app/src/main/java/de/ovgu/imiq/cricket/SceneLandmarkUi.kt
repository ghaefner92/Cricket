package de.ovgu.imiq.cricket

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Historical copy is intentionally outside the transport habit question. */
@Composable internal fun SceneLandmarkDialog(model:CricketModel,mode:String,onDismiss:()->Unit) {
 val context=LocalContext.current
 val title=when(mode){"pt"->"Grüne Zitadelle";"bike"->"Kölner Dom";"car"->"Herkules · Bergpark Wilhelmshöhe";else->"Alexanderplatz"}
 val location=when(mode){
  "pt"->model.text("Magdeburg · Saxony-Anhalt, Germany","Magdeburg · Sachsen-Anhalt, Deutschland")
  "bike"->model.text("Cologne · North Rhine-Westphalia, Germany","Köln · Nordrhein-Westfalen, Deutschland")
  "car"->model.text("Kassel · Hesse, Germany","Kassel · Hessen, Deutschland")
  else->model.text("Berlin · Germany's capital","Berlin · Hauptstadt Deutschlands")
 }
 val story=when(mode){
  "pt"->model.text("This pink building was designed by Friedensreich Hundertwasser and completed in 2005. Its irregular forms and planted roofs bring architecture and nature together.","Dieses rosa Gebäude wurde von Friedensreich Hundertwasser entworfen und 2005 fertiggestellt. Unregelmäßige Formen und begrünte Dächer verbinden Architektur und Natur.")
  "bike"->model.text("The twin spires belong to Cologne's Gothic cathedral. Construction began in 1248 and was completed in 1880; it became a UNESCO World Heritage site in 1996.","Die beiden Türme gehören zum gotischen Kölner Dom. Der Bau begann 1248 und wurde 1880 vollendet; seit 1996 gehört er zum UNESCO-Welterbe.")
  "car"->model.text("The Herkules monument overlooks the historic cascades of Bergpark Wilhelmshöhe. The water features work through gravity, without pumps. This scene combines the park with a motorway as an artistic travel illustration.","Das Herkules-Denkmal überragt die historischen Kaskaden im Bergpark Wilhelmshöhe. Die Wasserspiele funktionieren durch Schwerkraft, ohne Pumpen. Die Szene verbindet den Park als künstlerische Reiseillustration mit einer Autobahn.")
  else->model.text("The scene shows Alexanderplatz with the TV Tower and Urania World Clock. Inaugurated in 1969, the clock became a distinctive meeting point in the square.","Die Szene zeigt den Alexanderplatz mit Fernsehturm und Urania-Weltzeituhr. Die 1969 eingeweihte Uhr wurde zu einem markanten Treffpunkt auf dem Platz.")
 }
 val url=when(mode){
  "pt"->"https://www.gruene-zitadelle.de/das-projekt"
  "bike"->"https://www.unesco.de/staette/koelner-dom/"
  "car"->"https://www.kassel.de/buerger/kunst_und_kultur/parks_und_gaerten/wilhelmshoehe/wasserspiele.php"
  else->"https://www.berlin.de/sehenswuerdigkeiten/3561749-3558930-weltzeituhr.html"
 }
 AlertDialog(onDismissRequest=onDismiss,icon={PixelIcon("info")},title={Heading(title)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(location,color=Gold,style=MaterialTheme.typography.bodySmall);Copy(story)}},confirmButton={TextButton(onDismiss){Text(model.text("Close","Schließen"))}},dismissButton={TextButton({runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure {model.error=model.text("Could not open the source","Quelle konnte nicht geöffnet werden")}}){Text(model.text("Learn more","Mehr erfahren"))}})
}
