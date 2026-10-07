// Plain text only: highlights are rendered as Vue text, never HTML.
const patterns=[
 ['car',/(?<![\p{L}\p{N}_])(?:the car|car|Auto|Autofahren)(?![\p{L}\p{N}_])/giu],
 ['bike',/(?<![\p{L}\p{N}_])(?:the bicycle|bicycle|cycling|bike|Fahrrad|Radfahren)(?![\p{L}\p{N}_])/giu],
 ['walk',/(?<![\p{L}\p{N}_])(?:walking|walk|zu Fuß|Gehen)(?![\p{L}\p{N}_])/giu],
 ['pt',/(?<![\p{L}\p{N}_])(?:public transport|ÖPNV|öffentliche Verkehrsmittel|öffentlichen Verkehrsmitteln)(?![\p{L}\p{N}_])/giu],
 ['positive',/(?<![\p{L}\p{N}_])(?:fits|goes with|backed|towards this choice|support|supports|pas(?:st|sen)|dafür|unterstützt)(?![\p{L}\p{N}_])/giu],
 ['tension',/(?<![\p{L}\p{N}_])(?:trade-off|pull the other way|pulls the other way|away from this choice|Spannung|dagegen)(?![\p{L}\p{N}_])/giu]]
export function dialogueTokens(text,row={}){
 const local=[...patterns]
 const emotionKind=row.rule==='JOINT_SUPPORT'?'positive':row.rule==='JOINT_OPPOSITION'?'tension':row.rule==='OPPOSED_CHANNELS'?(row.text.startsWith('The model’s emotional side')||row.text.startsWith('Die emotionale Seite')?'positive':'tension'):null
 if(emotionKind)local.push([emotionKind,/(?:[Tt]he model’s emotional side|[Ii]ts emotional side|[Ss]eine emotionale Seite|[Dd]ie emotionale Seite des Modells)/gu])
 const candidates=[]
 for(const [kind,pattern] of local){pattern.lastIndex=0;for(const match of text.matchAll(pattern))candidates.push({start:match.index,end:match.index+match[0].length,kind,text:match[0]})}
 candidates.sort((a,b)=>a.start-b.start||b.end-a.end)
 const tokens=[];let end=0
 for(const match of candidates){if(match.start<end)continue;if(match.start>end)tokens.push({kind:'text',text:text.slice(end,match.start)});tokens.push({kind:match.kind,text:match.text});end=match.end}
 if(end<text.length)tokens.push({kind:'text',text:text.slice(end)})
 return tokens
}
export function narrativeRows(reflection,statements,opening=''){
 const preference=reflection?.narration?.selection?.emphasis
 const order=['selection',...(preference==='tensions'?['tensions','affinities']:['affinities','tensions']),'balance','comparison','observations','context','evolution','uncertainty','model_detail']
 const rows=opening?[{id:'opening',rule:'OPENING',section:'opening',text:opening}]:[]
 for(const section of order)rows.push(...statements.filter(row=>row.section===section))
 // Preserve every statement even if an older record uses another section.
 rows.push(...statements.filter(row=>!order.includes(row.section)))
 return rows
}
export function dialoguePages(rows,limit=850){
 const pages=[];let page=[],length=0
 for(const row of rows){if(page.length&&length+row.text.length>limit){pages.push(page);page=[];length=0}page.push(row);length+=row.text.length}
 if(page.length)pages.push(page)
 return pages.length?pages:[[]]
}
export function paragraphIcon(row){
 if(row.rule==='OBSERVED_temperature')return 'temperature'
 if(row.rule==='OBSERVED_rain')return 'rain'
 if(row.rule==='OBSERVED_windSpeed')return 'wind'
 if(row.section==='affinities'||row.rule==='JOINT_SUPPORT')return 'smile'
 if(row.section==='tensions'||row.rule==='JOINT_OPPOSITION'||row.rule==='OPPOSED_CHANNELS')return 'thoughtful'
 if(['uncertainty','model_detail'].includes(row.section))return 'question'
 return null
}
