// Original pixel particles. They mirror the response, not a reward for a mode.
export function affectParticles(value,time=0,reduced=false){
 if(!Number.isInteger(value)||value===0||Math.abs(value)>3)return []
 const level=Math.abs(value),count=level*2
 return Array.from({length:count},(_,i)=>{
  const progress=reduced ? 0.35 : ((time/1000*.48+i/count)%1+1)%1
  return {
   kind:value>0?'heart':level>=2&&i%3===2?'anger':'broken',
   x:Math.round(Math.sin(i*2.4)*(14+level*5)+(reduced?0:Math.sin(progress*Math.PI*2+i)*4)),
   y:Math.round(-16-progress*38-(i%2)*6),
   opacity:reduced?1:Math.max(.25,1-progress*.7),
  }
 })
}
const heart=['.22..22.','21122112','21111112','.211112.','..2112..','...22...']
function drawHeart(ctx,x,y,broken){
 for(let py=0;py<heart.length;py++)for(let px=0;px<8;px++){
  const cell=heart[py][px]
  if(cell==='.' || broken&&px===(py%2?4:3))continue
  ctx.fillStyle=cell==='2'?'#fff0e3':broken?'#ad729b':'#f77f92'
  ctx.fillRect(x+px*2,y+py*2,2,2)
 }
}
function drawAnger(ctx,x,y){
 // Four separated pixel corners: classic comic frustration mark.
 ctx.fillStyle='#ee8d85'
 for(const [dx,dy,w,h] of [[1,0,2,5],[0,3,5,2],[9,0,2,5],[7,3,5,2],[1,8,2,5],[0,8,5,2],[9,8,2,5],[7,8,5,2]])ctx.fillRect(x+dx,y+dy,w,h)
}
export function drawAffectParticles(ctx,value,x,y,time=0,reduced=false){
 const particles=affectParticles(value,time,reduced)
 if(!particles.length)return
 ctx.save()
 for(const particle of particles){
  ctx.globalAlpha=particle.opacity
  if(particle.kind==='anger')drawAnger(ctx,x+particle.x,y+particle.y)
  else drawHeart(ctx,x+particle.x,y+particle.y,particle.kind==='broken')
 }
 ctx.restore()
}
