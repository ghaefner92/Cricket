import {spawnSync} from 'node:child_process'
import {existsSync} from 'node:fs'
import {resolve,dirname} from 'node:path'
import {fileURLToPath} from 'node:url'

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..')
const env={...process.env}
// Prefer the JDK bundled with Android Studio over an older system JDK.
const candidates=[env.CRICKET_JAVA_HOME,
 'D:/Android Studio/jbr', 'C:/Program Files/Android/Android Studio/jbr',env.JAVA_HOME].filter(Boolean)
const javaHome=candidates.find(path=>existsSync(resolve(path,'bin',process.platform==='win32'?'java.exe':'java')))
if(javaHome){
 env.JAVA_HOME=javaHome
 const currentPath=env.PATH||env.Path||''
 delete env.Path
 env.PATH=resolve(javaHome,'bin')+(process.platform==='win32'?';':':')+currentPath
}
const sdk=env.ANDROID_HOME||env.ANDROID_SDK_ROOT||
 (env.LOCALAPPDATA?resolve(env.LOCALAPPDATA,'Android','Sdk'):undefined)
if(sdk&&existsSync(sdk))env.ANDROID_HOME=sdk
const windows=process.platform==='win32'
const result=spawnSync(windows?(env.ComSpec||'C:/Windows/System32/cmd.exe'):'./gradlew',windows?['/d','/c','gradlew.bat assembleDebug --console=plain']:['assembleDebug','--console=plain'],
 {cwd:resolve(root,'android'),env,stdio:'inherit'})
if(result.error)console.error(result.error.message)
process.exit(result.status??1)
