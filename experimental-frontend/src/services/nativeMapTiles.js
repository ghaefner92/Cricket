import {apiFetch} from './apiTransport.js'

// Leaflet keeps its map interactions; image bytes travel over the backend USB link.
export function nativeMapTiles(L,options) {
 const BackendTiles=L.TileLayer.extend({
  createTile(coords,done) {
   const tile=document.createElement('img')
   tile.alt=''
   tile.setAttribute('role','presentation')
   tile.onload=()=>done(null,tile)
   tile.onerror=()=>done(new Error('map-tile'),tile)
   apiFetch(`/api/dyconet/map-tiles/${coords.z}/${coords.x}/${coords.y}`)
    .then(async response=>{
     if(!response.ok)throw Error('map-tile')
     const data=await response.json()
     if(!data.data_url?.startsWith('data:image/png;base64,'))throw Error('map-tile')
     tile.src=data.data_url
    }).catch(error=>done(error,tile))
   return tile
  }
 })
 return new BackendTiles('',{...options,keepBuffer:0,updateWhenIdle:true})
}
