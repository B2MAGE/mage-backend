import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

export const directory = path.dirname(fileURLToPath(import.meta.url));
export const sha256 = value => createHash('sha256').update(value).digest('hex');

export function validateRelationships(catalog) {
  assert.equal(catalog.version, 1);
  assert.ok(catalog.users.length >= 2 && catalog.scenes.length >= 6, 'Demo needs multiple creators and enough scenes for pagination.');
  const users = new Set(catalog.users.map(user => user.key));
  assert.equal(users.size, catalog.users.length, 'Duplicate account key.');
  assert.equal(new Set(catalog.users.map(user => user.handle)).size, users.size, 'Duplicate handle.');
  assert.equal(new Set(catalog.users.map(user => user.email)).size, users.size, 'Duplicate email.');
  for (const user of catalog.users) {
    assert.match(user.email, /^[^@]+@example\.test$/, 'Only synthetic example.test accounts belong in fixtures.');
    assert.match(user.handle, /^[a-z][a-z0-9_]{2,29}$/);
    assert.ok(!('password' in user), 'Passwords come from environment, not fixtures.');
  }
  assert.equal(new Set(catalog.scenes.map(scene => scene.key)).size, catalog.scenes.length, 'Duplicate scene key.');
  for (const scene of catalog.scenes) {
    assert.match(scene.key, /^[a-z0-9-]+$/);
    assert.ok(users.has(scene.owner), 'Unknown scene owner.');
    assert.ok(scene.name.length >= 2 && scene.description.length <= 1000);
    assert.equal(scene.sceneData.schemaVersion, 1);
    assert.ok(['template', 'builder', 'custom'].includes(scene.sceneData.kind));
    assert.equal(scene.thumbnail, `thumbnails/${scene.key}.png`, 'Thumbnail must be a relative fixture path.');
    assert.ok(scene.tags.length && scene.tags.every(tag => typeof tag === 'string' && tag.trim().length));
    assert.ok(users.has(scene.discussion.author) && scene.discussion.author !== scene.owner, 'Unknown discussion author.');
    assert.ok(scene.discussion.text && scene.discussion.reply);
    for (const field of ['likedBy', 'savedBy']) {
      assert.equal(new Set(scene[field]).size, scene[field].length, 'Duplicate engagement.');
      assert.ok(scene[field].every(key => users.has(key) && key !== scene.owner), 'Unknown engagement account.');
    }
    assert.ok(Number.isInteger(scene.views) && scene.views > 0);
  }
}

export async function loadFixtures() {
  const catalog = JSON.parse(await readFile(path.join(directory, 'catalog.json'), 'utf8'));
  validateRelationships(catalog);
  const thumbnails = new Map();
  for (const scene of catalog.scenes) {
    const bytes = await readFile(path.join(directory, scene.thumbnail));
    assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a', `${scene.key}: invalid PNG`);
    assert.ok(bytes.length > 1000 && bytes.readUInt32BE(16) >= 320 && bytes.readUInt32BE(20) >= 180, `${scene.key}: unusable thumbnail`);
    thumbnails.set(scene.key, { bytes, hash: sha256(bytes) });
  }
  return { catalog, thumbnails };
}
