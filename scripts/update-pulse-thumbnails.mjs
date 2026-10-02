/**
 * Replace only unchanged, manifest-recorded local demo thumbnails.
 * Dry run is the default. --apply backs up originals before using thumbnail-only APIs.
 */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = path.join(repo, '.local/pulse-seed-manifest.json');
const pngSignature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function localUrl(value) {
  const url = new URL(value);
  assert.ok(['http:', 'https:'].includes(url.protocol), 'Only HTTP(S) URLs are allowed.');
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(url.hostname), 'Only loopback development URLs are allowed.');
  assert.ok(!url.username && !url.password, 'URL credentials are not allowed.');
  return url;
}

async function request(url, options = {}) {
  return fetch(localUrl(url), { ...options, redirect: 'error', signal: AbortSignal.timeout(15000) });
}

async function imageBytes(url) {
  const response = await request(url);
  assert.ok(response.ok, 'Thumbnail GET failed: ' + response.status);
  assert.ok(Number(response.headers.get('content-length') ?? 0) <= 16 * 1024 * 1024, 'Thumbnail is too large.');
  const bytes = Buffer.from(await response.arrayBuffer());
  assert.ok(bytes.length <= 16 * 1024 * 1024, 'Thumbnail is too large.');
  assert.ok(bytes.subarray(0, 8).equals(pngSignature), 'Thumbnail is not a PNG.');
  return bytes;
}

// Frozen legacy fixture generator, used only to identify old images byte-for-byte.
// Fresh seeds use the exact mockup PNGs instead.
const palettes = [[171,141,255],[75,216,199],[255,169,95],[106,174,250],[242,113,158],[190,220,132]];
const crcTable=Uint32Array.from({length:256},(_,n)=>{let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c>>>0;});
function chunk(type,data) {
  const contents=Buffer.concat([Buffer.from(type),data]); let crc=0xffffffff;
  for(const value of contents)crc=crcTable[(crc^value)&255]^(crc>>>8);
  const output=Buffer.alloc(data.length+12);output.writeUInt32BE(data.length);contents.copy(output,4);
  output.writeUInt32BE((crc^0xffffffff)>>>0,output.length-4);return output;
}
export function legacyThumbnail(index) {
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

export function identityMismatch(record, current) {
  if (!current) return 'scene-missing';
  if (current.sceneId !== record.sceneId) return 'scene-id-changed';
  if (current.ownerUserId !== record.ownerUserId) return 'owner-changed';
  if (current.thumbnailRef !== record.thumbnailRef) return 'thumbnail-reference-changed';
  return null;
}

async function writeReport(file, value) {
  const temporary = file + '.tmp';
  await writeFile(temporary, JSON.stringify(value, null, 2) + '\n');
  await rename(temporary, file);
}

export async function main(args = process.argv.slice(2)) {
  assert.ok(args.every(arg => arg === '--apply' || arg === '--dry-run'), 'Usage: node scripts/update-pulse-thumbnails.mjs [--apply | --dry-run]');
  assert.ok(!(args.includes('--apply') && args.includes('--dry-run')), 'Choose --apply or --dry-run, not both.');
  const apply = args.includes('--apply');
  const manifestBytes = await readFile(manifestPath);
  const manifest = JSON.parse(manifestBytes);
  const base = localUrl(process.env.MAGE_SEED_API_URL ?? 'http://127.0.0.1:8080/api/');
  assert.equal(base.href, localUrl(manifest.apiBase).href, 'API URL must match the original seed manifest.');
  assert.equal(manifest.version, 1, 'Unsupported seed manifest.');
  assert.ok(Array.isArray(manifest.scenes) && manifest.scenes.length === 30, 'Expected the original 30-scene seed manifest.');
  assert.ok(Array.isArray(manifest.users), 'Seed owners are missing.');
  assert.ok(typeof manifest.demoPassword === 'string' && manifest.demoPassword.length > 0, 'Demo password is missing.');
  assert.equal(new Set(manifest.scenes.map(scene => scene.sceneId)).size, manifest.scenes.length, 'Duplicate seed IDs.');
  const owners = new Map(manifest.users.map(user => [user.userId, user]));
  assert.equal(owners.size, manifest.users.length, 'Duplicate owner IDs.');
  for (const record of manifest.scenes) {
    assert.ok(Number.isSafeInteger(record.sceneId) && record.sceneId > 0, 'Invalid seed scene ID.');
    assert.ok(Number.isSafeInteger(record.ownerUserId) && record.ownerUserId > 0, 'Invalid seed owner ID.');
    const owner = owners.get(record.ownerUserId);
    assert.ok(owner && /^[a-z]+@pulse\.local$/.test(owner.email), 'Only recorded demo account owners are allowed.');
    localUrl(record.thumbnailRef);
  }

  async function api(route, { method = 'GET', token, body } = {}) {
    const response = await request(new URL(route, base), {
      method,
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!response.ok) throw new Error(method + ' ' + route + ': HTTP ' + response.status);
    return response.json();
  }

  const assets = await Promise.all(Array.from({ length: 5 }, async (_, index) => {
    const filename = String(index + 1) + '.png';
    const bytes = await readFile(path.join(repo, 'scripts/assets/pulse-thumbnails', filename));
    assert.ok(bytes.subarray(0, 8).equals(pngSignature), 'Invalid mockup asset: ' + filename);
    return { filename, bytes, sha256: sha256(bytes) };
  }));
  const scenes = await api('scenes');
  assert.ok(Array.isArray(scenes), 'Unexpected scene list.');
  const byId = new Map(scenes.map(scene => [scene.sceneId, scene]));
  const report = {
    version: 1, startedAt: new Date().toISOString(), mode: apply ? 'apply' : 'dry-run', apiBase: base.href,
    seedManifestSha256: sha256(manifestBytes), totalScenes: scenes.length,
    nonSeedScenesUntouched: scenes.filter(scene => !manifest.scenes.some(record => record.sceneId === scene.sceneId)).map(scene => scene.sceneId),
    scenes: [],
  };
  const candidates = [];
  for (const [index, record] of manifest.scenes.entries()) {
    const current = byId.get(record.sceneId);
    const asset = assets[index % assets.length];
    const entry = {
      sceneId: record.sceneId, ownerUserId: record.ownerUserId, name: current?.name ?? record.name,
      originalThumbnailRef: record.thumbnailRef, replacementAsset: asset.filename, replacementSha256: asset.sha256,
      status: 'skipped', reason: identityMismatch(record, current),
    };
    report.scenes.push(entry);
    if (entry.reason) continue;
    const original = await imageBytes(current.thumbnailRef);
    entry.originalSha256 = sha256(original);
    if (entry.originalSha256 === asset.sha256) {
      entry.reason = 'already-mockup-artwork';
      continue;
    }
    if (entry.originalSha256 !== sha256(legacyThumbnail(index))) {
      entry.reason = 'custom-or-unrecognized-thumbnail';
      continue;
    }
    entry.status = apply ? 'pending' : 'would-replace';
    entry.reason = null;
    candidates.push({ record, original, asset, entry });
  }

  const summary = () => ({
    mode: report.mode, eligible: candidates.length,
    replaced: report.scenes.filter(entry => entry.status === 'replaced').length,
    skipped: report.scenes.filter(entry => entry.status === 'skipped').length,
    nonSeedScenesUntouched: report.nonSeedScenesUntouched,
  });
  if (!apply || !candidates.length) {
    console.log(JSON.stringify({ ...summary(), scenes: report.scenes.map(({ sceneId, name, status, reason, replacementAsset }) => ({ sceneId, name, status, reason, replacementAsset })) }, null, 2));
    return report;
  }

  // Finalization deletes the old object. Back up every candidate before the first API write.
  const backupDirectory = path.join(repo, '.local/pulse-thumbnail-backups', new Date().toISOString().replace(/[:.]/g, '-') + '-' + process.pid);
  await mkdir(backupDirectory, { recursive: true });
  await writeFile(path.join(backupDirectory, 'seed-manifest.json'), manifestBytes, { flag: 'wx' });
  for (const { original, entry } of candidates) {
    entry.backupFile = entry.sceneId + '-original.png';
    await writeFile(path.join(backupDirectory, entry.backupFile), original, { flag: 'wx' });
    assert.equal(sha256(await readFile(path.join(backupDirectory, entry.backupFile))), entry.originalSha256, 'Backup verification failed.');
  }
  const reportPath = path.join(backupDirectory, 'report.json');
  await writeReport(reportPath, report);
  console.log('Originals and seed manifest backed up to ' + backupDirectory);
  const tokens = new Map();
  try {
    for (const { record, asset, entry } of candidates) {
      const owner = owners.get(record.ownerUserId);
      if (!tokens.has(owner.userId)) {
        const login = await api('auth/login', { method: 'POST', body: { email: owner.email, password: manifest.demoPassword } });
        assert.equal(login.userId, owner.userId, 'Logged-in user does not match the recorded seed owner.');
        assert.ok(login.accessToken, 'Login did not return an access token.');
        tokens.set(owner.userId, login.accessToken);
      }
      const token = tokens.get(owner.userId);
      const current = await api('scenes/' + record.sceneId);
      const mismatch = identityMismatch(record, current);
      if (mismatch || sha256(await imageBytes(current.thumbnailRef)) !== entry.originalSha256) {
        entry.status = 'skipped'; entry.reason = mismatch ?? 'thumbnail-changed-during-update';
        await writeReport(reportPath, report);
        continue;
      }
      const upload = await api('scenes/' + record.sceneId + '/thumbnail/presign', {
        method: 'POST', token, body: { filename: 'pulse-mockup-' + asset.filename, contentType: 'image/png', sizeBytes: asset.bytes.length },
      });
      assert.equal(upload.method, 'PUT', 'Unexpected upload method.');
      localUrl(upload.uploadUrl);
      entry.uploadObjectKey = upload.objectKey;
      entry.status = 'uploading';
      await writeReport(reportPath, report);
      const put = await request(upload.uploadUrl, { method: 'PUT', headers: upload.headers, body: asset.bytes });
      assert.ok(put.ok, 'Thumbnail upload failed: ' + put.status);
      // Recheck immediately before finalization so edits made during upload are left alone.
      const latest = await api('scenes/' + record.sceneId);
      const latestMismatch = identityMismatch(record, latest);
      if (latestMismatch || sha256(await imageBytes(latest.thumbnailRef)) !== entry.originalSha256) {
        entry.status = 'skipped'; entry.reason = latestMismatch ?? 'thumbnail-changed-during-upload';
        await writeReport(reportPath, report);
        continue;
      }
      entry.status = 'finalizing';
      await writeReport(reportPath, report);
      const updated = await api('scenes/' + record.sceneId + '/thumbnail/finalize', { method: 'POST', token, body: { objectKey: upload.objectKey } });
      entry.replacementThumbnailRef = updated.thumbnailRef;
      assert.equal(updated.sceneId, record.sceneId);
      assert.equal(updated.ownerUserId, record.ownerUserId);
      assert.equal(sha256(await imageBytes(updated.thumbnailRef)), asset.sha256, 'Replacement verification failed.');
      entry.status = 'replaced';
      await writeReport(reportPath, report);
      console.log('Updated scene ' + record.sceneId + ': ' + updated.name);
    }
    report.completedAt = new Date().toISOString();
    await writeReport(reportPath, report);
  } catch (error) {
    report.failedAt = new Date().toISOString();
    report.error = error.message;
    await writeReport(reportPath, report);
    console.error('Stopped safely. Check the per-scene status and recoverable originals at ' + reportPath);
    throw error;
  }
  // Keep the original manifest unchanged: reruns skip already-replaced references.
  console.log(JSON.stringify({ ...summary(), report: reportPath }, null, 2));
  return report;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
