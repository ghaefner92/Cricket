package de.ovgu.imiq.cricket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CricketApp(model:CricketModel,onExport:(JSONObject,String)->Unit,onImport:()->Unit,onLocate:()->Unit,onRetryMigration:()->Unit) {
 val revision=model.revision
 val settings=model.settings()
 CricketTheme(settings.optString("textSize")=="large") {
  Scaffold(containerColor=Ink,topBar={TopAppBar(title={Text("CRICKET",color=Gold,style=MaterialTheme.typography.titleMedium)},actions={TextButton({model.setLanguage(if(model.de)"en"else"de")}){Text(if(model.de)"EN"else"DE")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Ink))},bottomBar={
   if(model.ready&&model.passport()!=null&&model.store.json("cricket.passport-renewal.v1")==null)NavigationBar(containerColor=Panel){
    listOf("home","journey","passport","settings").forEach { key->NavigationBarItem(selected=model.tab==key,onClick={model.tab=key;model.error=null},icon={PixelIcon(when(key){"journey"->"walk";"passport"->"star";"settings"->"clock";else->"sun"})},label={Text(when(key){"home"->model.text("Home","Start");"journey"->model.text("Travel","Reise");"passport"->"Passport";else->model.text("Menu","Menü")},style=MaterialTheme.typography.bodySmall)},alwaysShowLabel=true) }
   }
  }) { padding->
   Column(Modifier.fillMaxSize().padding(padding)) {
    if(model.error!=null)PixelCard(Modifier.padding(horizontal=12.dp)){Copy(model.error!!,MaterialTheme.colorScheme.error);TextButton({model.error=null}){Text(model.text("Dismiss","Schließen"))}}
    if(model.notice!=null)Row(Modifier.fillMaxWidth().background(Panel).padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text(model.notice!!,Modifier.weight(1f));TextButton({model.notice=null}){Text("×")}}
    when {
     !model.ready->Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){CompanionSprite("robot",animated=true);Heading(model.text("Bringing your companion with you…","Dein Begleiter zieht mit um…"));if(model.migrationError==null)CircularProgressIndicator()else{Copy(model.migrationError!!);PixelButton(model.text("Retry import","Import wiederholen"),onRetryMigration)}}
     model.passport()==null||model.store.json("cricket.passport-renewal.v1")!=null->Onboarding(model)
     model.tab=="home"->HomeScreen(model)
     model.tab=="journey"->JourneyScreen(model,onLocate)
     model.tab=="passport"->PassportScreen(model)
     else->SettingsScreen(model,onExport,onImport)
    }
   }
  }
  model.pendingImport?.let { backup->AlertDialog(onDismissRequest={model.pendingImport=null},title={Heading(model.text("Import backup?","Sicherung importieren?"))},text={Copy(model.text("Your current Passport will be archived. Journeys are merged; conflicting IDs are rejected.","Dein aktueller Passport wird archiviert. Reisen werden zusammengeführt; widersprüchliche IDs werden abgelehnt."))},confirmButton={TextButton({runCatching {model.store.importBackup(backup);model.refresh();model.tab="home";model.notice=model.text("Backup imported","Sicherung importiert")}.onFailure { model.error=it.message };model.pendingImport=null}){Text(model.text("Import","Importieren"))}},dismissButton={TextButton({model.pendingImport=null}){Text(model.text("Cancel","Abbrechen"))}}) }
 }
 BackHandler(enabled=model.ready&&(model.tab!="home"||model.routeView!="search"&&model.tab=="journey")){if(model.tab=="journey"&&model.routeView!="search")model.routeView=if(model.routeView=="map"||model.routeView=="reflection")"alternatives"else"search" else model.tab="home"}
}

@Composable fun ScreenColumn(content:@Composable ColumnScope.()->Unit) {
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(14.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable fun HomeScreen(model:CricketModel) {
 val companion=model.companion();val source=model.passport()?.optJSONObject("sourceSnapshot");val points=source?.optJSONObject("plan")?.optJSONObject("points")?:model.passport()?.optJSONObject("goalPoints")
 ScreenColumn {
  PixelCard {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){CompanionSprite(companion.optString("appearance","robot"),animated=model.settings().optString("motion")=="full");Column(Modifier.weight(1f)){Heading(companion.optString("name"));Copy(model.text("Where shall we go today?","Wohin geht es heute?"))}};Copy(model.text("Your passport is ready. Let’s find a way that fits you.","Dein Passport ist bereit. Finden wir einen Weg, der zu dir passt."));PixelButton(model.text("Where are we going? →","Wohin geht es? →"),{model.tab="journey"},Modifier.fillMaxWidth(),primary=true)}
  PixelCard {Heading(model.text("Your priorities","Deine Prioritäten"));Copy(source?.optJSONObject("plan")?.optString("weekStart")?:model.passport()?.optString("weekStart").orEmpty(),Edge);goalKeys.filter { (points?.optInt(it)?:0)>0 }.sortedByDescending { points?.optInt(it) }.forEach { key->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){GoalIcon(key,Modifier.size(30.dp));Text(goalCopy(key,model.de).getString("title"),Modifier.weight(1f));Text("${points?.optInt(key)}",color=Gold)}};PixelButton(model.text("View my Passport","Mein Passport"),{model.tab="passport"})}
 }
}

@Composable fun Onboarding(model:CricketModel) {
 val renewing=model.store.json("cricket.passport-renewal.v1")!=null
 Column {
  if(renewing)Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){Text(model.text("New Passport draft","Neuer Passport-Entwurf"),Modifier.weight(1f));TextButton({model.cancelRenewal()}){Text(model.text("Cancel","Abbrechen"))}}
  when(model.step){"identity"->IdentityScreen(model,false);"goals"->GoalsScreen(model);"valences"->FeelingsScreen(model);"beliefs"->BeliefsScreen(model);else->PassportScreen(model,pending=true)}
 }
}
@Composable fun IdentityScreen(model:CricketModel,editing:Boolean) {
 val companion=model.companion();var name by remember { mutableStateOf(companion.optString("name")) };var appearance by remember { mutableStateOf(companion.optString("appearance","robot")) };val available=model.availability();val animated=model.settings().optString("motion")=="full"
 ScreenColumn {
  Heading(model.text("Meet your companion","Dein Begleiter"))
  PixelCard {CompanionSprite(appearance,Modifier.size(120.dp).align(Alignment.CenterHorizontally),animated=animated);OutlinedTextField(name,{name=it.take(24)},label={Text(model.text("Name","Name"))},modifier=Modifier.fillMaxWidth(),singleLine=true);listOf("robot","explorer","creature","naturalist").chunked(2).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach { kind->PixelButton(kind.replaceFirstChar { it.uppercase() },{appearance=kind},Modifier.weight(1f),primary=appearance==kind)}}}}
  if(!editing)PixelCard {Heading(model.text("Your travel kit","Deine Verkehrsmittel"));Copy(model.text("Which transport modes can you use?","Welche Verkehrsmittel kannst du nutzen?"));modes.forEach { mode->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){PixelIcon(mode);Text(modeName(mode,model.de))};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(true,false).forEach { value->PixelButton(if(value)model.text("Yes","Ja")else model.text("No","Nein"),{model.saveAvailability(available.copyJson().put(mode,value))},Modifier.weight(1f),primary=available.opt(mode)==value)}}}}}
  PixelButton(if(editing)model.text("Save companion","Begleiter speichern")else model.text("Next: weekly goals →","Weiter: Wochenziele →"),{model.saveCompanion(name,appearance);if(!editing)model.goStep("goals")else model.notice=model.text("Companion saved","Begleiter gespeichert")},Modifier.fillMaxWidth(),enabled=name.trim().isNotEmpty()&&(editing||modes.all { available.opt(it) is Boolean }&&modes.any { available.optBoolean(it) }),primary=true)
 }
}
@Composable fun GoalsScreen(model:CricketModel) {
 var index by remember { mutableIntStateOf(0) };var all by remember { mutableStateOf(false) };val key=goalKeys[index];val plan=model.plan();val points=plan.getJSONObject("points");val total=goalKeys.sumOf { points.optInt(it) }
 Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Heading(model.text("Your weekly mission","Deine Wochenmission"))
  PixelCard {Row {Text(model.text("Points left","Punkte übrig"),Modifier.weight(1f));Text("${10-total}",color=Gold)};LinearProgressIndicator(progress={total/10f},modifier=Modifier.fillMaxWidth());Copy(model.text("Share 10 points. Equal points mean equal priority.","Verteile 10 Punkte. Gleiche Punkte bedeuten gleiche Priorität."))}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PixelButton(model.text("← Back","← Zurück"),{model.goStep("identity")},Modifier.weight(1f));PixelButton(if(all)model.text("Goal card","Zielkarte")else model.text("All goals","Alle Ziele"),{all=!all},Modifier.weight(1f))}
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   if(all)items(goalKeys){goal->PixelCard {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){GoalIcon(goal);Text(goalCopy(goal,model.de).getString("title"),Modifier.weight(1f));Text("${points.optInt(goal)}",color=Gold)};PixelButton(model.text("Adjust","Anpassen"),{index=goalKeys.indexOf(goal);all=false})}}
   else item {PixelCard {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){PixelButton("←",{index--},enabled=index>0);Text("${index+1}/${goalKeys.size}");PixelButton("→",{index++},enabled=index<goalKeys.lastIndex)};GoalScene(model.companion().optString("appearance","robot"),key,model.settings().optString("motion")=="full");Heading(goalCopy(key,model.de).getString("title"));Copy(goalCopy(key,model.de).getString("hint"));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){PixelButton("−",{model.adjustGoal(key,-1)},enabled=points.optInt(key)>0);Text("${points.optInt(key)}/10",color=Gold);PixelButton("+",{model.adjustGoal(key,1)},enabled=total<10,primary=true)}}}
  }
  PixelButton(model.text("Next: transport feelings →","Weiter: Mobilitätsgefühle →"),{model.saveGoals()},Modifier.fillMaxWidth(),enabled=total==10,primary=true)
 }
}
@Composable fun FeelingsScreen(model:CricketModel) {
 var index by remember { mutableIntStateOf(model.store.json("imiq.experimental.valences-draft.v1")?.optInt("index")?:0) };val mode=modes[index];val answers=model.valences();val rating=if(answers.isNull(mode))null else answers.getInt(mode)
 ScreenColumn {
  TextButton({if(index>0)index-- else model.goStep("goals")}){Text(model.text("← Back","← Zurück"))};Heading(model.text("How does travelling feel?","Wie fühlt sich Mobilität an?"));Copy("${index+1}/4")
  PixelCard {Heading(modeName(mode,model.de));TravelScene(model.companion().optString("appearance","robot"),mode,model.settings().optString("motion")=="full",rating);Copy(model.text("Think about your usual journeys. How does this mode feel to you?","Denke an deine üblichen Wege. Wie fühlt sich dieses Verkehrsmittel an?"));RatingControl(rating?.plus(4),{model.rateFeeling(mode,it-4,index)},model.text("Very unpleasant","Sehr unangenehm"),model.text("Very pleasant","Sehr angenehm"));Copy(if(rating==null)model.text("Choose your feeling","Wähle dein Gefühl")else when {rating<0->model.text("Unpleasant","Unangenehm");rating>0->model.text("Pleasant","Angenehm");else->model.text("Neutral","Neutral")});PixelButton(model.text("Choose neutral","Neutral wählen"),{model.rateFeeling(mode,0,index)})}
  PixelButton(if(index<3)model.text("Next mode →","Nächster Modus →")else model.text("Next: connections →","Weiter: Verbindungen →"),{if(index<3)index++else model.saveFeelings()},Modifier.fillMaxWidth(),enabled=rating!=null&&(index<3||modes.all { !answers.isNull(it) }),primary=true)
 }
}
@Composable fun RatingControl(value:Int?,onChange:(Int)->Unit,low:String,high:String) {
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){PixelButton("−",{onChange(((value?:4)-1).coerceAtLeast(1))},enabled=value!=1);Slider((value?:4).toFloat(),{onChange(it.toInt())},Modifier.weight(1f),valueRange=1f..7f,steps=5);PixelButton("+",{onChange(((value?:4)+1).coerceAtMost(7))},enabled=value!=7)}
 Row {Text(low,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);Text(high,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}
}
@Composable fun BeliefsScreen(model:CricketModel) {
 LaunchedEffect(Unit){if(model.beliefStart==null)model.prepareBeliefs()}
 val question=model.beliefStart?.objects("questions")?.getOrNull(model.beliefIndex)
 ScreenColumn {
  TextButton({if(model.beliefIndex>0){model.beliefIndex--;model.persistBeliefs()}else model.goStep("valences")},enabled=!model.busy){Text(model.text("← Back","← Zurück"))};Heading(model.text("Connect travel with your goals","Verbinde Reisen mit deinen Zielen"))
  if(model.busy){CircularProgressIndicator();Copy(model.text("Preparing your connections…","Verbindungen werden vorbereitet…"))}
  if(question!=null){val cell=question.getString("cell_id");val value=if(model.beliefAnswers.isNull(cell))null else model.beliefAnswers.optInt(cell);val mode=question.getString("hotco_mode");val need=question.getString("model_need")
   PixelCard {Copy("${model.beliefIndex+1}/4");Heading(modeName(mode,model.de));TravelScene(model.companion().optString("appearance","robot"),mode,model.settings().optString("motion")=="full",background="citadel");Heading(needName(need,model.de));Copy(model.text("In your usual journeys, does this mode help or hinder this goal?","Hilft oder erschwert dieses Verkehrsmittel dieses Ziel auf deinen üblichen Wegen?"));RatingControl(value,{model.rateBelief(cell,it)},model.text("Hinders a lot","Erschwert stark"),model.text("Helps a lot","Hilft stark"));val labels=catalog().getJSONObject("beliefs").getJSONObject(model.locale).getJSONArray("labels");Copy(if(value==null)model.text("Choose your connection","Wähle deine Verbindung")else labels.getString(value-1));PixelButton(model.text("Choose neutral","Neutral wählen"),{model.rateBelief(cell,4)})}
   PixelButton(if(model.beliefIndex<3)model.text("Next connection →","Nächste Verbindung →")else model.text("Create Passport →","Passport erstellen →"),{model.beliefNext()},Modifier.fillMaxWidth(),enabled=!model.busy&&value!=null,primary=true)
  }else if(!model.busy)PixelButton(model.text("Retry","Erneut versuchen"),{model.prepareBeliefs(true)})
 }
}

@Composable fun PassportScreen(model:CricketModel,pending:Boolean=false) {
 var section by remember { mutableStateOf("overview") };val item=model.passport();val response=if(pending)model.pendingPassport else item?.optJSONObject("response");val passport=response?.optJSONObject("cognitive_passport");val profile=passport?.optJSONObject("profile")
 ScreenColumn {
  Heading(model.text("Your cognitive Passport","Dein kognitiver Passport"))
  if(pending&&passport==null){Copy(model.text("Your goals, feelings and four direct connections are ready.","Deine Ziele, Gefühle und vier direkten Verbindungen sind bereit."));PixelButton(model.text("Create Passport","Passport erstellen"),{model.createPassport()},enabled=!model.busy,primary=true);if(model.busy)CircularProgressIndicator()}
  if(passport!=null){
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("overview","feelings","beliefs").forEach { key->PixelButton(when(key){"overview"->model.text("Goals","Ziele");"feelings"->model.text("Feelings","Gefühle");else->model.text("Beliefs","Verbindungen")},{section=key},Modifier.weight(1f),primary=section==key)}}
   PixelCard {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CompanionSprite(model.companion().optString("appearance","robot"),animated=model.settings().optString("motion")=="full");Column(Modifier.weight(1f)){Heading(model.companion().optString("name"));Copy(if(pending)model.text("Review before confirming","Vor Bestätigung prüfen")else model.text("Confirmed profile","Bestätigtes Profil"))}}}
   when(section){
    "overview"->{val points=if(pending)model.plan().getJSONObject("points")else item?.optJSONObject("sourceSnapshot")?.optJSONObject("plan")?.optJSONObject("points")?:item?.optJSONObject("goalPoints");PixelCard {Heading(model.text("Weekly priorities","Wochenprioritäten"));goalKeys.sortedByDescending { points?.optInt(it)?:0 }.forEach { key->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){GoalIcon(key,Modifier.size(26.dp));Text(goalCopy(key,model.de).getString("title"),Modifier.weight(1f));Text("${points?.optInt(key)?:0}",color=Gold)}}};PixelCard {Heading(model.text("Available transport","Verfügbare Verkehrsmittel"));modes.forEach { mode->Copy("${modeName(mode,model.de)}: ${if(profile?.optJSONObject("availability")?.optBoolean(mode)==true)model.text("Yes","Ja")else model.text("No","Nein")}")}}}
    "feelings"->modes.forEach { mode->PixelCard {Heading(modeName(mode,model.de));TravelScene(model.companion().optString("appearance","robot"),mode,model.settings().optString("motion")=="full");Copy("${metric(profile?.optJSONObject("valences")?.number(mode),2)} · "+model.text("Your reported feeling","Dein berichtetes Gefühl"))}}
    else->{PixelCard {Copy(model.text("Four connections were answered directly. The remaining connections are initial model estimates.","Vier Verbindungen wurden direkt beantwortet. Die übrigen Verbindungen sind anfängliche Modellschätzungen."))};modes.forEach { mode->PixelCard {Heading(modeName(mode,model.de));val beliefs=profile?.optJSONObject("beliefs")?.optJSONObject(mode);val raw=passport.optJSONObject("observed_measurements")?.optJSONObject("beliefs_raw_1_to_7");needKeys.forEach { need->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(needName(need,model.de),Modifier.weight(1f));Column {Text(metric(beliefs?.number(need),2),color=if((beliefs?.number(need)?:0.0)>0)Mint else Gold);Text(if(raw?.has("${mode}__$need")==true)model.text("Reported","Berichtet")else model.text("Estimated","Geschätzt"),style=MaterialTheme.typography.bodySmall,color=Edge)}}}}}}
   }
   if(pending)PixelButton(model.text("Confirm my Passport →","Meinen Passport bestätigen →"),{model.confirmPassport()},Modifier.fillMaxWidth(),primary=true)
   else PixelButton(model.text("Find a journey →","Reise suchen →"),{model.tab="journey"},Modifier.fillMaxWidth(),primary=true)
  }
 }
}

@Composable fun SettingsScreen(model:CricketModel,onExport:(JSONObject,String)->Unit,onImport:()->Unit) {
 var section by remember { mutableStateOf("menu") };var confirm by remember { mutableStateOf<String?>(null) }
 when(section){
  "companion"->Column {TextButton({section="menu"}){Text(model.text("← Menu","← Menü"))};IdentityScreen(model,true)}
  "history"->Column {TextButton({section="menu"}){Text(model.text("← Menu","← Menü"))};HistoryScreen(model,onExport)}
  else->ScreenColumn {
   Heading(model.text("Your Cricket","Dein Cricket"))
   if(section=="menu"){
    listOf("connection" to model.text("Connection","Verbindung"),"display" to model.text("Display & travel","Anzeige & Reisen"),"availability" to model.text("Available transport","Verkehrsmittel"),"companion" to model.text("My companion","Mein Begleiter"),"history" to model.text("Journey history","Reiseverlauf"),"data" to model.text("My data","Meine Daten"),"about" to model.text("About Cricket","Über Cricket")).forEach { (key,label)->PixelButton(label,{section=key},Modifier.fillMaxWidth()) }
    PixelButton(model.text("Create a new Passport","Neuen Passport erstellen"),{confirm="renew"},Modifier.fillMaxWidth())
   }else{
    TextButton({section="menu"}){Text(model.text("← Menu","← Menü"))}
    when(section){
     "connection"->{var url by remember { mutableStateOf(model.api.base()) };PixelCard {Heading(model.text("Backend address","Backend-Adresse"));OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("URL")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri));Copy(model.text("USB: http://localhost:8077","USB: http://localhost:8077"));PixelButton(model.text("Save and connect","Speichern und verbinden"),{model.run { val normalized=model.api.normalizeBase(url);model.store.put("cricket.connection.v1",normalized);val health=model.api.call("/health");require(health.optString("status")=="ok");model.notice=model.text("Backend connected","Backend verbunden") }},enabled=!model.busy,primary=true)}}
     "display"->{val settings=model.settings();var max by remember { mutableStateOf(settings.optInt("maxWalk",500).toString()) };PixelCard {Heading(model.text("Walking limit to transit","Gehstrecke zum ÖPNV"));OutlinedTextField(max,{max=it},Modifier.fillMaxWidth(),suffix={Text("m")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));PixelButton(model.text("Save limit","Limit speichern"),{model.saveSettings(model.settings().put("maxWalk",max.toInt()));model.notice=model.text("Saved","Gespeichert")},enabled=max.toIntOrNull() in 0..10000);Heading(model.text("Animation","Animation"));listOf("full","reduced","off").forEach { motion->PixelButton(when(motion){"full"->model.text("Full","Voll");"reduced"->model.text("Reduced","Reduziert");else->model.text("Off","Aus")},{model.saveSettings(model.settings().put("motion",motion))},Modifier.fillMaxWidth(),primary=settings.optString("motion")==motion)};Row(verticalAlignment=Alignment.CenterVertically){Text(model.text("Larger text","Größere Schrift"),Modifier.weight(1f));Switch(settings.optString("textSize")=="large",{model.saveSettings(model.settings().put("textSize",if(it)"large"else"normal"))})}}}
     "availability"->{val settings=model.settings();val available=settings.optJSONObject("journeyAvailability")?:model.profile()?.getJSONObject("profile")?.getJSONObject("availability")?:JSONObject();PixelCard {Copy(model.text("Applies to new route searches and simulations.","Gilt für neue Routensuchen und Simulationen."));modes.forEach { mode->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){PixelIcon(mode);Text(modeName(mode,model.de),Modifier.weight(1f));Switch(available.optBoolean(mode),{enabled->val next=available.copyJson().put(mode,enabled);if(modes.none { next.optBoolean(it) })model.error=model.text("Keep at least one mode enabled","Mindestens einen Modus aktivieren")else model.saveSettings(model.settings().put("journeyAvailability",next))})}};PixelButton(model.text("Restore Passport availability","Passport-Verfügbarkeit wiederherstellen"),{val s=model.settings();s.remove("journeyAvailability");model.saveSettings(s)})}}
     "data"->PixelCard {PixelButton(model.text("Export backup","Sicherung exportieren"),{onExport(model.store.export(),"cricket-backup.json")},Modifier.fillMaxWidth());PixelButton(model.text("Import backup","Sicherung importieren"),onImport,Modifier.fillMaxWidth());PixelButton(model.text("Clear journey history","Reiseverlauf löschen"),{confirm="history"},Modifier.fillMaxWidth());PixelButton(model.text("Reset local data","Lokale Daten zurücksetzen"),{confirm="reset"},Modifier.fillMaxWidth())}
     "about"->PixelCard {Heading("Cricket 0.3.0");Copy(model.text("Your digital travel companion. Built with Kotlin and Jetpack Compose.","Dein digitaler Reisebegleiter. Mit Kotlin und Jetpack Compose."));Copy("HOTCO · Orion · OTP · Groq");Copy(model.text("Model assessments describe this simulation; they are not measured choice probabilities.","Modelleinschätzungen beschreiben diese Simulation; sie sind keine gemessenen Wahlwahrscheinlichkeiten."));Copy("© OpenStreetMap contributors · GTFS.de · MVB")}
    }
   }
  }
 }
 confirm?.let { action->AlertDialog(onDismissRequest={confirm=null},title={Heading(model.text("Confirm action","Aktion bestätigen"))},text={Copy(when(action){"renew"->model.text("Your confirmed Passport remains available while you create a new one.","Dein bestätigter Passport bleibt erhalten, während du einen neuen erstellst.");"history"->model.text("Delete your saved searches and choices?","Gespeicherte Suchen und Wahlen löschen?");else->model.text("Delete your local profile and history? Export a backup first if you want to keep them.","Lokales Profil und Verlauf löschen? Exportiere vorher eine Sicherung, wenn du sie behalten möchtest.")})},confirmButton={TextButton({when(action){"renew"->model.beginRenewal();"history"->{model.store.clearHistory();model.revision++;model.result=null;model.historyId=null;model.notice=model.text("History cleared","Verlauf gelöscht")};else->{model.store.reset();model.refresh();model.tab="home";model.result=null;model.historyId=null}};confirm=null}){Text(model.text("Confirm","Bestätigen"))}},dismissButton={TextButton({confirm=null}){Text(model.text("Cancel","Abbrechen"))}}) }
}

@Composable fun HistoryScreen(model:CricketModel,onExport:(JSONObject,String)->Unit) {
 val records=remember(model.revision){model.store.history()};var limit by remember {mutableIntStateOf(20)}
 LazyColumn(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item {Heading(model.text("Journey history","Reiseverlauf"));Copy(model.text("A saved choice does not confirm that the trip took place.","Eine gespeicherte Wahl bestätigt keine durchgeführte Reise."));if(records.isEmpty())Copy(model.text("Your searched journeys will appear here.","Deine gesuchten Reisen erscheinen hier."))}
  items(records.take(limit),key={it.getString("search_id")}){ record->PixelCard {val places=record.optJSONObject("places");Heading(places?.optJSONObject("destination")?.optString("label")?:model.text("Journey","Reise"));Copy(places?.optJSONObject("origin")?.optString("label").orEmpty());Copy(record.optString("created_at"));Copy("${record.optString("status")} · ${record.objects("choices").size} "+model.text("choices","Wahlen"));PixelButton(model.text("Open journey","Reise öffnen"),{model.openHistory(record)},enabled=record.optString("status")=="complete");PixelButton(model.text("Export simulation","Simulation exportieren"),{onExport(record,"Cricket-${record.getString("search_id")}.json")});HistoryDetails(model,record);Disclosure(model.text("Saved choices","Gespeicherte Wahlen")){record.objects("choices").forEach { choice->Copy("${modeName(choice.optString("mode_key"),model.de)} · ${choice.optString("chosen_at")}") }}}}
  if(records.size>limit)item {PixelButton(model.text("More journeys","Weitere Reisen"),{limit+=20},Modifier.fillMaxWidth())}
 }
}
@Composable fun Disclosure(title:String,content:@Composable ColumnScope.()->Unit) {
 var expanded by remember { mutableStateOf(false) }
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){TextButton({expanded=!expanded},Modifier.fillMaxWidth()){Text((if(expanded)"▾ "else"▸ ")+title)};if(expanded)Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}
}
