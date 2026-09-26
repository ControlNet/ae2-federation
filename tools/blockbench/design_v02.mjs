/** V02 art study: author pixels through Blockbench MCP, then save native projects. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {BlockbenchMcp} from './mcp_client.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const base=path.join(root,'tools/blockbench/versions/v01-baseline/models');
const work=path.join(root,'tools/blockbench/.local/v02-work');
for(const sub of ['models','textures','renders'])fs.mkdirSync(path.join(work,sub),{recursive:true});
const P={shell:'#d9dfdb',light:'#f0f1e6',plate:'#e4e8de',shade:'#b1beb9',seam:'#839794',steel:'#5c7275',dark:'#344950',black:'#20333e',deep:'#315860',teal:'#497f86',cyan:'#62b7ba',ice:'#b2e5da',purple:'#9a88bb',violet:'#655d81',plum:'#454761',lilac:'#d0c0df'};
function tile(bg=P.shell){return Array.from({length:16},()=>Array(16).fill(bg));}
function rect(t,x,y,w,h,c){for(let v=y;v<y+h;v++)for(let u=x;u<x+w;u++)if(t[v]?.[u]!==undefined)t[v][u]=P[c]??c;return t;}
function shell(){let t=tile();rect(t,0,0,16,1,'shade');rect(t,1,1,14,1,'light');rect(t,0,1,1,14,'shade');rect(t,15,1,1,14,'seam');rect(t,1,15,14,1,'seam');rect(t,1,14,14,1,'shade');rect(t,2,2,12,11,'plate');
 for(const [x,y] of [[1,1],[13,1],[1,12],[13,12]]){rect(t,x,y,2,3,'shade');rect(t,x,y,2,1,'seam');rect(t,x,y+1,1,1,'light');}
 rect(t,0,7,3,1,'seam');rect(t,13,7,3,1,'seam');rect(t,3,2,4,1,'light');rect(t,9,13,4,1,'shade');return t;}
function pocket(t,x,y,w,h){rect(t,x,y,w,h,'dark');rect(t,x+1,y+1,w-2,h-2,'black');rect(t,x,y,w,1,'seam');rect(t,x,y+h,w,1,'light');}
const tiles={};
let t=shell();pocket(t,5,5,6,6);rect(t,6,6,4,4,'teal');rect(t,7,6,2,4,'cyan');rect(t,6,7,4,2,'cyan');rect(t,7,7,2,2,'ice');
for(const [x,y,w,h] of [[7,2,2,3],[7,11,2,3],[2,7,3,2],[11,7,3,2]]){rect(t,x,y,w,h,'steel');rect(t,x,y,w===2?1:w,h===2?1:h,'cyan');}
tiles.router=t;
t=shell();
for(const [x,y] of [[4,3],[5,6],[4,9]]){rect(t,x-1,y,9,3,'dark');rect(t,x,y,7,1,'light');rect(t,x,y+1,6,1,'cyan');rect(t,x,y+2,6,1,'deep');rect(t,x,y+1,1,1,'ice');rect(t,x+6,y+1,1,2,'steel');}
rect(t,12,4,1,8,'teal');for(const y of [4,7,10])rect(t,11,y,2,1,'cyan');rect(t,4,12,7,1,'shade');tiles.pattern_provider=t;
t=shell();pocket(t,3,5,10,6);rect(t,4,6,8,4,'deep');rect(t,5,6,6,1,'teal');rect(t,5,7,6,2,'cyan');rect(t,6,7,4,1,'ice');rect(t,6,9,4,1,'teal');rect(t,3,7,2,1,'light');rect(t,11,8,2,1,'light');rect(t,6,3,4,1,'steel');rect(t,7,12,2,1,'cyan');tiles.processing_endpoint=t;
t=shell();pocket(t,5,4,6,8);rect(t,6,5,4,6,'plum');rect(t,7,5,2,6,'violet');rect(t,6,7,4,2,'purple');rect(t,7,6,2,4,'purple');rect(t,7,7,2,2,'lilac');rect(t,3,6,1,4,'steel');rect(t,12,6,1,4,'steel');tiles.me_port=t;
// A compact ME contact on peripheral faces; cyan remains toward the real front.
t=shell();pocket(t,4,5,6,6);rect(t,5,6,4,4,'plum');rect(t,6,6,2,4,'purple');rect(t,5,7,4,2,'purple');rect(t,6,7,2,2,'lilac');rect(t,2,7,2,2,'violet');rect(t,10,7,3,2,'steel');rect(t,11,7,2,1,'cyan');rect(t,13,5,1,5,'shade');rect(t,5,3,4,1,'shade');rect(t,6,12,3,1,'shade');
tiles.me_side_west=t;tiles.me_side_east=t.map(row=>[...row].reverse());
const rotateCW=a=>a[0].map((_,x)=>a.map(row=>row[x]).reverse());
tiles.me_side_up=rotateCW(t);tiles.me_side_down=rotateCW(rotateCW(rotateCW(t)));
// Contact seats and collars use world-aligned UVs, so their pixels stay one model unit wide.
t=tile(P.shade);rect(t,0,0,16,1,'light');rect(t,0,1,1,15,'plate');rect(t,0,15,16,1,'seam');rect(t,15,0,1,16,'steel');rect(t,4,4,8,1,'plate');rect(t,4,5,1,7,'plate');rect(t,5,11,7,1,'seam');rect(t,11,5,1,6,'seam');rect(t,5,5,6,6,'shell');tiles.armor=t;
t=tiles.armor.map(r=>[...r]);rect(t,7,4,2,1,'steel');rect(t,7,11,2,1,'steel');rect(t,4,7,1,2,'teal');rect(t,11,7,1,2,'teal');tiles.collar=t;
t=tile(P.violet);rect(t,6,6,4,4,'plum');rect(t,7,6,2,4,'purple');rect(t,6,7,4,2,'purple');rect(t,7,7,2,2,'lilac');tiles.me_context=t;
t=tile(P.steel);rect(t,4,4,8,8,'shade');rect(t,5,5,6,6,'plate');rect(t,6,6,4,4,'plum');rect(t,7,6,2,4,'purple');rect(t,6,7,4,2,'purple');rect(t,7,7,2,2,'lilac');tiles.bridge_end=t;
for(const name of ['bridge_east','bridge_west','bridge_up','bridge_down']){
 t=tile(P.steel);let patch=Array.from({length:8},()=>Array(4).fill(P.shell));
 rect(patch,0,0,4,1,'light');rect(patch,0,7,4,1,'seam');rect(patch,0,1,1,6,'shade');rect(patch,3,1,1,6,'shade');rect(patch,1,2,2,4,'deep');rect(patch,1,3,2,2,'cyan');rect(patch,1,3,2,1,'ice');
 if(name.endsWith('east')||name.endsWith('west')){let x=name.endsWith('east')?1:11;for(let y=0;y<8;y++)for(let u=0;u<4;u++)t[y+4][u+x]=patch[y][u];}
 else {let y=name.endsWith('up')?11:1;for(let v=0;v<4;v++)for(let x=0;x<8;x++)t[v+y][x+4]=patch[x][v];}
 tiles[name]=t;
}
t=tile(P.shade);rect(t,0,0,16,2,'plate');rect(t,0,2,2,12,'shell');rect(t,14,2,2,14,'seam');rect(t,2,14,12,2,'seam');pocket(t,4,4,8,8);rect(t,5,5,6,6,'deep');rect(t,6,6,4,4,'cyan');rect(t,7,7,2,2,'ice');tiles.cable_idle=t;
const client=new BlockbenchMcp();await client.init();
const literal=v=>JSON.stringify(v).replaceAll('/', '\\u002f');
const data=r=>r.structuredContent??JSON.parse(r.content.find(c=>c.type==='text').text);
async function evaluate(code){return data(await client.call('risky_eval',{code}));}
const overview=JSON.parse(fs.readFileSync(path.join(base,'ae2_federation_overview.bbmodel')));
await evaluate(`(()=>{Codecs.project.load(${literal(overview)},{path:${literal(path.join(work,'models/authoring.bbmodel'))}});return true;})()`);
await new Promise(r=>setTimeout(r,250));
try{
 await client.call('save_checkpoint',{name:'V02 cold ceramic material study'});
 for(const [name,grid] of Object.entries(tiles)){
  const colors=new Map();grid.forEach((row,y)=>row.forEach((color,x)=>{if(!colors.has(color))colors.set(color,[]);colors.get(color).push({x,y});}));
  for(const [color,coordinates] of colors)await client.call('paint_with_brush',{texture_id:name+'.png',coordinates,brush_settings:{size:1,opacity:255,softness:0,shape:'square',color},connect_strokes:false});
  console.log('Painted in Blockbench:',name);
 }
 // Animate one continuous volume. Texture.edit is Blockbench's native bitmap
 // edit path; all frames are authored in its canvas and participate in Undo.
 await evaluate(`(()=>{const targets=Texture.all.filter(t=>t.name.startsWith('stream_'));Undo.initEdit({textures:targets,bitmap:true});for(const t of targets){t.edit(canvas=>{const c=canvas.getContext('2d');for(let frame=0;frame<16;frame++)for(let y=0;y<16;y++)for(let x=0;x<16;x++){const axis=t.name.includes('_u')?x:y;const cross=t.name.includes('_u')?y:x;const band=(axis-frame+16)%16;let colors=['#34535d','#416e77','#57969c','#68b7b8','#94d3cd','#c2e5dc'];let level=cross===7||cross===8?2:1;if(band===0)level+=3;else if(band===1)level+=2;else if(band===2)level++;c.fillStyle=colors[Math.min(level,5)];c.fillRect(x,y+frame*16,1,1);}},{no_undo:true});}Undo.finishEdit('V02 restrained cable pulse');return true;})()`);
 const painted=await evaluate(`Texture.all.map(t=>({name:t.name,source:t.getDataURL()}))`);
 const sources=new Map(painted.map(t=>[t.name,t.source]));
 for(const [name,source] of sources)fs.writeFileSync(path.join(work,'textures',name),Buffer.from(source.split(',')[1],'base64'));
 fs.writeFileSync(path.join(work,'pixel-plan.json'),JSON.stringify({palette:P,tiles},null,2)+'\n');
 for(const file of fs.readdirSync(base).filter(f=>f.endsWith('.bbmodel')&&!f.includes('overview'))){
  const model=JSON.parse(fs.readFileSync(path.join(base,file)));
  for(const t of model.textures)t.source=sources.get(t.name)??t.source;
  model.name='v02_'+file.replace('.bbmodel','');
  const dest=path.join(work,'models',file);
  await evaluate(`(()=>{Project.saved=true;Codecs.project.load(${literal(model)},{path:${literal(dest)}});return true;})()`);
  await new Promise(r=>setTimeout(r,60));
  await client.call('export_model',{codec_id:'project',path:dest,max_content_length:0});
 }
 console.log('Saved candidate projects:',work);
}finally{await client.close();}
