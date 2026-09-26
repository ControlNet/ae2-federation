/** Read-only V07 editor verification; evidence never overwrites archived assets. */
import fs from 'node:fs';import path from 'node:path';import {BlockbenchMcp} from './mcp_client.mjs';
const folder=path.resolve(process.argv[2]??'tools/blockbench/.local/v07-work');const client=new BlockbenchMcp();await client.init();
const evidence=path.resolve('tools/blockbench/.local/verification/v07-editor');fs.mkdirSync(evidence,{recursive:true});
const lit=v=>JSON.stringify(v).replaceAll('/', '\\u002f');
const value=r=>r.structuredContent??JSON.parse(r.content.find(c=>c.type==='text').text);
const results=[];
try{
 const files=fs.readdirSync(path.join(folder,'models')).filter(f=>f.endsWith('.bbmodel')).sort((a,b)=>Number(a==='v07_family.bbmodel')-Number(b==='v07_family.bbmodel'));
 for(const file of files){
  const filename=path.join(folder,'models',file),model=JSON.parse(fs.readFileSync(filename));
  await client.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${lit(model)},{path:${lit(filename)}});return true;})()`});
  await new Promise(r=>setTimeout(r,100));
  const check=value(await client.call('risky_eval',{code:`({cubes:Cube.all.length,textures:Texture.all.length,errors:Texture.all.filter(t=>t.error).map(t=>t.name),missing:Cube.all.flatMap(c=>Object.values(c.faces)).filter(f=>f.texture!==null&&!Texture.all.some(t=>t.uuid===f.texture)).length})`}));
  if(check.errors.length||check.missing)throw new Error(JSON.stringify(check));
  results.push({file,...check});
 }
 const image=await client.call('capture_app_screenshot');const block=image.content.find(c=>c.type==='image');fs.writeFileSync(path.join(evidence,'editor.png'),Buffer.from(block.data,'base64'));
 fs.writeFileSync(path.join(evidence,'editor-validation.json'),JSON.stringify({editor:'Blockbench 5.2.1',transport:'MCP',models:results,game_tested:false},null,2)+'\n');
 console.log('All '+results.length+' candidate projects loaded in Blockbench without missing textures.');
}finally{await client.close();}
