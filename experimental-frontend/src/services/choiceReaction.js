// Presentation of verified comparison criteria, never inferred user emotion.
export function choiceReaction(reflection){
 const comparison=reflection?.explanation?.criteria?.choice_vs_simulation
 if(comparison==='ALIGNED_CLEAR')return {kind:'aligned',expression:'joyful'}
 if(['DIFFERENT_CLEAR','LOWER_THAN_AMBIGUOUS_LEADING_PAIR'].includes(comparison))return {kind:'different',expression:'reflective'}
 if(comparison==='WITHIN_AMBIGUOUS_LEADING_PAIR')return {kind:'close',expression:'curious'}
 return {kind:'unknown',expression:'neutral'}
}
