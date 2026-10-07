import {weatherTiles} from './routePresentation.js'
const weatherRules={OBSERVED_temperature:'temperature',OBSERVED_rain:'rain',OBSERVED_windSpeed:'windSpeed'}
// Presentation only: retain the full verified response and all other statements.
export function dialogueStatements(statements,choiceCard){
 const known=new Set(choiceCard?.candidate?weatherTiles(choiceCard.candidate).filter(t=>t.known).map(t=>t.variable):[])
 return statements.filter(row=>!known.has(weatherRules[row.rule]))
}
