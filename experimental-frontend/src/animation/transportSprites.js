import robotUrl from '../art/robot-transport-sheet-v1.png'
import explorerUrl from '../art/explorer-transport-sheet-v1.png'
import naturalistUrl from '../art/naturalist-transport-sheet-v1.png'
import creatureUrl from '../art/creature-transport-sheet-v1.png'
const urls={robot:robotUrl,explorer:explorerUrl,naturalist:naturalistUrl,creature:creatureUrl},cache={}
// Measured row bounds in the generated 1265 x 1243 sheet; equal quarters would clip bicycle wheels.
const specs={walk:{row:0,top:20,bottom:325,size:70,period:240},bike:{row:1,top:325,bottom:665,size:90,period:220},car:{row:2,top:665,bottom:925,size:112,period:260},pt:{row:3,top:925,bottom:1210,size:148,period:300}}
function load(appearance){
 if(cache[appearance]||typeof Image==='undefined')return
 const image=new Image(),entry={image,ready:false};cache[appearance]=entry
 image.onload=()=>{entry.ready=image.naturalWidth>0&&image.naturalHeight>0;if(entry.ready&&typeof window!=='undefined')window.dispatchEvent(new Event('cricket-transport-sprites-ready'))}
 image.src=urls[appearance]
}
export function drawTransportSprite(ctx,settings,x,time=0,reduced=false){
 // Valence signs remain represented by the scene's existing affect particles.
 if(!urls[settings.appearance]||!['hbf','citadel',undefined].includes(settings.background))return false
 const spec=specs[settings.mode];if(!spec)return false
 load(settings.appearance);const entry=cache[settings.appearance];if(!entry?.ready)return false
 const image=entry.image
 const bounds=settings.appearance==='naturalist'?[[45,347],[347,681],[681,915],[915,1210]][spec.row]:settings.appearance==='explorer'?[[35,324],[324,664],[664,905],[905,1193]][spec.row]:[spec.top,spec.bottom]
 const referenceHeight=['naturalist','creature'].includes(settings.appearance)?1230:1243
 const frame=reduced?0:Math.floor(Math.max(0,time)/spec.period)%4
 const columns=settings.appearance==='creature'?[[0,319.5,639,958.5,1278],[0,341,656,973,1278],[0,329,650,966,1278],[0,330,647,962,1278]][spec.row]:settings.appearance==='naturalist'?[[0,319.5,639,958.5,1278],[0,340,658,974,1278],[0,337,655,972,1278],[0,326,644,962,1278]][spec.row]:null
 const sx=columns?columns[frame]*image.naturalWidth/1278:frame*image.naturalWidth/4
 const creatureBounds=spec.row===0?[[20,324],[20,326],[20,328],[20,328]][frame]:spec.row===1?[[324,668],[326,668],[328,668],[328,668]][frame]:spec.row===2?[668,907]:[907,1200]
 const activeBounds=settings.appearance==='creature'?creatureBounds:bounds
 const w=columns?(columns[frame+1]-columns[frame])*image.naturalWidth/1278:image.naturalWidth/4,sy=activeBounds[0]*image.naturalHeight/referenceHeight,h=(activeBounds[1]-activeBounds[0])*image.naturalHeight/referenceHeight
 const height=Math.round(spec.size*h/w)
 ctx.imageSmoothingEnabled=false
 ctx.drawImage(image,sx,sy,w,h,Math.round(x-10),172-height,spec.size,height)
 if(settings.mode==='pt'){ctx.fillStyle='#78776c';ctx.fillRect(0,174,320,2);ctx.fillRect(0,178,320,2)}
 return true
}
