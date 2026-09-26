/** Compose V06 review scenes without changing the individual device projects. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const work=path.resolve('tools/blockbench/.local/v06-work');
const base=path.resolve('tools/blockbench/versions/v04-ae2-quartz');
const read=(name,old=false)=>JSON.parse(fs.readFileSync(path.join(old?base:work,'models',name+'.bbmodel')));
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

function save(name,instances){const m=scene(name,instances);m.editor_state={selected_elements:[],selected_groups:[],previews:{main:{position:[150,85,160],target:[40,8,22],orthographic:false,zoom:0.5}}};fs.writeFileSync(path.join(work,'models',name+'.bbmodel'),JSON.stringify(m,null,2)+'\n');}
for(const name of ['router','pattern_provider','processing_endpoint'])save(name+'_comparison',[
 ['V04',JSON.parse(fs.readFileSync(path.resolve('tools/blockbench/versions/v04-ae2-quartz/models',name+'.bbmodel'))),[0,0,0]],['V06',read(name),[24,0,0]]]);
save('v06_key_faces',[['Router',read('router'),[0,0,0]],['Provider',read('pattern_provider'),[24,0,0]],['Endpoint',read('processing_endpoint'),[48,0,0]]]);
const family=read('v04_family',true);family.name='v06_family';
const revised=new Set(['router','pattern_provider','processing_endpoint','me_port','me_side_east','me_side_west','me_side_up','me_side_down']);
for(const t of family.textures)if(revised.has(t.name.replace('.png','')))t.source='data:image/png;base64,'+fs.readFileSync(path.join(work,'textures',t.name)).toString('base64');
family.editor_state={selected_elements:[],selected_groups:[],previews:{main:{position:[160,100,180],target:[40,8,22],orthographic:false,zoom:0.5}}};
fs.writeFileSync(path.join(work,'models/v06_family.bbmodel'),JSON.stringify(family,null,2)+'\n');
const native=name=>JSON.parse(fs.readFileSync(path.join(work,'ae2_'+name+'.bbmodel')));
// Original AE2 models stay in local reference storage; only the render is archived.
const context=scene('AE2 19.2.17 context',[
 ['AE2 Provider',native('pattern_provider'),[0,0,0]],['V06 Provider',read('pattern_provider'),[24,0,0]],
 ['AE2 Interface',native('interface'),[48,0,0]],['V06 Endpoint',read('processing_endpoint'),[72,0,0]]]);
fs.writeFileSync(path.join(work,'ae2_context.bbmodel'),JSON.stringify(context,null,2)+'\n');
console.log('Composed V04/V06 comparisons, family, key faces and local AE2 context.');
