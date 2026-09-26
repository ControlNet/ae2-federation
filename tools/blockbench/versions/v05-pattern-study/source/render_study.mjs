/** Render versioned model files with the real Blockbench MCP viewport. */
import fs from 'node:fs';
import path from 'node:path';
import {BlockbenchMcp} from './mcp_client.mjs';
const [modelPath,outputPath,view='front']=process.argv.slice(2);
if(!modelPath||!outputPath)throw new Error('Usage: node render_study.mjs model.bbmodel output.png [front|back|side|top|gallery]');
const model=JSON.parse(fs.readFileSync(modelPath));
const literal=v=>JSON.stringify(v).replaceAll('/', '\\u002f');
const client=new BlockbenchMcp();await client.init();
try{
 await client.call('risky_eval',{code:`(()=>{Project.saved=true;Codecs.project.load(${literal(model)},{path:${literal(path.resolve(modelPath))}});return true;})()`});
 await new Promise(r=>setTimeout(r,200));
 await client.call('create_offscreen_view',{id:'study',width:1440,height:1080,antialias:false});
 const cameras={front:[[36,28,40],[8,8,8]],back:[[-30,26,-32],[8,8,8]],side:[[44,22,24],[8,8,8]],top:[[22,48,26],[8,8,8]],gallery:[[106,63,110],[40,8,22]],pair:[[64,34,65],[20,8,8]],bridge:[[26,21,28],[8,8,3]],bridge_pair:[[38,24,42],[14,8,8]],line:[[90,42,70],[40,8,8]],connections:[[130,100,150],[36,8,8]],front_flat:[[8,8,58],[8,8,8]],faces:[[32,8,110],[32,8,8]],context:[[100,50,115],[44,8,8]]};
 const [position,target]=cameras[view]??cameras.front;
 const result=await client.call('set_camera_angle',{view:'study',position,target,projection:'perspective'});
 const img=result.content.find(c=>c.type==='image');if(!img)throw new Error('Screenshot missing');
 fs.mkdirSync(path.dirname(outputPath),{recursive:true});fs.writeFileSync(outputPath,Buffer.from(img.data,'base64'));
 await client.call('delete_offscreen_view',{view:'study'});
 console.log(outputPath);
}finally{await client.close();}
