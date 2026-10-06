// Original procedural pixel scenes based on the five user-supplied references.
import {companions,palettes} from '../art/companions.js'
const W=320,H=180
function r(c,x,y,w,h,color){c.fillStyle=color;c.fillRect(Math.round(x),Math.round(y),w,h)}
function line(c,points,color,width=2){c.strokeStyle=color;c.lineWidth=width;c.beginPath();points.forEach(([x,y],i)=>i?c.lineTo(Math.round(x),Math.round(y)):c.moveTo(Math.round(x),Math.round(y)));c.stroke()}
function text(c,str,x,y){c.font='9px "Press Start 2P", monospace';c.fillStyle='#fff4d2';c.fillText(str,x,y)}
function person(c,x,y,t,color='#86c4ed',scale=1,jump=false){const dy=jump?Math.round(Math.abs(Math.sin(t*4))*5):0;r(c,x+3*scale,y-dy,5*scale,5*scale,'#e2bb96');r(c,x+2*scale,y+5*scale-dy,7*scale,9*scale,color);r(c,x+3*scale,y+14*scale-dy,2*scale,6*scale,'#202c49');r(c,x+7*scale,y+14*scale-dy,2*scale,6*scale,'#202c49');if(jump){line(c,[[x,y+5-dy],[x-2,y-dy]],'#e2bb96');line(c,[[x+10,y+5-dy],[x+12,y-dy]],'#e2bb96');r(c,x-3,y-3-dy,16,2,'#edf4ff')}}
function avatar(c,s,t,reduced){
 const answered=Number.isInteger(s.rating),discomfort=answered?Math.max(0,4-s.rating):0,calm=answered&&s.rating>4
 const jitter=reduced?0:Math.round(Math.sin(t*(5+discomfort*3))*discomfort*.6)
 const x=143+(reduced?0:Math.round(Math.sin(t*.6)*7))+jitter,y=127+(reduced?0:discomfort?Math.floor(t*6)%2:0)
 const rows=companions[s.appearance]||companions.robot,colors=palettes[s.palette]||palettes.mint,step=reduced?0:(Math.floor(t*(discomfort?3:5))%2?2:-2)
 r(c,x-3,173,38,3,'#24353f');rows.forEach((row,py)=>[...row].forEach((a,px)=>{if(a!=='.'){
  const lean=s.condition==='wind'&&discomfort?Math.round((24-py)*discomfort/10):0
  r(c,x+px*2+(py>=18?(px<8?step:-step):0)+lean,y+py*2,2,2,colors[Number(a)])
 }}))
 const mouth=s.appearance==='robot'?6:s.appearance==='explorer'?8:9
 if(answered){r(c,x+12,y+(mouth-1)*2,8,6,colors[2]);r(c,x+12,y+(calm?mouth-1:mouth+1)*2,2,2,colors[1]);r(c,x+18,y+(calm?mouth-1:mouth+1)*2,2,2,colors[1]);r(c,x+14,y+mouth*2,4,2,colors[1])}
 if(discomfort){for(let i=0;i<discomfort;i++)r(c,x-6-i*4,y-4+(reduced?0:Math.floor(t*4)%3),2,5,'#f8c998')}
 if(s.condition==='heat'&&discomfort)r(c,x+25,y+15+(reduced?0:Math.floor(t*4)%8),2,4,'#a9e1f1')
 if(s.condition==='traffic'&&discomfort)r(c,x+32,y+36,3,3,'#f8c998')
}
function snowman(c,s,t,reduced){
 const discomfort=Number.isInteger(s.rating)?Math.max(0,4-s.rating):0
 const x=146+(reduced?0:Math.round(Math.sin(t*(discomfort?7:2))*2)),y=128+(reduced?0:-Math.round(Math.abs(Math.sin(t*2))*3))
 r(c,x-4,y+23,31,22,'#e9f1f5');r(c,x-1,y+17,25,9,'#e9f1f5');r(c,x+2,y+5,19,18,'#f7fbff');r(c,x+5,y,13,7,'#f7fbff')
 r(c,x+3,y-3,17,4,'#26364e');r(c,x+6,y-11,11,9,'#26364e');r(c,x+6,y-5,11,2,'#db8276');r(c,x+5,y+10,2,2,'#26364e');r(c,x+15,y+10,2,2,'#26364e');r(c,x+10,y+13,9,2,'#d9945d');r(c,x+4,y+23,19,4,'#db8276');r(c,x+18,y+27,3,10,'#db8276')
 for(const yy of [30,37])r(c,x+10,y+yy,2,2,'#26364e')
 const wave=reduced?0:Math.round(Math.sin(t*(discomfort?6:3))*4)
 line(c,[[x-2,y+29],[x-13,y+20+wave]],'#856346',2);line(c,[[x+24,y+29],[x+35,y+20-wave]],'#856346',2)
 if(Number.isInteger(s.rating)){r(c,x+7,y+17,8,1,'#26364e');r(c,x+5,y+(s.rating>4?15:18),2,2,'#26364e');r(c,x+15,y+(s.rating>4?15:18),2,2,'#26364e')}
}

function arena(c,t,reduced){r(c,0,0,W,H,'#81a7c2');r(c,0,148,W,32,'#9da89c');r(c,12,62,295,76,'#6e818c');r(c,12,61,295,12,'#c6d4dc');for(let x=16;x<305;x+=23){line(c,[[x,62],[x+11,40],[x+22,62]],'#d5e0e2',1);r(c,x,75,4,64,'#3e5563');line(c,[[x,77],[x+19,130]],'#bccad0',1)}r(c,102,77,136,18,'#a8424d');text(c,'MDCC Arena',110,90);r(c,15,139,294,5,'#d2d8ce');for(let n=0;n<19;n++)person(c,6+n*16,146,t+n*.43,n%2?'#f2f5ff':'#699bc8',1,!reduced)}
function intersection(c,t,reduced,rain){r(c,0,0,W,H,rain?'#7e93b1':'#83aecb');r(c,0,96,W,84,'#7b8890');for(const [x,w,y,col] of [[0,54,24,'#d9d4c6'],[63,76,17,'#d3937e'],[145,32,28,'#efe3cb'],[267,53,14,'#b66567']]){r(c,x,y,w,105-y,col);r(c,x-2,y-3,w+4,4,'#34485a');for(let px=x+5;px<x+w-4;px+=12)for(let py=y+10;py<98;py+=16){r(c,px,py,6,9,'#304c65');r(c,px,py+9,6,2,'#e0daca')}}r(c,95,11,14,12,'#5e6b7a');r(c,99,4,6,8,'#3d4e62');for(const a of [0,17])line(c,[[185+a,95],[172+a,133],[50+a,180]],'#cad4d2',1);for(const a of [0,18])line(c,[[221+a,95],[246+a,132],[306+a,180]],'#cad4d2',1);for(let x=170;x<263;x+=14)r(c,x,113,9,3,'#f0e8d9');r(c,28,96,2,55,'#354658');r(c,25,94,8,5,'#f7d68d');r(c,288,98,2,58,'#354658');r(c,285,96,8,5,'#f7d68d');if(rain){for(let n=0;n<54;n++){const x=(n*43+(reduced?0:t*24))%W,y=(n*29+(reduced?0:t*80))%H;line(c,[[x,y],[x-2,y+7]],'#cbe3ef',1)}for(const [x,y]of [[33,163],[235,149],[85,136]]){r(c,x,y,30,3,'#a5c4d2');if(!reduced)r(c,x+Math.floor(t*4)%8,y-2,5,1,'#dbe9f0')}}else for(let i=0;i<7;i++){const x=(i*49+(reduced?0:t*17))%350-25,y=120+(i%3)*16;r(c,x,y,24,9,i%2?'#b8707d':'#7cb1cf');r(c,x+5,y-4,12,5,'#adc9d7');r(c,x+3,y+8,4,4,'#25313e');r(c,x+17,y+8,4,4,'#25313e')}}
function campus(c,t,reduced){r(c,0,0,W,H,'#15203c');for(let i=0;i<25;i++){const x=(i*53+13)%W,y=(i*19+4)%48;r(c,x,y,2,2,!reduced&&Math.sin(t*2+i)>.6?'#ffdf92':'#b7c8e6')}r(c,270,12,17,17,'#f0e4c0');r(c,276,8,16,17,'#15203c');r(c,0,51,320,39,'#8b8585');r(c,0,55,320,26,'#24344e');for(let x=8;x<320;x+=21){r(c,x,56,4,28,'#aaa195');if(x%3)r(c,x+6,61,12,14,'#526481')}r(c,0,85,320,7,'#bab2a2');for(const x of [183,255,307])r(c,x,90,5,64,'#98978f');r(c,0,98,167,57,'#3c404b');r(c,0,97,161,19,'#ab967a');for(let x=7;x<160;x+=20)r(c,x,101,10,11,'#283b56');line(c,[[0,85],[161,105],[181,92]],'#b6ab94',7);r(c,0,154,320,26,'#505b6d');r(c,205,148,115,9,'#4a6253');r(c,225,128,3,26,'#8c7d6b');r(c,214,112,24,22,'#365646')}
function lake(c,t,reduced){r(c,0,0,W,H,'#83b8d6');r(c,258,17,20,20,'#ffe19a');for(let i=0;i<8;i++){const a=i*Math.PI/4+(reduced?0:t*.15);line(c,[[268+15*Math.cos(a),27+15*Math.sin(a)],[268+20*Math.cos(a),27+20*Math.sin(a)]],'#ffe19a',2)}r(c,0,81,320,63,'#5689a8');for(let x=0;x<W;x+=13){r(c,x,72-(x%17),14,18,'#507755');r(c,x+4,80,5,4,'#776c51')}for(let i=0;i<21;i++)r(c,(i*43+(reduced?0:t*8))%320,90+(i*17)%45,15,1,'#9abfcd');r(c,0,144,320,36,'#cfb77d');for(let x=0;x<320;x+=19)r(c,x,148,8,3,'#909d6e');r(c,211,146,4,32,'#805a43');r(c,302,146,4,32,'#805a43');r(c,199,133,118,20,'#564c3e');text(c,'Salbker See',205,147)}
function horse(c,x,y,scale,flip=false){c.save();c.translate(x,y);if(flip)c.scale(-1,1);c.scale(scale,scale);const col='#4b5262';r(c,0,16,28,12,col);r(c,-5,2,12,22,col);r(c,-13,1,12,8,col);r(c,-6,-3,3,7,col);r(c,2,-4,3,9,col);line(c,[[6,25],[0,37],[-8,36]],col,5);line(c,[[20,27],[26,39],[32,39]],col,5);line(c,[[22,17],[30,9],[33,1]],col,4);r(c,8,0,7,17,col);r(c,7,-8,8,8,col);line(c,[[9,8],[-1,9]],col,3);c.restore()}
function windy(c,t,reduced){r(c,0,0,W,H,'#a4bacb');r(c,0,61,320,86,'#c9cccb');for(let y=67;y<136;y+=14)for(let x=6;x<320;x+=13){r(c,x,y,7,8,'#596d84');r(c,x+7,y,2,8,'#e2e2d7')}r(c,0,145,W,35,'#9aa4a0');r(c,73,154,184,23,'#363e51');line(c,[[74,151],[114,118],[187,144],[250,153]],'#505769',7);horse(c,98,72,1.7);horse(c,242,102,1.15,true);r(c,18,106,4,45,'#7a725a');r(c,0,85,44,29,'#5d8665');if(!reduced)for(let i=0;i<9;i++){const x=(i*43+t*46)%340-10,y=27+(i*19)%123;line(c,[[x,y],[x+17,y],[x+22,y-3]],'#e5ecdf',1);r(c,x+8,y+11,4,2,'#8a9c65')}}
export function drawToleranceScene(c,s,time=0,reduced=false){
 c.imageSmoothingEnabled=false;const t=reduced?0:time/1000
 if(s.condition==='crowding')arena(c,t,reduced)
 else if(s.condition==='rain')intersection(c,t,reduced,true)
 else if(s.condition==='darkness')campus(c,t,reduced)
 else if(s.condition==='heat'||s.condition==='cold'||s.condition==='temperature'){
  lake(c,t,reduced)
  if(s.condition==='cold'){
   r(c,0,0,W,62,'#c2d2e4');r(c,258,17,20,20,'#eef4f9');r(c,0,144,W,36,'#dde8f1');r(c,5,146,115,3,'#fbfcff');r(c,199,133,118,20,'#564c3e');text(c,'Salbker See',205,147)
   for(let i=0;i<38;i++){const x=(i*37+(reduced?0:t*8))%W,y=(i*23+(reduced?0:t*23))%H;r(c,x,y,2,2,'#fff')}
  }else if(!reduced){for(let i=0;i<4;i++){const x=50+i*63+Math.sin(t*2+i)*3;line(c,[[x,84],[x+3,75],[x,66]],'#f4d7a4',1)}}
 }
 else if(s.condition==='wind')windy(c,t,reduced)
 else intersection(c,t,reduced,false)
 if(s.condition==='cold')snowman(c,s,t,reduced);else avatar(c,s,t,reduced)
}
