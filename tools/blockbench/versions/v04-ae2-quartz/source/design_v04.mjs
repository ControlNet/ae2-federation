/** V04: AE2 quartz edge and flat functional glyphs, painted through native MCP. */
import fs from 'node:fs';import path from 'node:path';import {BlockbenchMcp} from './mcp_client.mjs';
const root=path.resolve(import.meta.dirname,'../..');
const base=path.join(root,'tools/blockbench/versions/v02-ae2-ceramic');
const work=path.join(root,'tools/blockbench/.local/v04-work');
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
// Four equal arms and a compact central junction; no nested square panels.
tiles.router=glyph([
 '...cbbc...', '...cbbc...', '...cbbc...', 'cccbabbccc',
 'bbbabbabbb', 'bbbabbabbb', 'cccbabbccc', '...cbbc...',
 '...cbbc...', '...cbbc...'],C);
for(let y=6;y<10;y++)for(let x=6;x<10;x++)tiles.router[y][x]=P.cyan;
for(let x=6;x<10;x++)tiles.router[6][x]=P.ice;
tiles.router[7][6]=P.ice;tiles.router[8][6]=P.ice;
// Three interleaved pattern sheets; color separates layers without black slots.
tiles.pattern_provider=glyph([
 'abbbbbbbba','acccccccca','aaaaaaaaaa','..abbbbbaa',
 '..accccaca','..aaaaaaca','abbbbbbbca','acccccccca',
 'aaaaaaaaaa','..........'],C);
// A compact execution field with a central workpiece and short flanking marks.
tiles.processing_endpoint=glyph([
 '..........','.aaaaaaaa.','.abbbbbba.','aaccccccaa',
 'abcabbacba','abcaabacba','aaccbbccaa','.aaaaaaaa.',
 '...c..c...','..........'].map(r=>r.padEnd(10,'.')),C);
// Large ME glyph: paired Fluix contacts around a pale central seam.
tiles.me_port=glyph([
 'aabbbbbaaa','abccccccba','abcaaaacba','abca..acba',
 'abca..acba','abca..acba','abca..acba','abcaaaacba',
 'abccccccba','aaabbbbbaa'],M);
let side=glyph([
 'aabbbbaa..','abccccba..','abcaaaba..','abca.aba..',
 'abca.abacc','abca.ababb','abcaaaba..','abccccba..',
 'aaabbbba..','..........'],M);
// Cyan is only the short front-edge cue; the ME motif retains the dominant area.
side[7][11]=P.teal;side[7][12]=P.cyan;side[8][11]=P.cyan;side[8][12]=P.ice;
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
