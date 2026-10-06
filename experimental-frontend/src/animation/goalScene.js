// Original pixel art for the eleven canonical needs; no transport preference cues.
import {companions,palettes} from '../art/companions.js'
export const goalArt = {
 env:['....gg....','...gggg...','..gggggg..','...gggg...','....bb....','....bb....','...bbbb...'],
 health_activity:['.....ww...','....wwww..','.....ww...','...mmmm...','..m.m..m..','....m.....','...w.w....','..w...w...'],
 cost:['..yyyyyy..','.y......y.','y...yy...y','y..y.y...y','y...yy...y','.y......y.','..yyyyyy..'],
 time:['..wwwwww..','.w......w.','w....w...w','w....w...w','w....www.w','w........w','.w......w.','..wwwwww..'],
 comfort_physical:['..mmmm....','..mmmm....','..mmmm....','..mmmm....','..mmmmmm..','..mmmmmm..','..b....b..','..b....b..'],
 reliable:['....y.....','....yyy...','....yyyyy.','....yyy...','....y.....','....y.....','....y.....','..wwwww...'],
 flex:['...w..w...','..ww..ww..','.www..www.','wwwwwwwwww','.www..www.','..ww..ww..','...w..w...'],
 safety_crime:['..wwwwww..','..wmmmmw..','..wmmmmw..','..wmmmmw..','...wmmw...','....ww....'],
 health_infection:['...wwww...','...wmmw...','wwwmmmmwww','wmmmmmmmmw','wwwmmmmwww','...wmmw...','...wwww...'],
 crowding:['ww......ww','w........w','w........w','..........','..........','w........w','w........w','ww......ww'],
 safety_accident:['..mmmmmm..','..myyyym..','..myyyym..','..mmmmmm..','....bb....','....bb....','...bbbb...']
}
export function drawGoalIcon(ctx,key,x,y,scale=3,time=0,reduced=false){
 const colors={g:'#8bc98a',b:'#b7977b',w:'#e8efff',m:'#86d8d0',y:'#ffda83'}
 const bob=reduced?0:Math.floor(Math.sin(time/600)*2)
 for(const [py,row] of (goalArt[key]||goalArt.env).entries()) for(const [px,c] of [...row].entries()) if(c!=='.'){ctx.fillStyle=colors[c];ctx.fillRect(x+px*scale,y+py*scale+bob,scale,scale)}
 if(!reduced){ctx.fillStyle='#ffda83';const phase=Math.floor(time/500)%3;ctx.fillRect(x+34+phase*3,y-5,2,2);ctx.fillRect(x-5,y+15-phase*3,2,2)}
}
export function drawGoalScene(ctx,settings,time=0,reduced=false){
 ctx.imageSmoothingEnabled=false
 ctx.fillStyle='#182744';ctx.fillRect(0,0,320,152)
 ctx.fillStyle='#324a70';ctx.fillRect(0,85,320,38)
 ctx.fillStyle='#466b73';ctx.fillRect(0,117,320,35)
 ctx.fillStyle='#91ab95';ctx.fillRect(0,119,320,3)
 ctx.fillStyle='#7c8c9f';ctx.fillRect(0,131,320,15)
 for(let i=0;i<8;i++){ctx.fillStyle='#a7b6cc';ctx.fillRect(10+i*42,138,18,2)}
 for(const [x,y] of [[25,25],[88,37],[174,19],[290,34]]){ctx.fillStyle='#b8c8ec';ctx.fillRect(x,y,2,2)}
 const t=reduced?0:time/1000
 const x=reduced?85:82+Math.sin(t*.7)*22
 const rows=companions[settings.appearance]||companions.robot,colors=palettes[settings.palette]||palettes.mint
 const stride=reduced?0:Math.floor(t*5)%2?2:-2
 ctx.fillStyle='#17233c';ctx.fillRect(Math.round(x)-3,127,40,4)
 for(const [py,row] of rows.entries())for(const [px,c] of [...row].entries())if(c!=='.'){
  const shift=py>=18?(px<8?stride:-stride):0
  ctx.fillStyle=colors[Number(c)];ctx.fillRect(Math.round(x)+px*2+shift,80+py*2,2,2)
 }
 // Each goal has an equal-size pedestal and its own animated symbol.
 ctx.fillStyle='#bbc9de';ctx.fillRect(229,114,56,5);ctx.fillStyle='#52688e';ctx.fillRect(234,119,46,8)
 drawGoalIcon(ctx,settings.goal,236,69,4,time,reduced)
 if(settings.pulse>0&&!reduced){ctx.fillStyle='#ffda83';for(let i=0;i<4;i++)ctx.fillRect(112+i*20,78-((time/15+i*12)%34),3,3)}
}
