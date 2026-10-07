// IMIQ citadel backdrop v1
import {drawHbfBackdrop} from './hbfBackdrop.js'
import {drawCitadelBackdrop} from './citadelBackdrop.js'
import {drawAffectParticles} from './affectParticles.js'
import {drawRobotCanvas} from './robotCanvas.js'
import {drawTransportSprite} from './transportSprites.js'
import {companions,palettes} from '../art/companions.js'
function rect(ctx,x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h))}
function tower(ctx){
 // Original stepped silhouette and timber rings based on the supplied photo.
 const cx=222
 for(let y=26;y<120;y+=2){const half=5+(y-26)*.58;rect(ctx,cx-half,y,half*2,2,'#ece6d8')}
 for(const [y,half] of [[49,19],[69,32],[91,45],[119,64]]){
  rect(ctx,cx-half,y,half*2,4,'#ac7755');rect(ctx,cx-half,y+4,half*2,2,'#655647')
 }
 for(const direction of [-1,1])for(let y=36;y<115;y+=4){const half=4+(y-26)*.43;rect(ctx,cx+direction*half,y,2,4,'#b6a893')}
 rect(ctx,cx-60,125,120,17,'#615e55');rect(ctx,cx-57,127,114,9,'#8fb4b9')
 for(let x=cx-56;x<cx+60;x+=12)rect(ctx,x,125,3,17,'#d9c9aa')
 rect(ctx,cx-8,126,16,17,'#2b454a')
}
function avatar(ctx,x,y,settings,scale=2,frame=0){
 const sprite=companions[settings.appearance]||companions.robot, colors=palettes[settings.palette]||palettes.mint
 for(let py=0;py<24;py++)for(let px=0;px<16;px++)if(sprite[py][px]!=='.'){
  const legShift=py>=18&&settings.mode==='walk'?(px<8?frame:-frame):0
  rect(ctx,x+px*scale+legShift,y+py*scale,scale,scale,colors[Number(sprite[py][px])])
 }
 const my=settings.appearance==='robot'?6:settings.appearance==='explorer'?8:9
 rect(ctx,x+6*scale,y+(my-1)*scale,4*scale,3*scale,colors[2])
 const v=settings.value
 if(v!==null&&v!==undefined&&v!==0){const sideY=v>0?my-1:my+1;rect(ctx,x+6*scale,y+sideY*scale,scale,scale,colors[1]);rect(ctx,x+9*scale,y+sideY*scale,scale,scale,colors[1]);rect(ctx,x+6*scale,y+my*scale,4*scale,scale,colors[1])}
 else rect(ctx,x+6*scale,y+my*scale,4*scale,scale,colors[1])
}
function wheel(ctx,x,y,frame){rect(ctx,x-7,y-7,14,14,'#202439');rect(ctx,x-4,y-4,8,8,'#bdc9d3');rect(ctx,x-1,y-1,2,2,'#202439');if(frame)rect(ctx,x-4,y-1,8,2,'#202439');else rect(ctx,x-1,y-4,2,8,'#202439')}
function drawTransportBase(ctx,settings,time=0,reduced=false){
 const t=reduced?0:time/1000,frame=reduced?0:Math.floor(t*5)%2
 ctx.imageSmoothingEnabled=false
 if(settings.background==='hbf'){drawHbfBackdrop(ctx,settings.sceneTime??time,reduced)}else if(settings.background==='citadel'){drawCitadelBackdrop(ctx,time,reduced)}else{
 rect(ctx,0,0,320,180,'#719bc2')
 for(const [x,y] of [[24,26],[90,18],[270,35]]){rect(ctx,x,y,22,5,'#eaf3ef');rect(ctx,x+5,y-4,10,4,'#eaf3ef')}
 rect(ctx,0,105,320,43,'#6d9c75');tower(ctx)
 for(const x of [8,32,65,290,312]){rect(ctx,x,120,3,22,'#876448');rect(ctx,x-10,103,22,19,'#466e55');rect(ctx,x-7,96,15,12,'#5c8661')}
 rect(ctx,0,148,320,32,'#d3c8ad');rect(ctx,0,171,320,9,'#819c6e')
 }
 const x=Math.round(46+(!reduced?Math.sin(t*.55)*24:0))
 // Valence walking uses the existing expressive robot sheet; other modes use their vehicle sheet.
 if(settings.background===undefined && settings.mode==='walk' && drawRobotCanvas(ctx,settings,{x:x-16,y:108,size:64,mood:settings.value>0?'joyful':settings.value<0?'reflective':'neutral'},time,reduced))return
 if(drawTransportSprite(ctx,settings,x,time,reduced))return
 if(settings.mode==='walk'){avatar(ctx,x,116+frame,settings,2,frame?2:-2)}
 if(settings.mode==='bike'){
  wheel(ctx,x+4,158,frame);wheel(ctx,x+54,158,frame)
  ctx.strokeStyle='#e9bf76';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(x+4,158);ctx.lineTo(x+25,136);ctx.lineTo(x+35,158);ctx.lineTo(x+4,158);ctx.moveTo(x+25,136);ctx.lineTo(x+45,136);ctx.lineTo(x+54,158);ctx.stroke()
  avatar(ctx,x+10,103,settings,2);rect(ctx,x+44,129,11,3,'#202439');rect(ctx,x+19,138,14,3,'#202439');rect(ctx,x+30,149+frame*3,8,3,'#202439')
 }
 if(settings.mode==='car'){
  rect(ctx,x-6,127,110,30,'#202439');rect(ctx,x+10,111,60,17,'#202439');rect(ctx,x+14,114,52,23,'#b7d9e2')
  avatar(ctx,x+20,107,settings,2)
  rect(ctx,x-2,138,102,17,'#d88978');rect(ctx,x+68,123,22,15,'#d88978');rect(ctx,x+73,136,24,4,'#f2a393');rect(ctx,x+94,140,6,7,'#ffe4a3')
  rect(ctx,x+45,125,3,13,'#202439');rect(ctx,x+37,129,16,3,'#202439');wheel(ctx,x+15,156,frame);wheel(ctx,x+79,156,frame)
 }
 if(settings.mode==='pt'){
  rect(ctx,x-15,108,157,49,'#202439');rect(ctx,x-11,112,149,40,'#d7c7ad')
  for(let n=0;n<4;n++)rect(ctx,x-7+n*33,117,27,24,'#a8ceda')
  avatar(ctx,x+27,109,settings,2);rect(ctx,x-11,139,149,13,'#c37e74');rect(ctx,x+66,115,27,37,'#263b4d');rect(ctx,x+71,120,17,19,'#a8ceda')
  rect(ctx,x+125,115,13,26,'#99c0d0');rect(ctx,x+40,96,3,12,'#202439');rect(ctx,x+28,95,31,3,'#202439')
  wheel(ctx,x+14,156,frame);wheel(ctx,x+110,156,frame);rect(ctx,0,165,320,2,'#78776c');rect(ctx,0,169,320,2,'#78776c')
 }
}

// IMIQ valence reactions v2
export function drawElbScene(ctx,settings,time=0,reduced=false){
 drawTransportBase(ctx,settings,time,reduced)
 const x=Math.round(46+(!reduced?Math.sin(time/1000*.55)*24:0))
 const positions={walk:[x+8,116],bike:[x+18,103],car:[x+28,107],pt:[x+35,109]}
 const [headX,headY]=positions[settings.mode]||positions.walk
 drawAffectParticles(ctx,settings.value,headX,headY,time,reduced)
}