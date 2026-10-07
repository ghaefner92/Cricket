// These meanings describe a saved model comparison, not the user's feelings or correctness.
export function reactionMeaning(kind,locale='en'){
 const de=locale==='de'
 return {
  aligned:de?'Deine Wahl stimmt mit der klaren Tendenz dieser Simulation überein.':'Your choice matches the clear tendency in this simulation.',
  different:de?'Deine Wahl weicht von dieser simulierten Tendenz ab. Das ist keine Bewertung deiner Entscheidung.':'Your choice differs from this simulated tendency. This is not a judgement of your decision.',
  close:de?'Die führenden Möglichkeiten liegen nahe beieinander; es gibt keinen klaren Favoriten.':'The leading options are close; there is no clear favourite.',
  unknown:de?'Ein verlässlicher Vergleich liegt noch nicht vor.':'A reliable comparison is not yet available.'
 }[kind]||''
}
