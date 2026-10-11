// Build without publishing or modifying app source. Record resolved build inputs.
import {build} from '../experimental-frontend/node_modules/vite/dist/node/index.js'
import {writeFileSync,mkdirSync} from 'node:fs'
import {resolve,relative,dirname} from 'node:path'
import {fileURLToPath} from 'node:url'

const repo=resolve(dirname(fileURLToPath(import.meta.url)),'..')
const root=resolve(repo,'experimental-frontend')
const reportDir=resolve(repo,'docs/vercel-audit')
mkdirSync(reportDir,{recursive:true})
let modules=[],outputs=[]
await build({root,configFile:resolve(root,'vite.config.js'),build:{write:false},plugins:[{
 name:'deployment-audit',
 generateBundle(_options,bundle){
  modules=[...this.getModuleIds()].filter(id=>id.replaceAll('\\','/').startsWith(root.replaceAll('\\','/'))&&!id.replaceAll('\\','/').includes('/node_modules/'))
   .map(id=>relative(repo,id.split('?')[0]).replaceAll('\\','/'))
  outputs=Object.values(bundle).map(item=>({file:item.fileName,type:item.type,
   bytes:item.type==='chunk'?Buffer.byteLength(item.code):Buffer.byteLength(item.source)}))
 }
}]})
const report={generated_at:new Date().toISOString(),method:'Vite/Rollup resolved build module graph; public assets require separate inclusion',
 resolved_frontend_inputs:[...new Set(modules)].sort(),bundle_outputs:outputs}
writeFileSync(resolve(reportDir,'frontend-build-graph.json'),JSON.stringify(report,null,2)+'\n')
console.log(JSON.stringify({resolved_inputs:report.resolved_frontend_inputs.length,bundle_outputs:outputs.length,
 bundle_bytes:outputs.reduce((n,o)=>n+o.bytes,0)}))
