/** Author V03 block textures inside Blockbench; retain V02 cable and Bridge. */
import fs from 'node:fs';
import path from 'node:path';
import {BlockbenchMcp} from './mcp_client.mjs';
const root=path.resolve(import.meta.dirname,'../..');
const base=path.join(root,'tools/blockbench/versions/v02-ae2-ceramic');
const work=path.join(root,'tools/blockbench/.local/v03-work');
for(const dir of ['models','textures','renders'])fs.mkdirSync(path.join(work,dir),{recursive:true});
const P={white:'#ebedf2',shell:'#cdd0d9',shade:'#a5a9b8',edge:'#777d90',dark:'#414454',black:'#292e40',cyan:'#56b6c8',ice:'#b6e4eb',teal:'#33839e',deep:'#35546c',purple:'#a78aca',lilac:'#d6c4e7',violet:'#78669f',plum:'#50476b'};
const rect=(t,x,y,w,h,c)=>{for(let j=y;j<y+h;j++)for(let i=x;i<x+w;i++)t[j][i]=P[c]??c;};
function shell(){
 const t=Array.from({length:16},()=>Array(16).fill(P.shell));
 rect(t,0,0,16,1,'shade');rect(t,0,1,1,14,'shade');rect(t,15,1,1,15,'edge');rect(t,1,15,14,1,'edge');
 rect(t,1,1,14,1,'white');rect(t,1,2,1,12,'white');rect(t,2,14,12,1,'shade');rect(t,14,2,1,12,'shade');
 // Four short structural breaks, rather than bolts or a nested border.
 for(const [x,y] of [[3,1],[12,1],[1,12],[14,3]])rect(t,x,y,1,2,'shade');
 return t;
}
function inset(t,x,y,w,h){rect(t,x+1,y,w-2,h,'dark');rect(t,x,y+1,w,h-2,'dark');rect(t,x+1,y+1,w-2,h-2,'black');rect(t,x+1,y+h,w-2,1,'white');}
const tiles={};
let t=shell();
// Four short equal paths meet a broad square coupler; every world face is equal.
inset(t,4,4,8,8);rect(t,5,5,6,6,'deep');rect(t,6,5,4,6,'teal');rect(t,5,6,6,4,'teal');rect(t,6,6,4,4,'cyan');rect(t,6,6,4,1,'ice');rect(t,6,7,1,2,'ice');rect(t,7,9,3,1,'teal');
for(const [x,y,w,h] of [[7,2,2,3],[7,11,2,3],[2,7,3,2],[11,7,3,2]])rect(t,x,y,w,h,'dark');
rect(t,7,2,2,2,'cyan');rect(t,7,12,2,2,'cyan');rect(t,2,7,2,2,'cyan');rect(t,12,7,2,2,'cyan');tiles.router=t;
// Three overlapping pattern plates, with a single short distribution rail.
t=shell();inset(t,3,3,10,10);
for(const [x,y,w] of [[4,4,7],[5,7,7],[4,10,7]]){rect(t,x,y,w,2,'teal');rect(t,x,y,w-1,1,'ice');rect(t,x,y+1,w-1,1,'cyan');rect(t,x,y,1,1,'white');}
rect(t,12,5,1,6,'deep');rect(t,11,5,2,1,'cyan');rect(t,12,8,1,1,'cyan');rect(t,11,11,2,1,'cyan');tiles.pattern_provider=t;
// A broad processing aperture and two compact execution marks.
t=shell();inset(t,3,4,10,8);rect(t,4,5,8,6,'deep');rect(t,5,5,6,1,'teal');rect(t,4,6,8,4,'teal');rect(t,5,6,6,3,'cyan');rect(t,5,6,6,1,'ice');rect(t,4,7,1,2,'shade');rect(t,11,7,1,2,'shade');rect(t,6,10,4,1,'black');rect(t,6,10,1,1,'ice');rect(t,9,10,1,1,'ice');tiles.processing_endpoint=t;
// ME faces use a larger Fluix plate, not a tiny decorative contact.
t=shell();inset(t,3,3,10,10);rect(t,4,4,8,8,'plum');rect(t,5,4,6,8,'violet');rect(t,4,5,8,6,'violet');rect(t,5,5,6,6,'purple');rect(t,5,5,6,1,'lilac');rect(t,5,6,1,4,'lilac');rect(t,7,7,2,2,'plum');rect(t,7,7,2,1,'violet');rect(t,6,10,5,1,'violet');tiles.me_port=t;
t=shell();inset(t,3,4,8,8);rect(t,4,5,6,6,'plum');rect(t,5,5,4,6,'violet');rect(t,4,6,6,4,'violet');rect(t,5,6,4,4,'purple');rect(t,5,6,4,1,'lilac');rect(t,5,7,1,2,'lilac');rect(t,7,8,2,1,'violet');rect(t,11,7,3,2,'dark');rect(t,12,7,2,2,'cyan');rect(t,12,7,2,1,'ice');tiles.me_side_west=t;
tiles.me_side_east=t.map(r=>[...r].reverse());
const cw=a=>a[0].map((_,x)=>a.map(row=>row[x]).reverse());
tiles.me_side_up=cw(t);tiles.me_side_down=cw(cw(cw(t)));
const lit=v=>JSON.stringify(v).replaceAll('/','\\u002f');
const value=r=>r.structuredContent??JSON.parse(r.content.find(c=>c.type==='text').text);
const client=new BlockbenchMcp();await client.init();
try{
 // Provider owns all shared ME tiles. Router/Endpoint are painted separately.
 const sources=new Map();
 for(const name of ['pattern_provider','router','processing_endpoint']){
  const model=JSON.parse(fs.readFileSync(path.join(base,'models',name+'.bbmodel')));
  const dest=path.join(work,'models',name+'.bbmodel');
  await client.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${lit(model)},{path:${lit(dest)}});return true;})()`});
  await new Promise(r=>setTimeout(r,150));
  for(const texture of model.textures){
   const key=texture.name.replace('.png',''),grid=tiles[key];if(!grid)continue;
   const colors=new Map();grid.forEach((row,y)=>row.forEach((color,x)=>{if(!colors.has(color))colors.set(color,[]);colors.get(color).push({x,y});}));
   for(const [color,coordinates]of colors)await client.call('paint_with_brush',{texture_id:texture.name,coordinates,brush_settings:{size:1,opacity:255,softness:0,shape:'square',color},connect_strokes:false});
  }
  const painted=value(await client.call('risky_eval',{code:'Texture.all.map(t=>({name:t.name,source:t.getDataURL()}))'}));
  for(const texture of painted)sources.set(texture.name,texture.source);
  await client.call('export_model',{codec_id:'project',path:dest,max_content_length:0});
  console.log('Painted and exported in Blockbench:',name);
 }
 for(const name of fs.readdirSync(path.join(base,'textures')))fs.copyFileSync(path.join(base,'textures',name),path.join(work,'textures',name));
 for(const [name,source]of sources)fs.writeFileSync(path.join(work,'textures',name),Buffer.from(source.split(',')[1],'base64'));
 for(const name of fs.readdirSync(path.join(base,'models')).filter(n=>n==='bridge.bbmodel'||/^cable_(isolated|end|straight_[xyz]|corner|tee|cross|six_way)\.bbmodel$/.test(n)))fs.copyFileSync(path.join(base,'models',name),path.join(work,'models',name));
 fs.writeFileSync(path.join(work,'pixel-plan.json'),JSON.stringify({palette:P,tiles},null,2)+'\n');
}finally{await client.close();}
