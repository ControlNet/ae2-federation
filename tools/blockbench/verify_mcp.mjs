/** Real MCP integration check. Only an isolated disposable Provider copy is edited. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {BlockbenchMcp} from './mcp_client.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const source=path.join(root,'tools/blockbench/projects/pattern_provider.bbmodel');
const evidence=path.join(root,'tools/blockbench/.local/verification');fs.mkdirSync(evidence,{recursive:true});
const bytes=fs.readFileSync(source);const sourceHash=crypto.createHash('sha256').update(bytes).digest('hex');
const model=JSON.parse(bytes);model.name='mcp_provider_verification';
const copyPath=path.join(evidence,'provider-copy.bbmodel');fs.writeFileSync(copyPath,JSON.stringify(model,null,2));
const client=new BlockbenchMcp();const init=await client.init();
const literal=value=>JSON.stringify(value).replaceAll('/', '\\u002f');
const data=r=>r.structuredContent??JSON.parse(r.content.find(x=>x.type==='text').text);
async function evaluate(code){return data(await client.call('risky_eval',{code}));}
async function readState(){return evaluate(`({cubes:Cube.all.map(c=>c.getSaveCopy()),textures:Texture.all.map(t=>({name:t.name,source:t.getDataURL()})),java_version:Project.java_block_version})`);}
function saveImage(result,name){const block=result.content.find(c=>c.type==='image');assert.ok(block,'Expected real MCP image');fs.writeFileSync(path.join(evidence,name),Buffer.from(block.data,'base64'));}
try{
 await evaluate(`(()=>{Codecs.project.load(${literal(model)},{path:${JSON.stringify(copyPath)}});return true;})()`);
 await new Promise(resolve=>setTimeout(resolve,300));
 const info=data(await client.call('get_project_info'));assert.equal(info.counts.cubes,1);assert.equal(info.counts.textures,6);
 const before=await readState();assert.equal(before.java_version,'1.9.0');
 await client.call('save_checkpoint',{name:'Before MCP integration verification'});
 const cube=before.cubes[0];
 await client.call('modify_cube',{id:cube.uuid,to:[16,15,16]});
 assert.deepEqual((await readState()).cubes[0].to,[16,15,16]);
 await client.call('undo');assert.deepEqual((await readState()).cubes,before.cubes);
 await client.call('redo');assert.deepEqual((await readState()).cubes[0].to,[16,15,16]);
 await client.call('undo');
 await client.call('paint_with_brush',{texture_id:'pattern_provider.png',coordinates:[{x:0,y:0}],brush_settings:{size:1,opacity:255,softness:0,shape:'square',color:'#ff00ff'},connect_strokes:false});
 assert.notDeepEqual((await readState()).textures,before.textures,'Paint must actually change a texture');
 await client.call('undo');assert.deepEqual((await readState()).textures,before.textures,'Undo must restore texture pixels');
 await client.call('create_offscreen_view',{id:'ae2f_verification',width:960,height:720,antialias:false});
 saveImage(await client.call('set_camera_angle',{view:'ae2f_verification',position:[36,28,40],target:[8,8,8],projection:'perspective'}),'provider-mcp.png');
 await client.call('delete_offscreen_view',{view:'ae2f_verification'});
 const savedPath=path.join(evidence,'provider-roundtrip.bbmodel');
 await client.call('export_model',{codec_id:'project',path:savedPath,max_content_length:0});
 const saved=JSON.parse(fs.readFileSync(savedPath,'utf8'));assert.equal(saved.elements.length,1);assert.equal(saved.textures.length,6);
 await evaluate(`(()=>{Project.saved=true;Codecs.project.load(${literal(saved)},{path:${JSON.stringify(savedPath)}});return true;})()`);
 await new Promise(resolve=>setTimeout(resolve,300));
 const after=await readState();
 assert.deepEqual(after.cubes,before.cubes);assert.deepEqual(after.textures,before.textures);
 assert.equal(crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex'),sourceHash);
 const report={server:init.serverInfo,protocol:init.protocolVersion,source:'tools/blockbench/projects/pattern_provider.bbmodel',source_sha256:sourceHash,checks:{read:true,geometry_edit:true,undo:true,redo:true,paint:true,paint_undo:true,mcp_screenshot:true,save:true,reopen:true,source_unchanged:true},project:info.counts,java_block_version:after.java_version};
 fs.writeFileSync(path.join(evidence,'result.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{await client.close();}
