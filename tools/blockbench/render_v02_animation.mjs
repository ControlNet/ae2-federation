/** Capture each texture frame from Blockbench; no external model renderer. */
import fs from 'node:fs';import path from 'node:path';import {BlockbenchMcp} from './mcp_client.mjs';
const work=path.resolve('tools/blockbench/.local/v02-work');const frames=path.join(work,'animation-frames');fs.mkdirSync(frames,{recursive:true});
const model=JSON.parse(fs.readFileSync(path.join(work,'models/cable_continuous.bbmodel')));
const lit=v=>JSON.stringify(v).replaceAll('/', '\\u002f');
const client=new BlockbenchMcp();await client.init();
try{
 await client.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${lit(model)},{path:${lit(path.join(work,'models/cable_continuous.bbmodel'))}});return true;})()`});
 await new Promise(r=>setTimeout(r,200));
 await client.call('create_offscreen_view',{id:'pulse',width:960,height:540,antialias:false});
 await client.call('set_camera_angle',{view:'pulse',position:[90,42,70],target:[40,8,8],projection:'perspective'});
 for(let frame=0;frame<16;frame++){
  await client.call('risky_eval',{code:`(()=>{TextureAnimator.stop();const animated=Texture.all.filter(t=>t.frameCount>1);for(const t of animated)t.currentFrame=${frame};TextureAnimator.update(animated);return true;})()`});
  const r=await client.call('capture_screenshot',{view:'pulse'});const img=r.content.find(c=>c.type==='image');fs.writeFileSync(path.join(frames,String(frame).padStart(2,'0')+'.png'),Buffer.from(img.data,'base64'));
 }
 await client.call('delete_offscreen_view',{view:'pulse'});
}finally{await client.close();}
