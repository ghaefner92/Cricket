// Original procedural pixel art. NES.css supplies UI, not transport sprites.
export function startMagdeburgScene(canvas, { reducedMotion = false } = {}) {
  const ctx = canvas.getContext('2d')
  if (!ctx) return () => {}
  const W = 480, H = 280
  canvas.width = W; canvas.height = H
  ctx.imageSmoothingEnabled = false
  const C = { sky:'#17162e', far:'#302d4a', stone:'#797189', lit:'#c8b6b0', shadow:'#4a435e', window:'#e9cc80', road:'#28273a', grass:'#3c665e', mint:'#83d5b8', pink:'#f39cbb', gold:'#ffd279', blue:'#87bbee', ink:'#201e34', white:'#ede5dc' }
  let frame = 0, stopped = false, start = null
  function rect(x,y,w,h,color) {ctx.fillStyle=color;ctx.fillRect(Math.round(x),Math.round(y),w,h)}
  function line(points,color,width=2) {ctx.strokeStyle=color;ctx.lineWidth=width;ctx.beginPath();points.forEach(([x,y],i)=>i?ctx.lineTo(Math.round(x),Math.round(y)):ctx.moveTo(Math.round(x),Math.round(y)));ctx.stroke()}
  function disk(x,y,r,color) {ctx.fillStyle=color;ctx.beginPath();ctx.arc(Math.round(x),Math.round(y),r,0,Math.PI*2);ctx.fill()}
  function wheel(x,y,r,t) {disk(x,y,r,C.ink);disk(x,y,r-2,C.white);disk(x,y,r-4,C.ink);line([[x-r*Math.cos(t),y-r*Math.sin(t)],[x+r*Math.cos(t),y+r*Math.sin(t)]],C.stone,1)}
  function arch(x,y,w,h,color) {rect(x,y+4,w,h-4,color);rect(x+2,y+2,w-4,2,color);rect(x+4,y,w-8,2,color)}
  function cathedral() {
    // Twin west towers, open pinnacles, central Gothic facade and nave.
    rect(265,123,52,67,C.shadow);rect(274,117,34,6,C.stone)
    rect(195,126,69,64,C.stone);rect(199,130,61,57,C.lit)
    for (const x of [177,249]) {
      rect(x,83,34,107,C.stone);rect(x+3,85,9,101,C.lit);rect(x+28,85,6,105,C.shadow)
      rect(x-2,80,38,4,C.lit);rect(x+5,73,24,7,C.stone)
      rect(x+7,67,20,6,C.lit);rect(x+9,62,16,5,C.stone)
      for(const p of [x+3,x+15,x+27]) {rect(p,59,3,18,C.lit);rect(p+1,54,1,5,C.window)}
      for(const y of [91,119,146]) {arch(x+12,y,11,19,C.shadow);rect(x+17,y+4,1,13,C.window);rect(x+1,y+24,32,3,C.lit)}
      rect(x-2,174,38,4,C.lit)
    }
    rect(211,116,38,11,C.stone);rect(216,108,28,8,C.lit);rect(222,103,16,5,C.stone)
    disk(230,140,12,C.shadow);disk(230,140,8,C.window);disk(230,140,4,C.stone)
    line([[220,140],[240,140]],C.stone);line([[230,130],[230,150]],C.stone)
    arch(219,161,22,29,C.shadow);arch(223,166,14,24,C.ink)
    for(const x of [201,255]) {rect(x,150,4,40,C.shadow);rect(x-2,145,8,5,C.lit)}
    rect(170,190,151,4,C.lit);rect(166,194,159,3,C.stone)
  }
  function city(t) {
    rect(0,0,W,H,C.sky)
    for(let i=0;i<40;i++) {const x=(i*73+19)%W,y=(i*31+11)%100;rect(x,y,1,1,i%4?C.stone:C.white)}
    rect(0,171,W,36,C.far)
    const buildings=[[0,133,38],[40,148,24],[69,124,39],[115,139,45],[331,140,30],[369,127,43],[420,145,35],[459,133,21]]
    buildings.forEach(([x,y,w],i)=>{rect(x,y,w,197-y,i%2?C.shadow:C.far);rect(x-2,y-3,w+4,3,C.stone);for(let wx=x+7;wx<x+w-3;wx+=10)for(let wy=y+10;wy<187;wy+=15)rect(wx,wy,3,5,C.window)})
    cathedral()
    rect(0,198,W,9,C.grass);rect(0,207,W,17,C.shadow)
    rect(0,224,W,56,C.road);rect(0,225,W,2,C.stone)
    for(let x=0;x<W;x+=29)rect(x,259,15,2,C.lit)
    // Rail tracks.
    rect(0,203,W,1,C.stone);rect(0,206,W,1,C.stone)
    for(const x of [90,344]) {rect(x,177,2,23,C.stone);rect(x-3,175,8,3,C.gold)}
  }
  function tram(x) {
    rect(x,179,87,23,C.blue);rect(x+4,177,75,2,C.white)
    rect(x+2,195,83,4,C.mint);rect(x+78,185,9,10,C.white)
    for(let i=0;i<6;i++)rect(x+7+i*11,182,8,9,C.ink)
    rect(x+61,191,1,9,C.stone);rect(x+17,200,9,4,C.ink);rect(x+65,200,9,4,C.ink)
    line([[x+32,177],[x+38,169],[x+48,177]],C.stone)
  }
  function car(x,t) {
    rect(x,240,48,11,C.pink);rect(x+9,233,29,8,C.pink)
    rect(x+13,235,10,6,C.ink);rect(x+26,235,9,6,C.ink)
    rect(x+44,243,4,3,C.gold);rect(x,247,48,3,C.shadow)
    wheel(x+10,251,5,t*6);wheel(x+38,251,5,t*6)
  }
  function bike(x,t) {
    const y=235
    wheel(x,y,8,t*7);wheel(x+30,y,8,t*7)
    line([[x,y],[x+11,y-13],[x+20,y],[x,y],[x+16,y-1],[x+23,y-17],[x+30,y]],C.mint,2)
    line([[x+20,y-18],[x+26,y-18]],C.white)
    disk(x+13,y-30,4,C.gold);rect(x+9,y-25,7,10,C.blue)
    line([[x+15,y-23],[x+23,y-18]],C.gold)
    const d=Math.sin(t*9)*3
    line([[x+12,y-15],[x+17+d,y-8],[x+14,y-1]],C.white)
    line([[x+12,y-15],[x+10-d,y-7],[x+16,y-2]],C.blue)
  }
  function person(x,t) {
    disk(x,199,4,C.gold);rect(x-3,203,7,10,C.mint)
    const d=Math.sin(t*9)*4
    line([[x,213],[x-3-d,220]],C.white,3);line([[x,213],[x+3+d,220]],C.white,3)
    line([[x-2,206],[x-6+d,211]],C.gold,2);line([[x+3,206],[x+7-d,211]],C.gold,2)
  }
  function fireworks(t) {
    if(reducedMotion) return
    const colors=[C.pink,C.mint,C.gold,C.blue]
    for(let k=0;k<3;k++) {
      const phase=(t+k*1.65)%6.4
      const x=[72,383,128][k],y=[66,47,96][k]
      if(phase<.6) {rect(x,y+50*(1-phase/.6),2,4,C.gold);continue}
      if(phase>2.2)continue
      const life=(phase-.6)/1.6,r=life*32
      ctx.globalAlpha=Math.max(0,1-life)
      for(let j=0;j<14;j++){const angle=j*Math.PI*2/14;rect(x+Math.cos(angle)*r,y+Math.sin(angle)*r+life*life*8,2,2,colors[k]);}
      ctx.globalAlpha=1
    }
  }
  function render(ms) {
    if(stopped)return
    if(start===null)start=ms
    const t=reducedMotion?2.5:(ms-start)/1000
    city(t);fireworks(t)
    tram(reducedMotion?338:((t*18+340)%(W+110))-100)
    person(reducedMotion?142:((t*13+140)%(W+20))-10,t)
    bike(reducedMotion?250:((t*28+250)%(W+55))-35,t)
    car(reducedMotion?100:W-((t*36+365)%(W+65)),t)
    if(!reducedMotion)frame=requestAnimationFrame(render)
  }
  frame=requestAnimationFrame(render)
  return ()=>{stopped=true;cancelAnimationFrame(frame)}
}
