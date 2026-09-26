/** Review the isolated cable against the unchanged connected cable family. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const base=path.resolve('tools/blockbench/versions/v06-dense-panels');
const work=path.resolve('tools/blockbench/.local/v07-work');
const read=(n,old=false)=>JSON.parse(fs.readFileSync(path.join(old?base:work,'models',n+'.bbmodel')));
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


function save(n,instances){fs.writeFileSync(path.join(work,'models',n+'.bbmodel'),JSON.stringify(scene(n,instances),null,2)+'\n');}
save('isolated_comparison',[
 ['V06 opaque isolated',read('cable_isolated',true),[0,0,0]],['V07 layered isolated',read('cable_isolated'),[24,0,0]],
 ['Unchanged connected end',read('cable_end'),[48,0,0]],['Unchanged straight',read('cable_straight_x'),[72,0,0]]]);
const family=read('v06_family',true);family.name='v07_family';
const old=family.elements.find(e=>e.from.join(',')==='77,5,37'&&e.to.join(',')==='83,11,43');
if(!old)throw new Error('Expected original isolated exhibit cube');
const isolated=read('cable_isolated');const map=isolated.textures.map(t=>{
 let i=family.textures.findIndex(x=>x.name===t.name&&x.source===t.source);
 if(i<0){i=family.textures.length;family.textures.push({...t,id:String(i),uuid:crypto.randomUUID()});}return i;
});
const cubes=isolated.elements.map(e=>{const n=structuredClone(e);n.uuid=crypto.randomUUID();for(const k of ['from','to','origin'])if(n[k])n[k]=n[k].map((v,i)=>v+[72,0,32][i]);for(const f of Object.values(n.faces))f.texture=map[f.texture];return n;});
family.elements=family.elements.filter(e=>e.uuid!==old.uuid).concat(cubes);
function replace(nodes){return nodes.flatMap(n=>typeof n==='string'?(n===old.uuid?cubes.map(e=>e.uuid):[n]):[{...n,children:replace(n.children??[])}]);}
family.outliner=replace(family.outliner);
fs.writeFileSync(path.join(work,'models/v07_family.bbmodel'),JSON.stringify(family,null,2)+'\n');
console.log('Composed isolated comparison and complete series.');
