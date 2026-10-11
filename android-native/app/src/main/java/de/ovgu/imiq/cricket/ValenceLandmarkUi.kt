package de.ovgu.imiq.cricket

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal fun valenceLandmarkTitle(mode:String)=when(mode){"walk"->"Elbauenpark · Jahrtausendturm";"bike"->"Wissenschaftshafen · Magdeburg";"pt"->"OVGU · Universitätsbibliothek";else->"Quedlinburg · Marktplatz"}
internal fun valenceLandmarkSource(mode:String)=when(mode){
 "walk"->"https://www.elbauenpark.de/de/attraktion/jahrtausendturm/"
 "bike"->"https://wissenschaft.magdeburg.de/Wissensstadt/Wissenschaftshafen/"
 "pt"->"https://ub.ovgu.de/ub/en/Contact.html"
 else->"https://www.quedlinburg-info.de/en/sights/town-hall.html"
}

@Composable internal fun ValenceLandmarkDialog(mode:String,de:Boolean,onDismiss:()->Unit) {
 val context=LocalContext.current
 val story=when(mode){
  "walk"->if(de)"Der Jahrtausendturm wurde 1999 für die Bundesgartenschau errichtet. Eine spiralförmige Außenrampe führt zur Aussichtsplattform. Im Inneren erzählt eine Ausstellung von 6.000 Jahren Wissenschaft und Technik."else"Built for the 1999 garden exhibition, the Jahrtausendturm has a spiral exterior ramp leading to its viewing platform. Inside, an exhibition explores 6,000 years of science and technology."
  "bike"->if(de)"Der ehemalige Handelshafen wird zu einem Quartier für Forschung und Wissenschaft. Historische Speicher, Hafenkräne und die Denkfabrik verbinden die industrielle Geschichte mit dem heutigen Wissenschaftshafen."else"The former trading harbor is becoming a district for research and science. Historic warehouses, harbor cranes and the Denkfabrik connect its industrial history with today's Wissenschaftshafen."
  "pt"->if(de)"Die Universitätsbibliothek der Otto-von-Guericke-Universität liegt am Universitätsplatz 2, Gebäude 30. Das 2003 eröffnete Haus ist an seiner Glasfassade, den gelben Paneelen und dem weit auskragenden Dach erkennbar."else"The Otto von Guericke University Library is at Universitätsplatz 2, building 30. Opened in 2003, its glass facade, yellow panels and broad overhanging roof make it distinctive."
  else->if(de)"Der Marktplatz liegt im Herzen der historischen Altstadt. Fachwerkhäuser, das efeubewachsene Rathaus und die Türme der Marktkirche prägen den Platz. Quedlinburgs Altstadt gehört zum UNESCO-Welterbe."else"The market square is at the heart of the historic old town. Half-timbered houses, the ivy-covered town hall and the market church towers frame the square. Quedlinburg's old town is a UNESCO World Heritage site."
 }
 AlertDialog(onDismissRequest=onDismiss,icon={PixelIcon("info")},title={Heading(valenceLandmarkTitle(mode))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(if(mode=="car")"Quedlinburg · Sachsen-Anhalt"else"Magdeburg · Sachsen-Anhalt",color=Gold,style=MaterialTheme.typography.bodySmall)
  Copy(story)
  Text(if(de)"Künstlerische Reiseszene nach fotografischen Referenzen."else"Artistic travel scene based on photographic references.",color=MutedText,style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(onDismiss){Text(if(de)"Schließen"else"Close")}},dismissButton={TextButton({runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(valenceLandmarkSource(mode))))}}){Text(if(de)"Mehr erfahren"else"Learn more")}})
}
