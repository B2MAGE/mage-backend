import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { loadFixtures, directory, sha256 } from './fixtures.mjs';
import { validateSceneSubmissions } from '../audit-scene-submissions.mjs';

export const project = 'mage-portfolio-demo';
const base = 'http://127.0.0.1:18080/api/';
const repo = path.resolve(directory, '../..');
export function parseCommand(args) {
  if (args.length === 1 && ['check', 'start', 'seed', 'verify'].includes(args[0])) return args[0];
  if (args.length === 3 && args[0] === 'reset' && args[1] === '--confirm' && args[2] === project) return 'reset';
  throw new Error('Usage: node scripts/demo/demo.mjs check|start|seed|verify OR reset --confirm mage-portfolio-demo');
}
function compose(args) {
  const result = spawnSync('docker', ['compose', '-p', project, '-f', path.join(directory, 'compose.yml'), ...args], {
    cwd: repo, stdio: 'inherit', windowsHide: true,
  });
  if (result.error || result.status !== 0) throw new Error(`Demo Docker operation failed: ${args[0]}`);
}
async function api(route, { method = 'GET', token, body } = {}) {
  const response = await fetch(new URL(route, base), {
    method, redirect: 'error', signal: AbortSignal.timeout(30_000),
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${method} ${route}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
async function waitForApi() {
  for (let attempt = 0; attempt < 90; attempt++) {
    try { await api('/ready'); return; } catch { await delay(2000); }
  }
  throw new Error('Demo backend did not become ready. Inspect docker compose logs before retrying.');
}
async function seed({ catalog, thumbnails }, password) {
  assert.equal((await api('scenes')).length, 0, 'Demo already contains scenes. Use the explicit reset command to replace it.');
  const sessions = new Map();
  for (const user of catalog.users) {
    await api('auth/register', { method:'POST', body:{ ...user, key:undefined, handle:`@${user.handle}`, password } });
    const session = await api('auth/login', { method:'POST', body:{ email:user.email, password } });
    sessions.set(user.key, session);
    const current = await api('users/me', { token:session.accessToken });
    assert.equal(current.handle, user.handle);
  }
  const tags = new Map();
  for (const name of new Set(catalog.scenes.flatMap(scene => scene.tags))) {
    const tag = await api('tags', { method:'POST', token:sessions.get(catalog.users[0].key).accessToken, body:{name} });
    tags.set(name, tag.tagId);
  }
  for (const scene of catalog.scenes) {
    const token = sessions.get(scene.owner).accessToken;
    const thumbnail = thumbnails.get(scene.key);
    const upload = await api('scenes/thumbnail/presign', { method:'POST', token,
      body:{filename:scene.key+'.png',contentType:'image/png',sizeBytes:thumbnail.bytes.length} });
    assert.equal(new URL(upload.uploadUrl).origin, 'http://127.0.0.1:19000', 'Upload must target this demo storage.');
    const uploaded = await fetch(upload.uploadUrl, { method:upload.method,headers:upload.headers,body:thumbnail.bytes,redirect:'error',signal:AbortSignal.timeout(30_000) });
    assert.ok(uploaded.ok, `Thumbnail upload failed for ${scene.name}`);
    const saved = await api('scenes', { method:'POST',token,body:{name:scene.name,description:scene.description,sceneData:scene.sceneData,thumbnailObjectKey:upload.objectKey,tagIds:scene.tags.map(name=>tags.get(name))} });
    const route = `scenes/${saved.sceneId}`;
    const comment = await api(route+'/comments',{method:'POST',token:sessions.get(scene.discussion.author).accessToken,body:{text:scene.discussion.text}});
    await api(route+'/comments',{method:'POST',token,body:{text:scene.discussion.reply,parentCommentId:comment.commentId}});
    for(const key of scene.likedBy) await api(route+'/vote',{method:'PUT',token:sessions.get(key).accessToken,body:{vote:'up'}});
    for(const key of scene.savedBy) await api(route+'/save',{method:'POST',token:sessions.get(key).accessToken});
    for(let view=0;view<scene.views;view++) await api(route+'/views',{method:'POST'});
    console.log(`Created ${scene.name}`);
  }
}
async function verify({catalog,thumbnails}) {
  const scenes = await api('scenes');
  assert.equal(scenes.length,catalog.scenes.length,'Scene count differs; reset should not leave duplicates.');
  for(const fixture of catalog.scenes) {
    const scene = scenes.find(item=>item.name===fixture.name);
    assert.ok(scene,`Missing ${fixture.name}`);
    assert.equal(scene.creatorHandle,catalog.users.find(user=>user.key===fixture.owner).handle);
    assert.equal(scene.availability.available,true);
    assert.deepEqual(scene.sceneData,fixture.sceneData);
    assert.equal(scene.description,fixture.description);
    assert.equal(scene.engagement.upvotes,fixture.likedBy.length);
    assert.equal(scene.engagement.saves,fixture.savedBy.length);
    assert.equal(scene.engagement.views,fixture.views);
    const detail=await api(`scenes/${scene.sceneId}`);
    assert.deepEqual([...detail.tags].sort(),[...fixture.tags].sort());
    const comments=await api(`scenes/${scene.sceneId}/comments`);
    assert.equal(comments.length,1);
    assert.equal(comments[0].text,fixture.discussion.text);
    assert.equal(comments[0].replies.length,1);
    assert.equal(comments[0].replies[0].text,fixture.discussion.reply);
    assert.equal(new URL(scene.thumbnailRef).origin,'http://127.0.0.1:19000');
    const image=await fetch(scene.thumbnailRef,{redirect:'error',signal:AbortSignal.timeout(30_000)});
    assert.equal(image.status,200);
    assert.equal(sha256(Buffer.from(await image.arrayBuffer())),thumbnails.get(fixture.key).hash);
  }
  for(const user of catalog.users) {
    const profile=await api(`profiles/${user.handle}`);
    assert.equal(profile.handle,user.handle);
  }
  const report={project,accounts:catalog.users.length,scenes:scenes.length,comments:scenes.length*2,checkedAt:new Date().toISOString(),fixtureHash:sha256(JSON.stringify(catalog))};
  await mkdir(path.join(repo,'.local/portfolio-demo'),{recursive:true});
  await writeFile(path.join(repo,'.local/portfolio-demo/verification.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report));
}
async function main() {
  const command=parseCommand(process.argv.slice(2));
  const fixtures=await loadFixtures();
  if(['check','reset','seed'].includes(command)) await validateSceneSubmissions(fixtures.catalog.scenes.map(scene=>({sceneId:scene.key,sceneData:scene.sceneData})));
  if(command==='check') {console.log('Fixtures, references, thumbnails and current scene validation passed.');return;}
  const password=process.env.MAGE_DEMO_PASSWORD;
  if(['start','reset','seed'].includes(command)) assert.ok(password && password.length>=12 && password.length<=72,'Set MAGE_DEMO_PASSWORD to 12–72 characters. It is used only by the separate demo environment.');
  if(command==='reset') {
    console.log(`Reset target: Docker project ${project}; database mage_demo; bucket mage-demo-thumbnails. Only this project’s database and thumbnail volumes will be replaced.`);
    compose(['down','--volumes','--remove-orphans']);
  }
  if(command==='start'||command==='reset') {compose(['up','--build','--detach']);await waitForApi();}
  if(command==='start') {console.log('Demo started; existing data retained. No seeding or reset performed.');return;}
  if(command==='reset'||command==='seed') await seed(fixtures,password);
  await verify(fixtures);
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) main().catch(error=>{console.error(error.message);process.exitCode=1;});
