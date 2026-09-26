/** V07 changes only the isolated cable to the connected cable's layered center. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';import {BlockbenchMcp} from './mcp_client.mjs';
const base=path.resolve('tools/blockbench/versions/v06-dense-panels');
const work=path.resolve('tools/blockbench/.local/v07-work');
for(const n of ['models','renders'])fs.mkdirSync(path.join(work,n),{recursive:true});
fs.cpSync(path.join(base,'textures'),path.join(work,'textures'),{recursive:true});
const names=['router','pattern_provider','processing_endpoint','bridge','cable_isolated','cable_end','cable_straight_x','cable_straight_y','cable_straight_z','cable_corner','cable_tee','cable_cross','cable_six_way'];
for(const n of names)fs.copyFileSync(path.join(base,'models',n+'.bbmodel'),path.join(work,'models',n+'.bbmodel'));
const read=n=>JSON.parse(fs.readFileSync(path.join(base,'models',n+'.bbmodel')));
const m=read('cable_end');m.name='cable_isolated';m.groups=[];
m.elements=m.elements.filter(e=>e.name==='stream_000'||e.name==='glass_000');
if(m.elements.length!==2)throw new Error('Expected one center per layer');
for(const e of m.elements){
 e.faces.east=structuredClone(e.faces.west);
 e.faces.east.uv=e.name.startsWith('stream')?[6,6,10,10]:[5,5,11,11];
}
const used=[...new Set(m.elements.flatMap(e=>Object.values(e.faces).map(f=>f.texture)))];
const oldTextures=m.textures;m.textures=used.map((i,j)=>({...oldTextures[i],id:String(j)}));
for(const e of m.elements)for(const f of Object.values(e.faces))f.texture=used.indexOf(f.texture);
m.outliner=m.elements.map(e=>e.uuid);
const dest=path.join(work,'models/cable_isolated.bbmodel');
const literal=v=>JSON.stringify(v).replaceAll('/','\\u002f');
const c=new BlockbenchMcp();await c.init();
try{
 await c.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${literal(m)},{path:${literal(dest)}});return true;})()`});
 await new Promise(r=>setTimeout(r,150));
 await c.call('export_model',{codec_id:'project',path:dest,max_content_length:0});
}finally{await c.close();}
console.log('Exported layered isolated cable from actual Blockbench.');
