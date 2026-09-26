import fs from 'node:fs';
import path from 'node:path';
import {BlockbenchMcp} from './mcp_client.mjs';
const work=path.resolve('tools/blockbench/.local/v02-work');
const client=new BlockbenchMcp();await client.init();
const lit=v=>JSON.stringify(v).replaceAll('/', '\\u002f');
const read=r=>r.structuredContent??JSON.parse(r.content.find(c=>c.type==='text').text);
try{
 const source=JSON.parse(fs.readFileSync(path.join(work,'models/cable_straight_x.bbmodel')));
 await client.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${lit(source)},{path:${lit(path.join(work,'models/cable_straight_x.bbmodel'))}});return true;})()`});
 await new Promise(r=>setTimeout(r,100));
 const texture=read(await client.call('risky_eval',{code:`(()=>{const t=Texture.all.find(t=>t.name==='glass.png');Undo.initEdit({textures:[t],bitmap:true});t.edit(canvas=>{const c=canvas.getContext('2d');c.clearRect(0,0,16,16);c.fillStyle='rgba(139,182,182,0.22)';c.fillRect(1,5,14,1);c.fillStyle='rgba(112,149,155,0.12)';c.fillRect(1,10,14,1);},{no_undo:true});Undo.finishEdit('V02 restrained glass highlights');return t.getDataURL();})()`}));
 fs.writeFileSync(path.join(work,'textures/glass.png'),Buffer.from(texture.split(',')[1],'base64'));
 for(const file of fs.readdirSync(path.join(work,'models')).filter(f=>f.startsWith('cable_'))){
  const model=JSON.parse(fs.readFileSync(path.join(work,'models',file)));
  const glass=model.textures.find(t=>t.name==='glass.png');if(!glass)continue;glass.source=texture;
  await client.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${lit(model)},{path:${lit(path.join(work,'models',file))}});return true;})()`});
  await client.call('export_model',{codec_id:'project',path:path.join(work,'models',file),max_content_length:0});
 }
}finally{await client.close();}
