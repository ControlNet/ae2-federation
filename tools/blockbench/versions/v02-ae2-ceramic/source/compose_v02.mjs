/** Assemble review-only scenes; source devices remain independent editable models. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const work=path.resolve('tools/blockbench/.local/v02-work');
const baseline=path.resolve('art/versions/v01-baseline/models');
const read=(name,old=false)=>JSON.parse(fs.readFileSync(path.join(old?baseline:path.join(work,'models'),name+'.bbmodel')));
const textureSource=name=>'data:image/png;base64,'+fs.readFileSync(path.join(work,'textures',name)).toString('base64');
function scene(name,instances){
 const result={meta:{format_version:'5.0',model_format:'free',box_uv:false},name,resolution:{width:16,height:16},elements:[],groups:[],outliner:[],textures:[]};
 for(const [label,input,offset] of instances){
  const m=structuredClone(input), ids=new Map();
  const remap=id=>{if(!ids.has(id))ids.set(id,crypto.randomUUID());return ids.get(id);};
  const textureMap=m.textures.map(t=>{const index=result.textures.findIndex(x=>x.name===t.name&&x.source===t.source);if(index>=0)return index;const newIndex=result.textures.length;result.textures.push({...t,uuid:crypto.randomUUID(),id:String(newIndex)});return newIndex;});
  for(const cube of m.elements){cube.uuid=remap(cube.uuid);for(const key of ['from','to','origin'])if(cube[key])cube[key]=cube[key].map((v,i)=>v+offset[i]);for(const face of Object.values(cube.faces??{}))if(face.texture!==null)face.texture=textureMap[face.texture];result.elements.push(cube);}
  for(const group of m.groups??[]){group.uuid=remap(group.uuid);if(group.origin)group.origin=group.origin.map((v,i)=>v+offset[i]);result.groups.push(group);}
  function tree(nodes){return nodes.map(n=>typeof n==='string'?remap(n):{...n,uuid:remap(n.uuid),children:tree(n.children??[])});}
  const parent={name:label,uuid:crypto.randomUUID(),origin:offset,visibility:true,export:true,isOpen:false};result.groups.push(parent);result.outliner.push({uuid:parent.uuid,children:tree(m.outliner)});
 }
 return result;
}
function save(name,instances){const m=scene(name,instances);fs.writeFileSync(path.join(work,'models',name+'.bbmodel'),JSON.stringify(m,null,2)+'\n');}
function cable(mask){const m=JSON.parse(fs.readFileSync(path.join(work,'connection_sources',String(mask).padStart(2,'0')+'.bbmodel')));for(const t of m.textures)t.source=textureSource(t.name);return m;}
save('v02_family',[
 ['Router',read('router'),[0,0,0]],['Provider',read('pattern_provider'),[24,0,0]],['Endpoint',read('processing_endpoint'),[48,0,0]],['Bridge',read('bridge'),[72,0,5]],
 ['Cable end',cable(1),[0,0,32]],['Cable straight A',cable(3),[16,0,32]],['Cable straight B',cable(3),[32,0,32]],['Cable end',cable(2),[48,0,32]],['Cable isolated',cable(0),[72,0,32]],
]);
for(const name of ['router','pattern_provider','processing_endpoint'])save(name+'_comparison',[
 ['V01 baseline',read(name,true),[0,0,0]],['V02 candidate',read(name),[24,0,0]],
]);
save('bridge_comparison', [['V01 baseline',read('bridge',true),[0,0,5]],['V02 candidate',read('bridge'),[12,0,5]]]);
const line=[...Array(5)].map((_,x)=>['Cable '+x,cable(x===0?1:x===4?2:3),[x*16,0,0]]);
save('cable_continuous',line);
const positions=[[0,0,0],[1,0,0],[2,0,0],[3,0,0],[4,0,0],[2,0,1],[2,0,2],[3,0,2],[4,0,2],[2,0,-1],[2,0,-2],[2,1,0],[2,2,0],[2,-1,0],[2,-2,0]];
const dirs=[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]],keys=new Set(positions.map(p=>p.join(',')));
save('cable_connections',positions.map((p,i)=>{let mask=0;dirs.forEach((d,bit)=>{if(keys.has(p.map((v,k)=>v+d[k]).join(',')))mask|=1<<bit;});return ['Cable '+i+' mask '+mask,cable(mask),p.map(v=>v*16)];}));
console.log('Composed review scenes');
save('v02_key_faces',[['Router',read('router'),[0,0,0]],['Provider',read('pattern_provider'),[24,0,0]],['Endpoint',read('processing_endpoint'),[48,0,0]]]);
const native=name=>JSON.parse(fs.readFileSync(path.join(work,'ae2_'+name+'.bbmodel')));
save('ae2_context',[
 ['AE2 native Provider',native('pattern_provider'),[0,0,0]],['Federation Provider',read('pattern_provider'),[24,0,0]],
 ['AE2 native Interface',native('interface'),[48,0,0]],['Federation Endpoint',read('processing_endpoint'),[72,0,0]],
]);
