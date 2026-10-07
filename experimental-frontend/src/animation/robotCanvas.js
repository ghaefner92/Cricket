import robotUrl from '../art/robot-mint-sheet-v1.png'
import explorerUrl from '../art/explorer-mint-sheet-v1.png'
import naturalistUrl from '../art/naturalist-sheet-v1.png'
import creatureUrl from '../art/creature-mint-sheet-v1.png'
const urls={robot:robotUrl,explorer:explorerUrl,naturalist:naturalistUrl,creature:creatureUrl}
const cache={}
function load(appearance){
 if(cache[appearance]||typeof Image==='undefined')return
 const image=new Image(),entry={image,ready:false};cache[appearance]=entry
 image.onload=()=>{entry.ready=image.naturalWidth>0&&image.naturalHeight>0;if(entry.ready&&typeof window!=='undefined')window.dispatchEvent(new Event('cricket-robot-canvas-ready'))}
 image.src=urls[appearance]
}
export function drawRobotCanvas(ctx,settings,{x,y,size=64,mood='neutral'}={},time=0,reduced=false){
 if(!urls[settings.appearance])return false
 load(settings.appearance);const entry=cache[settings.appearance];if(!entry?.ready)return false
 const image=entry.image
 const row=mood==='joyful'?2:mood==='reflective'?3:0
 // Neutral blink: long open-eye hold and short closing/reopening frames.
 const cycle=((Math.max(0,time)%2810)+2810)%2810
 const frame=reduced?0:row===0&&settings.appearance==='naturalist'?Math.floor(Math.max(0,time)/260)%4:row===0?(cycle<2400?0:cycle<2520?1:cycle<2630?2:3):Math.floor(Math.max(0,time)/650)%4
 const w=image.naturalWidth/4
 const bounds=(settings.appearance==='creature'?[[0,322],[322,620],[620,916],[916,1230]]:settings.appearance==='naturalist'?[[0,312],[312,607],[607,904],[904,1230]]:[[0,315],[315,610],[610,907],[907,1230]])[row]
 const sy=['explorer','naturalist','creature'].includes(settings.appearance)?bounds[0]*image.naturalHeight/1230:row*image.naturalHeight/4
 const h=['explorer','naturalist','creature'].includes(settings.appearance)?(bounds[1]-bounds[0])*image.naturalHeight/1230:image.naturalHeight/4
 ctx.imageSmoothingEnabled=false
 ctx.drawImage(image,frame*w,sy,w,h,Math.round(x),Math.round(y),size,size)
 return true
}
