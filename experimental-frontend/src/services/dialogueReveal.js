// Reveal only completed, verified text. Replacement/unmount cancels older timers.
export function createDialogueReveal(onChange,schedule=setTimeout,cancel=clearTimeout){
 let timer=null,count=0,total=0,generation=0
 function stop(){generation++;if(timer!==null)cancel(timer);timer=null}
 function showAll(){stop();count=total;onChange(count)}
 function start(text,animate=true){
  stop();total=Array.from(text).length;count=animate?0:total;onChange(count)
  if(!animate||!total)return
  const version=generation
  function tick(){if(version!==generation)return;count=Math.min(total,count+8);onChange(count);timer=count<total?schedule(tick,25):null}
  timer=schedule(tick,25)
 }
 return {start,showAll,dispose:stop}
}
