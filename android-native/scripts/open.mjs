import {spawn} from 'node:child_process'
import {resolve,dirname} from 'node:path'
import {fileURLToPath} from 'node:url'
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..')
const studio=process.env.CRICKET_ANDROID_STUDIO||'D:/Android Studio/bin/studio64.exe'
const child=spawn(studio,[root],{detached:true,stdio:'ignore',windowsHide:true})
child.on('error',error=>{console.error(error.message);process.exitCode=1})
child.unref()
