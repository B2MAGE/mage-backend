/**
 * Populate the fresh local quality-review database with captured, rendered scenes.
 *
 * Before running: start the API on port 8080 with the dedicated volume below and
 * capture .local/quality-review/thumbnails/001.png through 100.png from the catalog.
 *   node scripts/seed-quality-local.mjs
 *   node scripts/seed-quality-local.mjs --resume   (only after an interrupted run)
 *   node scripts/seed-quality-local.mjs --validate-only
 *
 * This script never resets a database, removes a volume, or persists access tokens.
 * Users, scenes, uploads, tags and conversations use the public application APIs.
 * Local-only SQL adds historical dates and real engagement rows efficiently.
 */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { users as people, scenes as content } from './quality-scene-content.mjs';
import { buildSceneCatalog } from './quality-scene-catalog.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(repo, '.local', 'quality-review');
const checkpointPath = path.join(output, 'seed-checkpoint.json');
const manifestPath = path.join(output, 'seed-manifest.json');
const expectedVolume = 'mage-pulse-review-postgres-v2-quality';
const postgresContainer = 'mage-pulse-postgres';
const backendContainer = 'mage-pulse-backend';
const password = 'PulseDemo2026!';
const base = new URL(process.env.MAGE_SEED_API_URL ?? 'http://127.0.0.1:8080/api/');
const resume = process.argv.includes('--resume');
const validateOnly = process.argv.includes('--validate-only');
assert.ok(process.argv.slice(2).every(arg => ['--resume', '--validate-only'].includes(arg)), 'Unknown seed option.');
const day = 86_400_000;
const minute = 60_000;
const catalog = buildSceneCatalog();
const sha256 = value => createHash('sha256').update(value).digest('hex');
const fingerprint = sha256(JSON.stringify({ people, content, catalog }));
const iso = milliseconds => new Date(milliseconds).toISOString();
const id = value => {
  assert.ok(Number.isSafeInteger(value) && value > 0, `Expected a positive numeric database ID, received ${value}`);
  return String(value);
};
const sqlDate = value => {
  assert.match(value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
  return `'${value}'::timestamptz`;
};

function requireLoopback(url) {
  assert.ok(['http:', 'https:'].includes(url.protocol), 'Only HTTP(S) local URLs are permitted.');
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(url.hostname), `Refusing non-loopback target ${url.origin}`);
  assert.equal(url.username + url.password, '', 'Credentials must not be embedded in a URL.');
}
requireLoopback(base);
assert.equal(base.port, '8080', 'This seeder is restricted to the local review API on port 8080.');
assert.equal(base.pathname, '/api/', 'The API URL must end in /api/.');

async function api(route, { method = 'GET', token, body } = {}) {
  const response = await fetch(new URL(route, base), {
    method,
    redirect: 'error',
    signal: AbortSignal.timeout(30_000),
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const responseText = await response.text();
  if (!response.ok) throw new Error(`${method} ${route}: ${response.status} ${responseText.slice(0, 1000)}`);
  return responseText ? JSON.parse(responseText) : null;
}

function docker(args, input) {
  return execFileSync('docker', args, {
    encoding: 'utf8', input, windowsHide: true, timeout: 240_000,
    maxBuffer: 8 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'],
  }).trim();
}
function environment(container, name) {
  return container.Config.Env.find(entry => entry.startsWith(`${name}=`))?.slice(name.length + 1);
}

// Inspect the mounted volume and the API's actual connection before any write.
const postgres = JSON.parse(docker(['inspect', postgresContainer]))[0];
const backend = JSON.parse(docker(['inspect', backendContainer]))[0];
assert.ok(postgres.State.Running && backend.State.Running, 'Start both local review containers first.');
const mount = postgres.Mounts.find(item => item.Destination === '/var/lib/postgresql/data');
assert.equal(mount?.Type, 'volume', 'PostgreSQL must use the dedicated named review volume.');
assert.equal(mount?.Name, expectedVolume, `Refusing to seed any volume except ${expectedVolume}.`);
const volumeCreatedAt = JSON.parse(docker(['volume', 'inspect', expectedVolume]))[0].CreatedAt;
const database = environment(postgres, 'POSTGRES_DB');
const databaseUser = environment(postgres, 'POSTGRES_USER');
assert.ok(database && databaseUser, 'PostgreSQL database/user configuration is missing.');
const jdbc = environment(backend, 'SPRING_DATASOURCE_URL');
assert.ok(jdbc?.startsWith('jdbc:postgresql://'), 'The API must be configured for this local PostgreSQL container.');
const databaseUrl = new URL(jdbc.slice('jdbc:'.length));
assert.equal(databaseUrl.pathname.slice(1), database, 'API and inspected database names differ.');
const sharedNetworks = Object.keys(postgres.NetworkSettings.Networks)
  .filter(name => Object.hasOwn(backend.NetworkSettings.Networks, name));
assert.ok(sharedNetworks.length, 'API and PostgreSQL must share a local Docker network.');
assert.ok(sharedNetworks.some(name => {
  const network = postgres.NetworkSettings.Networks[name];
  return [postgresContainer, network.IPAddress, ...(network.Aliases ?? [])].includes(databaseUrl.hostname);
}), 'The API datasource does not point at the inspected local review database.');
const bindings = backend.NetworkSettings.Ports['8080/tcp'] ?? [];
assert.ok(bindings.some(binding => binding.HostPort === base.port), 'API port is not bound by the inspected review backend.');

function sql(statement) {
  return docker(['exec', '-i', postgresContainer, 'psql', '-X', '-A', '-t', '-q',
    '-v', 'ON_ERROR_STOP=1', '-U', databaseUser, '-d', database], statement);
}
function inventory() {
  return JSON.parse(sql(`SELECT json_build_object(
    'users', (SELECT COALESCE(json_agg(json_build_object('userId', id, 'email', email)), '[]'::json) FROM users),
    'scenes', (SELECT COALESCE(json_agg(json_build_object('sceneId', id, 'ownerUserId', owner_user_id, 'name', name)), '[]'::json) FROM scenes),
    'comments', (SELECT COUNT(*) FROM scene_comments),
    'views', (SELECT COUNT(*) FROM scene_views),
    'votes', (SELECT COUNT(*) FROM scene_votes),
    'saves', (SELECT COUNT(*) FROM scene_saves),
    'commentVotes', (SELECT COUNT(*) FROM scene_comment_votes));`));
}

assert.equal(people.length, 10);
assert.equal(content.length, 100);
assert.equal(catalog.length, 100);
assert.equal(new Set(content.map(scene => scene.name)).size, 100, 'Scene titles must be unique.');
people.forEach((_, ownerIndex) => assert.equal(content.filter(scene => scene.ownerIndex === ownerIndex).length, 10));
content.forEach((scene, index) => {
  assert.equal(scene.familyIndex, Math.floor(index / 10), 'Content/render families are misaligned.');
  assert.ok(scene.popularity >= 0 && scene.popularity <= 1);
  assert.ok(scene.description.length > 40 && scene.description.length <= 1000);
  assert.ok(scene.comments.length >= 2 && scene.comments.length <= 12);
  assert.equal(new Set(scene.comments.map(comment => comment.text)).size, scene.comments.length);
  assert.ok(catalog[index].sceneData?.visualizer?.shader, `Scene ${index + 1} has no renderable shader.`);
});

// A sample run, a failed render, or captures from an earlier catalog cannot seed.
const renderValidation = JSON.parse(await readFile(path.join(output, 'render-validation.json'), 'utf8'));
const catalogFingerprint = sha256(JSON.stringify(catalog));
assert.equal(renderValidation.catalogFingerprint, catalogFingerprint,
  'Render validation belongs to a different scene catalog. Capture all scenes again.');
assert.ok(Array.isArray(renderValidation.errors) && renderValidation.errors.length === 0,
  'The capture browser reported errors. Resolve them before seeding.');
assert.ok(Array.isArray(renderValidation.report) && renderValidation.report.length === content.length,
  'All 100 scenes must have a render-validation result; a sample capture is insufficient.');
assert.equal(new Set(renderValidation.report.map(result => result.index)).size, content.length,
  'Render-validation indices must be unique.');
for (const result of renderValidation.report) {
  assert.ok(Number.isInteger(result.index) && result.index >= 0 && result.index < content.length,
    'Render validation contains an invalid scene index.');
  assert.equal(result.valid, true, `Scene ${result.index + 1} failed render validation.`);
  assert.equal(result.filename, `${String(result.index + 1).padStart(3, '0')}.png`,
    `Scene ${result.index + 1} has an unexpected capture filename.`);
}

// Fail before registering anyone if a validated capture is missing or not an image.
const thumbnails = [];
for (let index = 0; index < content.length; index++) {
  const filename = `${String(index + 1).padStart(3, '0')}.png`;
  const bytes = await readFile(path.join(output, 'thumbnails', filename));
  assert.ok(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), `${filename} is not a PNG.`);
  const width = bytes.readUInt32BE(16);
  const height = bytes.readUInt32BE(20);
  assert.ok(width >= 320 && height >= 180 && Math.abs(width / height - 16 / 9) < 0.02, `${filename} must be a usable 16:9 scene capture.`);
  assert.ok(bytes.length > 1000, `${filename} is suspiciously small; verify its rendered content.`);
  thumbnails.push({ bytes, filename, width, height, sha256: sha256(bytes) });
}
const captureFingerprint = sha256(thumbnails.map(thumbnail => thumbnail.sha256).join('\n'));
console.log(`Preflight: 10 users, 100 scenes, 100 captured PNGs; dedicated volume ${expectedVolume}.`);

const before = inventory();
let checkpoint;
try { checkpoint = JSON.parse(await readFile(checkpointPath, 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
if (resume) {
  assert.ok(checkpoint, 'No matching checkpoint exists. A new run requires an empty database.');
  assert.equal(checkpoint.fingerprint, fingerprint, 'Seed content/catalog changed since the interrupted run.');
  assert.equal(checkpoint.captureFingerprint, captureFingerprint, 'Captured PNGs changed since the interrupted run.');
  assert.equal(checkpoint.databaseVolume, expectedVolume);
  assert.equal(checkpoint.volumeCreatedAt, volumeCreatedAt, 'The named volume has been recreated since the checkpoint.');
  assert.equal(checkpoint.apiBase, base.href);
  assert.ok(before.users.every(user => people.some(person => person.email === user.email)), 'Database contains an account outside this seed.');
  assert.ok(before.scenes.every(scene => {
    const expected = content.find(item => item.name === scene.name);
    const owner = before.users.find(user => user.userId === scene.ownerUserId);
    return expected && owner?.email === people[expected.ownerIndex].email;
  }), 'Database contains a scene outside this seed.');
} else {
  assert.equal(before.users.length + before.scenes.length + before.comments + before.views + before.votes + before.saves + before.commentVotes, 0,
    'Database is not empty. Nothing was reset. Use the fresh dedicated volume, or --resume for this script\'s interrupted run.');
  assert.ok(!checkpoint, 'An existing checkpoint must be reviewed before starting a new seed run.');
  checkpoint = {
    version: 1, fingerprint, captureFingerprint, databaseVolume: expectedVolume, volumeCreatedAt,
    apiBase: base.href, anchorTime: new Date().toISOString(), phase: 'ready', users: [], scenes: [],
  };
}
const visibleBefore = await api('scenes');
assert.deepEqual(visibleBefore.map(scene => scene.sceneId).sort((a, b) => a - b),
  before.scenes.map(scene => scene.sceneId).sort((a, b) => a - b), 'API and SQL inventories differ.');
if (validateOnly) {
  console.log('Read-only validation passed. No accounts, scenes or engagement were created.');
  process.exit(0);
}
assert.ok(!checkpoint.complete, 'This seed is already complete. No data was changed.');
await mkdir(output, { recursive: true });
const saveCheckpoint = () => writeFile(checkpointPath, JSON.stringify(checkpoint, null, 2) + '\n');
await saveCheckpoint();
const anchor = Date.parse(checkpoint.anchorTime);
const sessions = [];

for (let index = 0; index < people.length; index++) {
  const person = people[index];
  if (!before.users.some(user => user.email === person.email)) {
    await api('auth/register', { method: 'POST', body: { ...person, password } });
  }
  const session = await api('auth/login', { method: 'POST', body: { email: person.email, password } });
  assert.ok(session.accessToken && session.userId, `Registration/login failed for ${person.email}`);
  sessions.push(session);
  checkpoint.users[index] = {
    userId: session.userId, ...person,
    createdAt: iso(anchor - (370 + index * 3) * day),
  };
  checkpoint.phase = 'accounts';
  await saveCheckpoint();
  console.log(`[users ${index + 1}/10] ${person.displayName}`);
}

const tags = new Map((await api('tags')).map(tag => [tag.name.toLowerCase(), tag.tagId]));
for (const name of new Set(content.flatMap(scene => scene.tags))) {
  if (tags.has(name.toLowerCase())) continue;
  const tag = await api('tags', { method: 'POST', token: sessions[0].accessToken, body: { name } });
  tags.set(name.toLowerCase(), tag.tagId);
}

function sceneDate(index) {
  const ageRank = (index * 53 + 27) % 100;
  return anchor - (ageRank / 99) * 364 * day - 2 * 60 * minute - (index % 11) * minute;
}
const votePairs = [];
for (let upvotes = 0; upvotes <= 9; upvotes++) {
  for (let downvotes = 0; downvotes <= Math.min(4, 9 - upvotes); downvotes++) {
    votePairs.push({ upvotes, downvotes });
  }
}
assert.equal(votePairs.length, 40);

for (let index = 0; index < content.length; index++) {
  const definition = content[index];
  const owner = sessions[definition.ownerIndex];
  const thumbnail = thumbnails[index];
  let scene = visibleBefore.find(existing => existing.name === definition.name && existing.ownerUserId === owner.userId);
  if (scene) {
    assert.equal(scene.description, definition.description, `Existing description differs for ${definition.name}.`);
    assert.deepEqual(scene.sceneData, catalog[index].sceneData, `Existing render data differs for ${definition.name}.`);
  } else {
    const upload = await api('scenes/thumbnail/presign', {
      method: 'POST', token: owner.accessToken,
      body: { filename: `quality-${thumbnail.filename}`, contentType: 'image/png', sizeBytes: thumbnail.bytes.length },
    });
    requireLoopback(new URL(upload.uploadUrl));
    const put = await fetch(upload.uploadUrl, {
      method: upload.method, headers: upload.headers, body: thumbnail.bytes,
      redirect: 'error', signal: AbortSignal.timeout(30_000),
    });
    assert.ok(put.ok, `Thumbnail upload failed for ${definition.name}: ${put.status}`);
    scene = await api('scenes', {
      method: 'POST', token: owner.accessToken,
      body: {
        name: definition.name, description: definition.description,
        sceneData: catalog[index].sceneData, thumbnailObjectKey: upload.objectKey,
      },
    });
  }
  await api(`scenes/${scene.sceneId}/tags`, {
    method: 'PUT', token: owner.accessToken,
    body: { tagIds: definition.tags.map(name => tags.get(name.toLowerCase())) },
  });
  requireLoopback(new URL(scene.thumbnailRef));
  const image = await fetch(scene.thumbnailRef, { redirect: 'error', signal: AbortSignal.timeout(30_000) });
  assert.equal(image.status, 200, `Public thumbnail is unavailable for ${definition.name}.`);
  assert.match(image.headers.get('content-type') ?? '', /^image\/png/);
  assert.equal(sha256(Buffer.from(await image.arrayBuffer())), thumbnail.sha256, `Stored thumbnail differs for ${definition.name}.`);

  const published = sceneDate(index);
  const life = anchor - minute - published;
  const upvotes = 1 + Math.round(definition.popularity * 8);
  const downvotes = definition.popularity < 0.65 && index % 3 === 1 ? Math.min(9 - upvotes, 1 + index % 2) : 0;
  const record = {
    index, sceneId: scene.sceneId, ownerUserId: owner.userId,
    name: definition.name, createdAt: iso(published), popularity: definition.popularity,
    visualFamily: catalog[index].visualFamily, paletteName: catalog[index].paletteName,
    thumbnailRef: scene.thumbnailRef, thumbnailSha256: thumbnail.sha256,
    thumbnailWidth: thumbnail.width, thumbnailHeight: thumbnail.height,
    engagement: {
      views: 35 + Math.round(Math.pow(definition.popularity, 2.4) * 14_965),
      upvotes, downvotes, saves: Math.round(definition.popularity * 8),
    },
    comments: [],
  };
  const existingThreads = await api(`scenes/${scene.sceneId}/comments`);
  const existingComments = existingThreads.flatMap(parent => [parent, ...(parent.replies ?? [])]);
  let ordinal = 0;
  for (let commentIndex = 0; commentIndex < definition.comments.length; commentIndex++) {
    const authored = definition.comments[commentIndex];
    const authorIndex = (definition.ownerIndex + 1 + (index + commentIndex) % 9) % people.length;
    const author = sessions[authorIndex];
    const createdAt = published + life * (0.04 + 0.8 * commentIndex / (definition.comments.length + 1));
    let parent = existingComments.find(item => !item.parentCommentId && item.authorUserId === author.userId && item.text === authored.text);
    if (!parent) parent = await api(`scenes/${scene.sceneId}/comments`, {
      method: 'POST', token: author.accessToken, body: { text: authored.text },
    });
    const parentRecord = {
      commentId: parent.commentId, authorUserId: author.userId, parentCommentId: null,
      text: authored.text, createdAt: iso(createdAt),
      ...votePairs[(index * 7 + ordinal++ * 13) % votePairs.length],
    };
    record.comments.push(parentRecord);
    for (let replyIndex = 0; replyIndex < (authored.replies?.length ?? 0); replyIndex++) {
      const replyAuthor = replyIndex % 2 === 0 ? owner : author;
      const text = authored.replies[replyIndex];
      let reply = existingComments.find(item => item.parentCommentId === parent.commentId && item.authorUserId === replyAuthor.userId && item.text === text);
      if (!reply) reply = await api(`scenes/${scene.sceneId}/comments`, {
        method: 'POST', token: replyAuthor.accessToken, body: { parentCommentId: parent.commentId, text },
      });
      record.comments.push({
        commentId: reply.commentId, authorUserId: replyAuthor.userId, parentCommentId: parent.commentId,
        text, createdAt: iso(createdAt + Math.min(day, (anchor - createdAt) * 0.08) * (replyIndex + 1)),
        ...votePairs[(index * 7 + ordinal++ * 13) % votePairs.length],
      });
    }
  }
  assert.ok(existingComments.every(existing => record.comments.some(item => item.commentId === existing.commentId)),
    `Unexpected comments found on ${definition.name}; refusing to alter their history.`);
  assert.equal(new Set(record.comments.map(comment => `${comment.upvotes}/${comment.downvotes}`)).size, record.comments.length,
    'Each comment in a scene must have a distinct like/dislike pair.');
  checkpoint.scenes[index] = record;
  checkpoint.phase = 'scenes';
  await saveCheckpoint();
  console.log(`[scenes ${index + 1}/100] ${definition.name} — captured thumbnail, ${record.comments.length} comments/replies`);
}

// A single transaction applies historical dates and constraint-checked rows.
// The view insertion fills only the missing count so --resume cannot duplicate it.
const statements = ["BEGIN; SET LOCAL statement_timeout = '180s'; SET LOCAL lock_timeout = '10s';"];
for (const user of checkpoint.users) {
  statements.push(`UPDATE users SET created_at = ${sqlDate(user.createdAt)} WHERE id = ${id(user.userId)};`);
}
for (const scene of checkpoint.scenes) {
  const published = Date.parse(scene.createdAt);
  const life = anchor - minute - published;
  const voters = checkpoint.users.filter(user => user.userId !== scene.ownerUserId);
  const shiftedVoters = voters.map((_, index) => voters[(index + scene.index) % voters.length]);
  statements.push(`UPDATE scenes SET created_at = ${sqlDate(scene.createdAt)} WHERE id = ${id(scene.sceneId)};`);
  for (let voter = 0; voter < scene.engagement.upvotes + scene.engagement.downvotes; voter++) {
    const votedAt = sqlDate(iso(published + life * (0.08 + 0.085 * voter)));
    const value = voter < scene.engagement.upvotes ? 1 : -1;
    statements.push(`INSERT INTO scene_votes (scene_id, user_id, vote_value, created_at, updated_at)
      VALUES (${id(scene.sceneId)}, ${id(shiftedVoters[voter].userId)}, ${value}, ${votedAt}, ${votedAt})
      ON CONFLICT (scene_id, user_id) DO UPDATE SET vote_value = EXCLUDED.vote_value, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at;`);
  }
  for (let saver = 0; saver < scene.engagement.saves; saver++) {
    const savedAt = sqlDate(iso(published + life * (0.15 + 0.08 * saver)));
    statements.push(`INSERT INTO scene_saves (scene_id, user_id, created_at)
      VALUES (${id(scene.sceneId)}, ${id(shiftedVoters[(saver + 2) % shiftedVoters.length].userId)}, ${savedAt})
      ON CONFLICT (scene_id, user_id) DO UPDATE SET created_at = EXCLUDED.created_at;`);
  }
  const lastViewAt = sqlDate(iso(anchor - minute));
  statements.push(`INSERT INTO scene_views (scene_id, user_id, viewed_at)
    SELECT ${id(scene.sceneId)}, NULL, ${sqlDate(scene.createdAt)} + (${lastViewAt} - ${sqlDate(scene.createdAt)}) * (n::double precision / (${scene.engagement.views} + 1))
    FROM generate_series(1, GREATEST(0, ${scene.engagement.views} - (SELECT COUNT(*) FROM scene_views WHERE scene_id = ${id(scene.sceneId)}))) AS g(n);`);
  for (let commentIndex = 0; commentIndex < scene.comments.length; commentIndex++) {
    const comment = scene.comments[commentIndex];
    const commentVoters = checkpoint.users.filter(user => user.userId !== comment.authorUserId);
    const createdAt = Date.parse(comment.createdAt);
    statements.push(`UPDATE scene_comments SET created_at = ${sqlDate(comment.createdAt)} WHERE id = ${id(comment.commentId)} AND scene_id = ${id(scene.sceneId)};`);
    for (let voter = 0; voter < comment.upvotes + comment.downvotes; voter++) {
      const votedAt = sqlDate(iso(createdAt + (anchor - minute - createdAt) * (0.1 + 0.08 * voter)));
      const user = commentVoters[(voter + scene.index + commentIndex) % commentVoters.length];
      statements.push(`INSERT INTO scene_comment_votes (comment_id, user_id, vote_value, created_at, updated_at)
        VALUES (${id(comment.commentId)}, ${id(user.userId)}, ${voter < comment.upvotes ? 1 : -1}, ${votedAt}, ${votedAt})
        ON CONFLICT (comment_id, user_id) DO UPDATE SET vote_value = EXCLUDED.vote_value, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at;`);
    }
  }
}
statements.push('COMMIT;');
const historySql = statements.join('\n');
await writeFile(path.join(output, 'engagement.sql'), historySql + '\n');
// Recheck the mounted target immediately before applying the only direct writes.
const currentPostgres = JSON.parse(docker(['inspect', postgresContainer]))[0];
assert.equal(currentPostgres.Mounts.find(item => item.Destination === '/var/lib/postgresql/data')?.Name, expectedVolume);
console.log('Applying publication dates and varied engagement from the ten real accounts...');
sql(historySql);
checkpoint.phase = 'verifying';
await saveCheckpoint();

const after = inventory();
assert.equal(after.users.length, 10);
assert.equal(after.scenes.length, 100);
assert.equal(after.comments, checkpoint.scenes.reduce((count, scene) => count + scene.comments.length, 0));
for (const user of checkpoint.users) assert.equal(after.scenes.filter(scene => scene.ownerUserId === user.userId).length, 10);
const chronologyProblems = Number(sql(`SELECT
  (SELECT COUNT(*) FROM scenes s JOIN users u ON u.id = s.owner_user_id WHERE s.created_at < u.created_at OR s.created_at > now() OR s.created_at < now() - INTERVAL '366 days') +
  (SELECT COUNT(*) FROM scene_comments c JOIN scenes s ON s.id = c.scene_id LEFT JOIN scene_comments p ON p.id = c.parent_comment_id WHERE c.created_at < s.created_at OR c.created_at > now() OR c.created_at < p.created_at) +
  (SELECT COUNT(*) FROM scene_votes v JOIN scenes s ON s.id = v.scene_id WHERE v.created_at < s.created_at OR v.created_at > now()) +
  (SELECT COUNT(*) FROM scene_saves v JOIN scenes s ON s.id = v.scene_id WHERE v.created_at < s.created_at OR v.created_at > now()) +
  (SELECT COUNT(*) FROM scene_comment_votes v JOIN scene_comments c ON c.id = v.comment_id WHERE v.created_at < c.created_at OR v.created_at > now()) +
  (SELECT COUNT(*) FROM scene_views v JOIN scenes s ON s.id = v.scene_id WHERE v.viewed_at < s.created_at OR v.viewed_at > now());`));
assert.equal(chronologyProblems, 0, 'Historical dates are inconsistent.');
const publicScenes = await api('scenes');
assert.equal(publicScenes.length, 100);
for (const expected of checkpoint.scenes) {
  const actual = publicScenes.find(scene => scene.sceneId === expected.sceneId);
  assert.ok(actual, `API is missing ${expected.name}.`);
  assert.equal(Date.parse(actual.createdAt), Date.parse(expected.createdAt));
  assert.ok(actual.engagement.views >= expected.engagement.views, `View count is too low on ${expected.name}.`);
  for (const field of ['upvotes', 'downvotes', 'saves']) assert.equal(actual.engagement[field], expected.engagement[field], `${expected.name}: ${field}`);
  const threads = await api(`scenes/${expected.sceneId}/comments`);
  const comments = threads.flatMap(parent => [parent, ...(parent.replies ?? [])]);
  assert.equal(comments.length, expected.comments.length);
  for (const comment of expected.comments) {
    const actualComment = comments.find(item => item.commentId === comment.commentId);
    assert.ok(actualComment, `Missing comment ${comment.commentId}.`);
    assert.equal(actualComment.upvotes, comment.upvotes);
    assert.equal(actualComment.downvotes, comment.downvotes);
    assert.equal(Date.parse(actualComment.createdAt), Date.parse(comment.createdAt));
  }
}
const manifest = {
  version: 1, generatedAt: new Date().toISOString(), apiBase: base.href,
  databaseVolume: expectedVolume, fingerprint, captureFingerprint, catalogFingerprint,
  renderValidationCreatedAt: renderValidation.createdAt,
  featuredSceneId: checkpoint.scenes[0].sceneId,
  frontendEnvironment: { VITE_HOME_FEATURED_SCENE_ID: String(checkpoint.scenes[0].sceneId) },
  demoPassword: password, users: checkpoint.users, scenes: checkpoint.scenes,
  validation: {
    registeredUsers: after.users.length, sceneCount: after.scenes.length, scenesPerUser: 10,
    capturedThumbnailChecks: thumbnails.length, commentsAndReplies: after.comments,
    sceneViews: after.views, sceneVotes: after.votes, sceneSaves: after.saves, commentVotes: after.commentVotes,
    chronologyProblems, earliestPublication: [...checkpoint.scenes].sort((a, b) => a.createdAt.localeCompare(b.createdAt))[0].createdAt,
    latestPublication: [...checkpoint.scenes].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0].createdAt,
  },
};
await writeFile(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
checkpoint.phase = 'complete';
checkpoint.complete = true;
await saveCheckpoint();
console.log(`Complete: 10 users, 100 scenes, 100 captured thumbnails, ${after.comments} comments/replies, ${after.views.toLocaleString()} views.`);
console.log(`Featured: ${manifest.scenes[0].name} (scene ${manifest.featuredSceneId}).`);
console.log(`Local account: ari@pulse.local / ${password}`);
console.log(`Manifest: ${manifestPath}`);
