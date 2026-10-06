// Original code-drawn pixel interpretation of the supplied Magdeburg Hbf reference.
export function drawHbfBackdrop(ctx,time=0,reduced=false){
 const r=(x,y,w,h,c)=>{ctx.fillStyle=c;ctx.fillRect(Math.round(x),Math.round(y),w,h)}
 r(0,0,320,180,'#8bb8db')
 for(const [base,y] of [[25,17],[170,10],[275,30]]){const x=reduced?base:((base+time*.004)%370)-25;r(x,y,30,5,'#eef4ef');r(x+7,y-5,15,5,'#eef4ef')}
 for(let i=0;i<3;i++){const x=reduced?45+i*65:((45+i*65+time*.012)%350)-15;const y=30+i*7;const flap=reduced?0:Math.floor(time/220+i)%2*2;r(x,y,2,2,'#344b67');r(x-3,y-flap,3,1,'#344b67');r(x+2,y-flap,3,1,'#344b67')}
 r(0,76,320,74,'#b9a582');r(0,72,320,5,'#e0cba5');r(0,86,320,3,'#8a795f')
 for(const x of [77,200]){r(x,60,43,90,'#cbb68e');r(x-3,57,49,5,'#dfcda8');r(x,65,43,3,'#8a795f')}
 r(119,68,81,82,'#cbb68e');r(117,63,85,5,'#e3d0ab')
 // Clock, central arch and glazed entrance.
 r(149,52,22,15,'#aa9675');r(153,47,14,5,'#aa9675');r(155,44,10,3,'#aa9675');r(154,54,12,12,'#eee3c8');r(159,56,1,5,'#46525b');r(159,60,5,1,'#46525b')
 r(143,99,34,51,'#284c60');r(148,91,24,8,'#284c60');r(154,87,12,4,'#284c60');r(138,103,4,47,'#e1cda9');r(178,103,4,47,'#e1cda9')
 for(const y of [109,126,143])r(143,y,34,1,'#658393');r(159,94,2,56,'#658393')
 for(const x of [10,31,52,91,211,258,279,300]){r(x,94,9,18,'#384d58');r(x,126,9,19,'#384d58');r(x-2,112,13,2,'#ddcaa8')}
 for(const y of [80,119,147])r(0,y,320,2,'#9d8a6d')
 r(0,150,320,30,'#b7b6ad');r(0,170,320,1,'#979b98');for(const x of [25,85,145,205,265])r(x,153,1,27,'#a4a69f')
}
