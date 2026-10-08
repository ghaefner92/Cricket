import {CapacitorHttp} from '@capacitor/core'
import {isNative} from './nativePlatform.js'

const KEY='cricket.connection.v1'
const DEFAULT=import.meta.env.VITE_BACKEND_BASE_URL||'http://localhost:8077'
export function normalizeBackend(value){
 const url=new URL(String(value).trim())
 if(!['http:','https:'].includes(url.protocol)||url.username||url.password||url.search||url.hash)throw Error('server')
 return url.href.replace(/\/+$/,'')
}
export function backendBase(){
 const saved=localStorage.getItem(KEY)
 try{return normalizeBackend(saved||DEFAULT)}catch{return normalizeBackend(DEFAULT)}
}
export function saveBackend(value){const base=normalizeBackend(value);localStorage.setItem(KEY,base);return base}

// Native requests use the same JSON endpoints as the Vite proxy, without CORS.
// Cancellation discards the response; the underlying native request can finish.
export async function apiFetch(path,options={}){
 if(!isNative())return fetch(path,options)
 const signal=options.signal
 if(signal?.aborted)throw new DOMException('Aborted','AbortError')
 const url=path.startsWith('/api/dyconet')
  ?backendBase()+(path==='/api/dyconet/health'?'/health':path)
  :new URL(path).href
 const headers=Object.fromEntries(new Headers(options.headers).entries())
 const data=options.body===undefined?undefined:JSON.parse(options.body)
 return new Promise((resolve,reject)=>{
  const abort=()=>reject(new DOMException('Aborted','AbortError'))
  signal?.addEventListener('abort',abort,{once:true})
  if(signal?.aborted){signal.removeEventListener('abort',abort);abort();return}
  CapacitorHttp.request({url,method:options.method||'GET',headers,data,
   connectTimeout:15000,readTimeout:180000,responseType:'json',disableRedirects:true})
   .then(result=>{
    if(signal?.aborted)return
    const body=[204,205,304].includes(result.status)?null:
     typeof result.data==='string'?result.data:JSON.stringify(result.data)
    resolve(new Response(body,{status:result.status,headers:result.headers}))
   }).catch(reject).finally(()=>signal?.removeEventListener('abort',abort))
 })
}
