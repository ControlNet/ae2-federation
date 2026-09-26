/** V05: AE2 quartz edge and flat functional glyphs, painted through native MCP. */
import fs from 'node:fs';import path from 'node:path';import {BlockbenchMcp} from './mcp_client.mjs';
const root=path.resolve(import.meta.dirname,'../..');
const base=path.join(root,'tools/blockbench/versions/v02-ae2-ceramic');
const work=path.join(root,'tools/blockbench/.local/v05-work');
for(const dir of ['models','textures','renders'])fs.mkdirSync(path.join(work,dir),{recursive:true});
// Neutral values sampled from the locked AE2 19.2.17 Interface/Provider assets.
// Accent pixels are original Federation artwork, with three flat color levels.
const P={white:'#f2f2f2',quartz:'#e8e8ea',seam:'#413f54',seamLight:'#4d4d67',cyan:'#6caebb',ice:'#a2dce2',teal:'#477e93',purple:'#a48ac1',lilac:'#d0b4e9',violet:'#795f99'};
function shell(){
 const t=Array.from({length:16},()=>Array(16).fill(P.white));
 for(let i=1;i<15;i++){t[1][i]=P.seam;t[14][i]=P.seam;t[i][1]=P.seam;t[i][14]=P.seam;}
 // Small coherent material patches, with no beveled gray surround or corner bolts.
 for(const [x,y,n] of [[6,0,3],[10,15,3],[5,2,2],[9,13,3]])for(let i=0;i<n;i++)t[y][x+i]=P.quartz;
 for(const [x,y] of [[0,5],[0,6],[15,8],[15,9],[2,10],[2,11],[13,4],[13,5]])t[y][x]=P.quartz;
 for(const [x,y] of [[5,1],[6,1],[7,1],[8,14],[9,14],[10,14],[1,5],[1,6],[14,9],[14,10]])t[y][x]=P.seamLight;
 return t;
}
function glyph(rows,colors){const t=shell();rows.forEach((row,y)=>[...row].forEach((c,x)=>{t[y+3][x+3]=P[colors[c]];}));return t;}
const C={'.':'white',a:'cyan',b:'ice',c:'teal',d:'seam',e:'quartz'};
const M={'.':'white',a:'purple',b:'lilac',c:'violet',d:'seam',e:'quartz'};
const tiles={};
const grid=()=>Array.from({length:10},()=>Array(10).fill('.'));
const paint=(g,x,y,w,h,c)=>{for(let j=y;j<y+h;j++)for(let i=x;i<x+w;i++)g[j][i]=c;};
const finish=(g,colors=C)=>glyph(g.map(r=>r.join('')),colors);
// Compact diamond coupler and four short terminals replace the heavy plus sign.
let g=grid();
for(let y=0;y<10;y++)for(let x=0;x<10;x++){
 const d=Math.abs(x-4.5)+Math.abs(y-4.5);
 if(d<=4)g[y][x]=d>3?'c':d>1?'a':'b';
}
for(const [x,y,w,h]of [[4,0,2,2],[4,8,2,2],[0,4,2,2],[8,4,2,2]])paint(g,x,y,w,h,'a');
paint(g,4,0,2,1,'b');paint(g,0,4,1,2,'b');paint(g,4,9,2,1,'c');paint(g,9,4,1,2,'c');
tiles.router=finish(g);
// Two overlapping pattern wafers with stepped silhouettes and shallow tracks.
// Offset sheets avoid the old three-bar numeral and server-slot interpretations.
g=grid();paint(g,3,0,6,7,'a');paint(g,3,0,6,1,'b');paint(g,8,1,1,6,'c');paint(g,3,6,5,1,'c');
paint(g,4,2,3,1,'c');paint(g,5,3,2,1,'b');
paint(g,0,3,7,7,'a');paint(g,0,3,6,1,'b');paint(g,0,4,1,5,'b');paint(g,6,4,1,6,'c');paint(g,1,9,5,1,'c');
paint(g,2,5,3,1,'b');paint(g,2,6,1,2,'c');paint(g,3,7,2,1,'c');
paint(g,8,8,2,1,'a');paint(g,9,7,1,1,'b');tiles.pattern_provider=finish(g);
// A suspended processing core inside two restrained opposing contact jaws.
g=grid();
paint(g,2,1,2,1,'b');paint(g,6,1,2,1,'b');
paint(g,1,2,2,6,'a');paint(g,7,2,2,6,'a');
paint(g,1,3,1,4,'b');paint(g,8,3,1,4,'c');
paint(g,2,8,2,1,'c');paint(g,6,8,2,1,'c');
paint(g,3,3,4,4,'a');paint(g,3,3,4,1,'b');paint(g,3,4,1,2,'b');paint(g,4,6,3,1,'c');
// White breaks preserve two contact gaps; there is no face-like pair of dots.
paint(g,3,4,1,2,'.');paint(g,6,4,1,2,'.');tiles.processing_endpoint=finish(g);
// Faceted Fluix pair replaces the rectangular nested-ring ME symbol.
g=grid();
for(let y=0;y<10;y++)for(let x=0;x<10;x++){
 const d=Math.abs(x-4.5)+Math.abs(y-4.5);
 if(d<=5)g[y][x]=x<4?'b':x<6?'a':'c';
}
paint(g,4,2,1,3,'c');paint(g,5,5,1,3,'b');paint(g,4,4,2,2,'.');tiles.me_port=finish(g,M);
// Narrower side crystal leaves room for the existing short front-edge cue.
g=grid();
for(let y=1;y<9;y++)for(let x=0;x<8;x++){
 const d=Math.abs(x-3.5)+Math.abs(y-4.5);
 if(d<=4)g[y][x]=x<3?'b':x<5?'a':'c';
}
paint(g,3,3,1,2,'c');paint(g,4,5,1,2,'b');
let side=finish(g,M);side[7][11]=P.teal;side[7][12]=P.cyan;side[8][11]=P.cyan;side[8][12]=P.ice;
tiles.me_side_west=side;tiles.me_side_east=side.map(r=>[...r].reverse());
const cw=a=>a[0].map((_,x)=>a.map(row=>row[x]).reverse());
tiles.me_side_up=cw(side);tiles.me_side_down=cw(cw(cw(side)));
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
