import test from 'node:test';
import assert from 'node:assert/strict';
import { loadFixtures, validateRelationships } from './fixtures.mjs';
import { parseCommand, project } from './demo.mjs';

test('committed catalog has valid account/scene references and actual captured PNGs',async()=>{
  const {catalog,thumbnails}=await loadFixtures();
  assert.equal(thumbnails.size,catalog.scenes.length);
  assert.equal(new Set(catalog.scenes.filter(scene=>scene.sceneData.kind==='template').map(scene=>scene.sceneData.templateId)).size,14);
  const modes = new Set(catalog.scenes.map(scene => scene.sceneData.settings.audioResponse ?? 'legacy'));
  assert.deepEqual(modes, new Set(['legacy', 'mapped-v1']));
});
test('Signal Duet is a concrete editable Builder example with independent bass and treble responses',async()=>{
  const {catalog}=await loadFixtures();
  const starter=catalog.scenes.find(scene=>scene.key==='signal-duet');
  assert.ok(starter,'The curated Builder music example must remain in the reproducible demo.');
  const document=starter.sceneData;
  assert.equal(document.kind,'builder');
  assert.equal(document.builderVersion,1);
  assert.equal(document.templateId,undefined);
  assert.equal(document.shader,undefined);
  assert.equal(document.settings.audioResponse,'mapped-v1');
  assert.deepEqual(document.settings.audioResponseConfig.mappings,[],'Object responses must not be obscured by a shared scene-wide response.');
  assert.deepEqual(document.objects.map(object=>({name:object.name,shape:object.operation.type,responses:object.bindings.map(binding=>({target:binding.target,source:binding.source}))})),[
    {name:'Bass Pearl',shape:'sphere',responses:[{target:'scale.y',source:'bass-hit'}]},
    {name:'Treble Halo',shape:'torus',responses:[{target:'position.y',source:'treble-hit'}]},
  ]);
});
test('bad ownership, duplicate engagement, embedded credentials and escaped thumbnail paths fail preflight',async()=>{
  const {catalog}=await loadFixtures();
  for(const change of [
    value=>{value.scenes[0].owner='missing-user';},
    value=>{value.scenes[0].likedBy=['ari','ari'];},
    value=>{value.users[0].password='never-commit-credentials';},
    value=>{value.scenes[0].thumbnail='../outside.png';},
  ]) {const invalid=structuredClone(catalog);change(invalid);assert.throws(()=>validateRelationships(invalid));}
});
test('reset must explicitly name the fixed demo project; normal start and check never imply reset',()=>{
  assert.equal(parseCommand(['start']),'start');
  assert.equal(parseCommand(['check']),'check');
  assert.equal(parseCommand(['reset','--confirm',project]),'reset');
  for(const args of [[],['reset'],['reset','--confirm','production'],['start','--reset'],['reset','--confirm',project,'--api','https://mage.peterbucci.com']]) {
    assert.throws(()=>parseCommand(args));
  }
});
