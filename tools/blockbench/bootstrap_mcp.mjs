/** Register the pinned local plugin in the isolated project profile via CDP. */
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const pages = await (await fetch('http://127.0.0.1:9339/json/list')).json();
const page = pages.find(p => p.type === 'page');
if (!page) throw new Error('Start the project launcher with --remote-debugging-port=9339 first');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, {once:true}));
const expression = `(async () => {
  if (SystemInfo.user_data_directory !== ${JSON.stringify(path.join(root,'tools/blockbench/.local/profile'))}) {
    throw new Error('Refusing to install outside the project profile');
  }
  if (Plugins.all.some(p => p.id === 'mcp' && p.installed)) {
    return {installed: true, existing: true, port: Settings.get('mcp_port')};
  }
  Settings.stored.mcp_port = {value:31337};
  Settings.stored.mcp_prompt_cdn_enabled = {value:false};
  Settings.stored.mcp_ai_scratchpad_enabled = {value:false};
  await new Plugin().loadFromFile({path:${JSON.stringify(path.join(root,'tools/blockbench/.local/mcp.js'))},name:'mcp.js',content:''},false);
  Settings.save();
  return {installed: Plugins.all.some(p => p.id === 'mcp' && p.installed), port:Settings.get('mcp_port')};
})()`;
const timer = setTimeout(() => { ws.close(); throw new Error('Bootstrap timed out'); }, 30000);
const result = await new Promise(resolve => {
  ws.addEventListener('message', event => {const r=JSON.parse(event.data); if(r.id===1)resolve(r);});
  ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));
});
clearTimeout(timer); ws.close();
if(result.error || result.result?.exceptionDetails) throw new Error(JSON.stringify(result));
console.log(JSON.stringify(result.result.result.value,null,2));
