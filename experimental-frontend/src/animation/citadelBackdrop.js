function rect(ctx,x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h))}
export function drawCitadelBackdrop(ctx,time=0,reduced=false){
 rect(ctx,0,0,320,180,'#79a8ca')
 for(const [x,y]of [[72,18],[272,26]]){rect(ctx,x,y,24,4,'#e5f1ec');rect(ctx,x+6,y-4,12,4,'#e5f1ec')}
 const phase=reduced?0:Math.floor(time/800)%2
 rect(ctx,18,16,14,14,'#ffd675');rect(ctx,21,13,8,20,'#ffd675');rect(ctx,15,19,20,8,'#ffd675')
 for(const [x,y,w,h]of phase?[[9,9,3,3],[37,9,3,3],[9,35,3,3],[37,35,3,3]]:[[23,5,3,5],[23,36,3,5],[6,22,5,3],[38,22,5,3]])rect(ctx,x,y,w,h,'#fff0a6')
 rect(ctx,20,20,2,2,'#886334');rect(ctx,27,20,2,2,'#886334');rect(ctx,22,25,5,2,'#a47839')
 // Original pink stepped architecture inspired by the supplied photo.
 const buildings=[[43,58,45,91],[84,73,38,76],[113,42,43,107],[153,33,43,116],[193,21,45,128],[236,55,35,94],[268,71,42,78]]
 for(const [x,y,w,h]of buildings){
  rect(ctx,x,y,w,h,'#cd8d9b');rect(ctx,x+3,y+3,w-6,h-3,'#e1a1ac');rect(ctx,x,y,w,3,'#424e53')
  for(let band=x+5;band<x+w;band+=22){for(let py=y+4;py<147;py+=8)rect(ctx,band+(Math.floor(py/16)%2)*2,py,5,8,'#aab1a5')}
  for(let wy=y+15;wy<135;wy+=27)for(let wx=x+11;wx<x+w-8;wx+=20){
   rect(ctx,wx,wy,13,15,'#293f4f');rect(ctx,wx+2,wy+2,9,11,'#74a6bd');rect(ctx,wx+6,wy+2,1,11,'#263b4d');rect(ctx,wx+2,wy+7,9,1,'#263b4d')
  }
  rect(ctx,x+w/2-5,y-6,10,7,'#535a50');rect(ctx,x+w/2-3,y-9,6,6,'#d5be78')
  rect(ctx,x+10,134,18,15,'#293f42')
 }
 rect(ctx,43,58,11,67,'#253e4b');for(let y=62;y<121;y+=13)rect(ctx,45,y,7,10,'#659db4')
 rect(ctx,199,91,36,23,'#293f4f');rect(ctx,202,94,30,17,'#7eafc0');rect(ctx,214,94,2,17,'#293f4f')
 for(const x of [30,98,252,315]){rect(ctx,x,125,3,24,'#735647');rect(ctx,x-9,111,22,20,'#456f55');rect(ctx,x-6,103,15,14,'#608760')}
 rect(ctx,0,149,320,31,'#bdb7ad');rect(ctx,0,170,320,2,'#eee5d6')
}
