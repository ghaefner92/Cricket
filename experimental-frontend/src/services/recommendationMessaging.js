// Labels describe the existing consensus rule. They do not create a new ranking.
export function recommendationMessage(view,locale='en',modeName=''){
 const de=locale==='de'
 const text={
  clear:de?['Eine Möglichkeit, die im Modell zu dir passt',`Das Modell spricht in allen verfügbaren Kontextvergleichen klar für ${modeName}. Du kannst diese Möglichkeit entdecken und trotzdem frei vergleichen.`]:['An option that fits you in this simulation',`Across the available context checks, the model clearly favours ${modeName}. You can explore it and still compare freely.`],
  similar:de?['Mehrere Möglichkeiten liegen nah beieinander','Einige Möglichkeiten liegen im Modell nah beieinander. Ich hebe deshalb keinen einzelnen Weg als besten hervor. Was zählt für deine Reise?']:['Some options are closely matched','Some options are close in the model. I’m not highlighting one journey as best. What matters for your trip?'],
  disagreement:de?['Kein gemeinsamer Favorit','Die Kontextvergleiche ergeben keinen gemeinsamen Favoriten unter den angebotenen Möglichkeiten. Schauen wir uns die Wege gemeinsam an.']:['No shared favourite','The context checks don’t establish a shared favourite among the offered options. Let’s look at the journeys together.'],
  incomplete:de?['Verfügbare Wege entdecken','Einige Modellergebnisse fehlen. Du kannst die verfügbaren Wege vergleichen, aber ich kann keinen Modellfavoriten benennen.']:['Explore available journeys','Some model results are missing. You can compare the available journeys, but I can’t name a model favourite.'],
  uncertain:de?['Verfügbare Wege entdecken','Das Modell zeigt hier keinen klaren Favoriten. Vergleiche die Wege und entscheide, was für diese Reise zählt.']:['Explore available journeys','The model doesn’t show a clear favourite here. Compare the journeys and decide what matters for this trip.']
 }
 const [title,message]=text[view?.state]||text.uncertain
 return {title,message,order:de?'Die Reihenfolge folgt der Routensuche; sie ist keine Rangliste von HOTCO.':'The order follows route search; it is not a HOTCO ranking.'}
}
