/** Small Streamable HTTP client for diagnostics when tools are not hot-reloaded. */
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
export class BlockbenchMcp {
  constructor(){ this.url='http://127.0.0.1:31337/bb-mcp';this.nextId=1;this.session=null; }
  async rpc(method, params={}, notification=false){
    const id=notification?undefined:this.nextId++;
    const headers={'Content-Type':'application/json','Accept':'application/json, text/event-stream'};
    if(this.session)headers['Mcp-Session-Id']=this.session;
    if(this.protocol)headers['MCP-Protocol-Version']=this.protocol;
    const response=await fetch(this.url,{method:'POST',headers,body:JSON.stringify({jsonrpc:'2.0',id,method,params}),signal:AbortSignal.timeout(120000)});
    if(response.headers.get('mcp-session-id'))this.session=response.headers.get('mcp-session-id');
    const body=await response.text();
    if(!response.ok)throw new Error(`HTTP ${response.status}: ${body.slice(0,1000)}`);
    if(notification)return;
    const messages=response.headers.get('content-type')?.includes('text/event-stream')
      ?body.split(/\r?\n/).filter(line=>line.startsWith('data:')).map(line=>JSON.parse(line.slice(5)))
      :[JSON.parse(body)];
    const message=messages.find(message=>message.id===id);
    if(!message)throw new Error(`No response for ${method}`);
    if(message.error)throw new Error(JSON.stringify(message.error));
    return message.result;
  }
  async init(){
    const result=await this.rpc('initialize',{protocolVersion:'2025-03-26',capabilities:{},clientInfo:{name:'ae2-federation-art-tools',version:'1.0.0'}});
    this.protocol=result.protocolVersion;
    await this.rpc('notifications/initialized',{},true);return result;
  }
  async call(name,args={}){
    const result=await this.rpc('tools/call',{name,arguments:args});
    if(result.isError)throw new Error(JSON.stringify(result));
    return result;
  }
  async close(){
    if(this.session)await fetch(this.url,{method:'DELETE',headers:{'Mcp-Session-Id':this.session,'MCP-Protocol-Version':this.protocol},signal:AbortSignal.timeout(5000)});
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const client=new BlockbenchMcp();await client.init();
  try{
    const result=process.argv[2]==='list'?await client.rpc('tools/list'):await client.call(process.argv[2],process.argv[3]?JSON.parse(fs.readFileSync(process.argv[3],'utf8')):{});
    console.log(JSON.stringify(result,null,2));
  }finally{await client.close();}
}
