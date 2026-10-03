/** Read-only offline audit. The Java tool uses the same validator as scene saves. */
import { readFileSync } from 'node:fs';
import { mkdir, unlink, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { delimiter, dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..');
let built = false;
function prepareInventory() {
  if (built) return;
  const buildArguments = ['-q', '-DskipTests', 'compile', 'dependency:build-classpath',
    '-Dmdep.includeScope=runtime', '-Dmdep.outputFile=target/scene-inventory-classpath.txt'];
  // Windows requires cmd to launch its .cmd wrapper. This command is entirely
  // fixed text; the input filename never enters a shell command.
  const build = process.platform === 'win32'
    ? spawnSync('cmd.exe', ['/d', '/s', '/c', 'mvnw.cmd ' + buildArguments.join(' ')],
      { cwd: repo, stdio: ['ignore', 2, 2], windowsHide: true })
    : spawnSync('./mvnw', buildArguments, { cwd: repo, stdio: ['ignore', 2, 2] });
  if (build.error || build.status !== 0) {
    throw new Error('Inventory compilation failed. Check Java 21, Maven dependencies, and the build output.');
  }
  built = true;
}
function inspectFile(input, { build = true } = {}) {
  if (build) prepareInventory();
  let dependencies;
  try {
    dependencies = readFileSync(resolve(repo, 'target/scene-inventory-classpath.txt'), 'utf8').trim();
  } catch {
    throw new Error('Inventory classpath is missing. Run the audit without --no-build first.');
  }
  const audit = spawnSync('java', ['-cp', resolve(repo, 'target/classes') + delimiter + dependencies,
    'com.bdmage.mage_backend.tools.SceneSubmissionInventory', input],
  { cwd: repo, encoding: 'utf8', maxBuffer: 1024 * 1024, windowsHide: true });
  if (audit.error || ![0, 1, 2].includes(audit.status)) {
    throw new Error('Could not run the inventory tool. Check Java 21 and the inventory build.');
  }
  let report;
  try { report = JSON.parse(audit.stdout); }
  catch { throw new Error('The inventory tool did not return a valid JSON report.'); }
  return { status: audit.status, report };
}

/** Preflight a whole seed/import batch before any mutable API call. */
export async function validateSceneSubmissions(records, options = {}) {
  if (!Array.isArray(records) || records.length > 10_000) throw new Error('Scene preflight accepts at most 10000 records.');
  const data = JSON.stringify(records);
  if (Buffer.byteLength(data, 'utf8') > 64 * 1024 * 1024) throw new Error('Scene preflight export exceeds 64 MiB.');
  const temporary = resolve(repo, '.local', `scene-preflight-${randomUUID()}.json`);
  await mkdir(dirname(temporary), { recursive: true });
  await writeFile(temporary, data, { encoding: 'utf8', flag: 'wx' });
  try {
    const { status, report } = inspectFile(temporary, options);
    if (status !== 0) {
      const error = new Error(status === 1
        ? `Scene submission preflight rejected ${report.invalidScenes} record(s): ${JSON.stringify(report.failures)}`
        : 'Scene submission preflight could not read the batch.');
      error.report = report;
      throw error;
    }
    return report;
  } finally {
    // Only the fresh file created by this invocation is removed.
    await unlink(temporary);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const skipBuild = args[0] === '--no-build';
  if (skipBuild) args.shift();
  if (args.length !== 1) {
    console.error('Usage: node scripts/audit-scene-submissions.mjs [--no-build] <scene-export.json>');
    process.exit(2);
  }
  try {
    const { status, report } = inspectFile(resolve(args[0]), { build: !skipBuild });
    console.log(JSON.stringify(report));
    process.exitCode = status;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 2;
  }
}
