import {Capacitor} from '@capacitor/core'
export const isNative=()=>Capacitor.isNativePlatform()

export function currentPosition(success,failure,options){
 if(!isNative())return navigator.geolocation.getCurrentPosition(success,failure,options)
 import('@capacitor/geolocation').then(async({Geolocation})=>{
  const permission=await Geolocation.requestPermissions({permissions:['location']})
  if(permission.location!=='granted'&&permission.coarseLocation!=='granted'){
   failure({code:1});return
  }
  success(await Geolocation.getCurrentPosition(options))
 }).catch(error=>failure({code:error?.code==='OS-PLUG-GLOC-0003'?1:error?.code==='OS-PLUG-GLOC-0010'?3:2}))
}

export async function initializeNative(){
 if(!isNative())return
 document.documentElement.classList.add('cricket-native')
 const {App}=await import('@capacitor/app')
 await App.addListener('backButton',async({canGoBack})=>{
  const dialog=[...document.querySelectorAll('dialog[open]')].at(-1)
  if(dialog){
   const event=new Event('cancel',{cancelable:true})
   if(dialog.dispatchEvent(event))dialog.close()
  }else if(canGoBack)window.history.back()
  else await App.minimizeApp()
 })
}
