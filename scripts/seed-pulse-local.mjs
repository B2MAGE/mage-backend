/** Local fixture data; production APIs are exercised without adding production seed migrations. */
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const frontend = path.resolve(repo, '../mage-frontend');
const base = new URL(process.env.MAGE_SEED_API_URL ?? 'http://127.0.0.1:8080/api/');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) throw new Error('Only loopback development API targets are allowed.');
const password = 'PulseDemo2026!';
const people = [
  ['Ari','Rivera','ari'], ['Mina','Park','mina'], ['Jonah','Reed','jonah'], ['Talia','North','talia'],
  ['Elio','Mercer','elio'], ['Sasha','Chen','sasha'], ['Nico','Santos','nico'], ['Imani','Brooks','imani'],
  ['Kai','Tanaka','kai'], ['Lena','Sol','lena'], ['Theo','Vale','theo'], ['Noor','Aziz','noor'],
];
const titles = [
  'Neon Bloom','Orbit Garden','Afterglow','Blue Noise','Red Phase','Prism Drift','Lunar Circuit','Violet Reverie',
  'Soft Focus','Midnight Geometry','Amber Current','Opal Field','Polar Signal','Slow Wave','Electric Moss',
  'Horizon Lines','Crystal Rain','Liquid Chrome','Paper Constellation','Ultraviolet','Quiet Sparks','Night Transit',
  'Afterimage','Chromatic Tide','Velvet Phase','Echo Chamber','Signal Garden','Deep Current','Solar Static','Glass Frequency',
];
const tagSets = [
  ['Ambient','Reactive','Neon'], ['Geometry','Minimal','Loop'], ['Experimental','Reactive','Glitch'],
  ['Ambient','Cinematic','Generative'], ['Abstract','Geometry','Neon'], ['Minimal','Generative','Loop'],
];
const palettes = [[171,141,255],[75,216,199],[255,169,95],[106,174,250],[242,113,158],[190,220,132]];
async function api(route, {method='GET', token, body, allowConflict=false} = {}) {
  const response = await fetch(new URL(route, base), {
    method, headers: {...(body ? {'Content-Type':'application/json'} : {}), ...(token ? {Authorization:'Bearer '+token} : {})},
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  if (!response.ok && !(allowConflict && response.status === 409)) throw new Error(method+' '+route+': '+response.status+' '+text);
  return text ? JSON.parse(text) : null;
}
// Import the editor's real defaults, sanitizer, and bundled shaders.
const ts = createRequire(path.join(frontend,'package.json'))('typescript');
async function compile(relative) {
  return ts.transpileModule(await readFile(path.join(frontend,relative),'utf8'), {
    compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext},
  }).outputText;
}
const moduleUrl = text => 'data:text/javascript;base64,'+Buffer.from(text).toString('base64');
const embeddedUrl = moduleUrl(await compile('src/modules/scene-editor/embeddedShaderScenes.ts'));
const source = (await compile('src/modules/scene-editor/sceneEditor.ts')).replace("'./embeddedShaderScenes'", JSON.stringify(embeddedUrl));
const {createDefaultSceneData,sanitizeSceneData,SHADER_SCENES} = await import(moduleUrl(source));
function sceneData(index) {
  const data = createDefaultSceneData();
  data.visualizer.shader = SHADER_SCENES[index % SHADER_SCENES.length].shader;
  data.intent.time_multiplier = 0.25+(index%5)*0.15;
  data.intent.autoRotateSpeed = 0.1+(index%4)*0.1;
  data.fx.bloom = {enabled:true,strength:0.5+(index%4)*0.2,radius:0.35,threshold:0.25};
  data.fx.passes.rgbShift = index%4===2;
  data.fx.params.rgbShift.amount = 0.002;
  if (index===0) {
    data.intent.autoRotate = false;
    data.visualizer.shader = [
      'let size = input();',
      'let pointerDown = input();',
      'size = 0.9 + size * 0.15;',
      'setMaxIterations(100); setStepSize(0.7);',
      'rotateX(PI / 2 + sin(time * 0.12) * 0.12); rotateZ(time * 0.025);',
      'color(0.66, 0.52, 1.0); shine(0.65); metal(0.35); torus(size * 1.25, 0.013);',
      'color(0.25, 0.8, 0.74); torus(size * 0.87, 0.01);',
      'color(0.74, 0.3, 0.75); torus(size * 0.48, 0.012);',
      'color(0.65, 0.52, 1.0); sphere(size * 0.11);',
    ].join('\n');
    data.visualizer.scale=2;
    data.fx.bloom.strength=0.35;
    data.fx.bloom.threshold=0.5;
    data.fx.toneMapping.exposure=0.8;
  }
  return sanitizeSceneData(data);
}
// Deterministic PNG fixture artwork, generated without image libraries or remote image services.
const crcTable=Uint32Array.from({length:256},(_,n)=>{let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c>>>0;});
function chunk(type,data) {
  const contents=Buffer.concat([Buffer.from(type),data]); let crc=0xffffffff;
  for(const value of contents)crc=crcTable[(crc^value)&255]^(crc>>>8);
  const output=Buffer.alloc(data.length+12);output.writeUInt32BE(data.length);contents.copy(output,4);
  output.writeUInt32BE((crc^0xffffffff)>>>0,output.length-4);return output;
}
function thumbnail(index) {
  const width=800,height=450,pixels=Buffer.alloc((width*3+1)*height),palette=palettes[index%palettes.length];
  for(let y=0;y<height;y++)for(let x=0;x<width;x++) {
    const nx=(x-width/2)/height,ny=(y-height/2)/height,angle=Math.atan2(ny,nx),r=Math.hypot(nx,ny);
    let light=0;
    for(let ring=0;ring<3;ring++){
      const radius=0.1+ring*0.11+Math.sin(angle*(3+index%5)+index)*(index===0?0.008:0.028);
      const d=Math.abs(r-radius);light+=Math.exp(-d*d/0.000015)*0.9+Math.exp(-d*d/0.0012)*0.12;
    }
    light+=Math.exp(-r*r/0.0012)*0.75;
    const offset=y*(width*3+1)+1+x*3;
    for(let channel=0;channel<3;channel++)pixels[offset+channel]=Math.min(255,(channel===2?13:8)+palette[channel]*(light+Math.exp(-r*r/0.2)*0.04));
  }
  const header=Buffer.alloc(13);header.writeUInt32BE(width);header.writeUInt32BE(height,4);header[8]=8;header[9]=2;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))]);
}
const existing=await api('scenes');
if(existing.some(scene=>!titles.includes(scene.name)))throw new Error('Non-demo scenes exist. Start with a fresh Pulse review volume; nothing was changed.');
const users=[];
for(const [firstName,lastName,handle] of people) {
  const email=handle+'@pulse.local';
  await api('auth/register',{method:'POST',body:{email,password,firstName,lastName,displayName:firstName+' '+lastName},allowConflict:true});
  users.push(await api('auth/login',{method:'POST',body:{email,password}}));
}
console.log('Verified registration/login for '+users.length+' demo users.');
const tags=new Map((await api('tags')).map(tag=>[tag.name.toLowerCase(),tag.tagId]));
for(const name of new Set(tagSets.flat()))if(!tags.has(name.toLowerCase())){
  const tag=await api('tags',{method:'POST',token:users[0].accessToken,body:{name}});tags.set(name.toLowerCase(),tag.tagId);
}
const scenes=[];let thumbnailChecks=0;
const commentTexts=[
  'The color transitions feel beautifully calm. This is going into my late-night playlist.',
  'Love how the geometry opens up when the rhythm changes. The slower rotation really works here.',
  'Tried this with an ambient set and kept it running for the whole session.',
  'That bloom is just right. Curious how this would look with a warmer palette.',
];
const replyText='Thank you! I spent a while balancing the motion and glow. Glad it landed.';
for(let index=0;index<titles.length;index++){
  const owner=users[index<14?0:1+(index-14)%11];
  let scene=existing.find(s=>s.name===titles[index]&&s.ownerUserId===owner.userId);
  if(!scene) {
    const bytes=thumbnail(index);
    const upload=await api('scenes/thumbnail/presign',{method:'POST',token:owner.accessToken,body:{filename:'pulse-'+(index+1)+'.png',contentType:'image/png',sizeBytes:bytes.length}});
    const put=await fetch(upload.uploadUrl,{method:upload.method,headers:upload.headers,body:bytes});
    assert.equal(put.ok,true,'MinIO PUT failed: '+put.status);
    scene=await api('scenes',{method:'POST',token:owner.accessToken,body:{
      name:titles[index],
      description:index===0?'A slow-moving audiovisual environment built around layered geometry, reactive bloom, and shifting color.':
        titles[index]+' explores '+tagSets[index%tagSets.length][0].toLowerCase()+' motion through layered geometry and shifting light. Try it with your own music, or let the slow rotation play on its own. Created as part of the Pulse local review collection.',
      sceneData:sceneData(index),thumbnailObjectKey:upload.objectKey,
    }});
  }
  await api('scenes/'+scene.sceneId+'/tags',{method:'PUT',token:owner.accessToken,body:{tagIds:tagSets[index%tagSets.length].map(name=>tags.get(name.toLowerCase()))}});
  const image=await fetch(scene.thumbnailRef);assert.equal(image.status,200,'Public thumbnail GET failed: '+scene.name);
  assert.match(image.headers.get('content-type'),/^image\/png/);thumbnailChecks++;
  const currentComments=await api('scenes/'+scene.sceneId+'/comments');
  for(let c=0;c<(index===0?4:2);c++){
    const author=users[(index+c+2)%users.length];
    let comment=currentComments.find(item=>item.text===commentTexts[c]&&item.authorUserId===author.userId);
    if(!comment)comment=await api('scenes/'+scene.sceneId+'/comments',{method:'POST',token:author.accessToken,body:{text:commentTexts[c]}});
    if(c===0&&!comment.replies?.some(reply=>reply.text===replyText)){
      await api('scenes/'+scene.sceneId+'/comments',{method:'POST',token:owner.accessToken,body:{parentCommentId:comment.commentId,text:replyText}});
    }
    await api('scenes/'+scene.sceneId+'/comments/'+comment.commentId+'/vote',{method:'PUT',token:users[(index+7)%users.length].accessToken,body:{vote:'up'}});
  }
  for(let voter=0;voter<(index===0?11:3+index%7);voter++){
    const token=users[voter].accessToken;
    await api('scenes/'+scene.sceneId+'/vote',{method:'PUT',token,body:{vote:'up'}});
    if(voter%2===0)await api('scenes/'+scene.sceneId+'/save',{method:'POST',token});
    if(!existing.some(item=>item.sceneId===scene.sceneId))await api('scenes/'+scene.sceneId+'/views',{method:'POST',token});
  }
  scenes.push({sceneId:scene.sceneId,name:scene.name,ownerUserId:owner.userId,thumbnailRef:scene.thumbnailRef});
  console.log('['+(index+1)+'/'+titles.length+'] '+scene.name);
}
const comments=await api('scenes/'+scenes[0].sceneId+'/comments');
assert.ok(commentTexts.every(text=>comments.some(comment=>comment.text===text)),'Featured seed comments are missing');
assert.ok(comments.some(comment=>comment.replies.length>0));
const manifestPath=path.join(repo,'.local/pulse-seed-manifest.json');await mkdir(path.dirname(manifestPath),{recursive:true});
await writeFile(manifestPath,JSON.stringify({
  version:1,generatedAt:new Date().toISOString(),apiBase:base.href,featuredSceneId:scenes[0].sceneId,
  frontendEnvironment:{VITE_HOME_FEATURED_SCENE_ID:String(scenes[0].sceneId)},demoPassword:password,
  users:users.map(({userId,email,displayName})=>({userId,email,displayName})),scenes,
  validation:{registrationAndLogin:users.length,thumbnailGetChecks:thumbnailChecks,featuredComments:comments.length,featuredReplies:comments.flatMap(comment=>comment.replies).length},
},null,2)+'\n');
console.log('Ready: 12 users, 30 scenes, 92 comments/replies, 30 public thumbnails.');
console.log('Featured: '+scenes[0].name+' (ID '+scenes[0].sceneId+'). Login: ari@pulse.local / '+password);
console.log('Manifest: '+manifestPath);

