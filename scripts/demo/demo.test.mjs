import test from 'node:test';
import assert from 'node:assert/strict';
import { loadFixtures, validateRelationships } from './fixtures.mjs';
import { parseCommand, project } from './demo.mjs';

test('committed catalog has valid account/scene references and actual captured PNGs',async()=>{
  const {catalog,thumbnails}=await loadFixtures();
  assert.equal(thumbnails.size,catalog.scenes.length);
  assert.equal(new Set(catalog.scenes.map(scene=>scene.sceneData.templateId)).size,14);
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
