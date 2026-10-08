import {ref,onMounted,onUnmounted} from 'vue'

export function useCompactLayout(){
 const query=window.matchMedia('(max-width: 700px)')
 const compact=ref(query.matches)
 const update=()=>{compact.value=query.matches}
 onMounted(()=>query.addEventListener('change',update))
 onUnmounted(()=>query.removeEventListener('change',update))
 return compact
}
