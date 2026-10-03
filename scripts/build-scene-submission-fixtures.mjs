/** Maintainer-only snapshot builder. Reads source literals; never executes shaders. */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildSceneCatalog } from './quality-scene-catalog.mjs';

if (process.argv.length !== 3) {
  console.error('Usage: node scripts/build-scene-submission-fixtures.mjs <frontend-checkout>');
  process.exit(2);
}
const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const frontend = resolve(process.argv[2]);
const ts = createRequire(resolve(frontend, 'package.json'))('typescript');
const definitionPath = 'src/modules/player/templates/versions/v1/definitions.ts';
const source = await readFile(resolve(frontend, definitionPath), 'utf8');
const catalog = JSON.parse(await readFile(resolve(frontend, 'contracts/scenes/template-catalog.v1.json'), 'utf8'));
const ast = ts.createSourceFile(definitionPath, source, ts.ScriptTarget.Latest, true);
const shaders = new Map();
function readDefinitions(node) {
  if (ts.isObjectLiteralExpression(node)) {
    const fields = new Map(node.properties.filter(ts.isPropertyAssignment).map(property => [property.name.getText(ast), property.initializer]));
    const id = fields.get('templateId');
    const shader = fields.get('shader');
    if (id && shader) {
      assert.ok(ts.isStringLiteral(id) && ts.isNoSubstitutionTemplateLiteral(shader), 'Template definitions must use literal IDs and literal shader text.');
      shaders.set(id.text, shader.text);
    }
  }
  ts.forEachChild(node, readDefinitions);
}
readDefinitions(ast);
assert.equal(shaders.size, 16);
const sha256 = value => createHash('sha256').update(value).digest('hex');
const passOrder = ['glitchPass', 'bloom', 'RGBShift', 'dotShader', 'technicolorShader',
  'luminosityShader', 'afterImagePass', 'sobelShader', 'colorifyShader', 'halftonePass',
  'gammaCorrectionShader', 'kaleidoShader', 'copyShader', 'bleachBypassShader', 'toonShader', 'outputPass'];
// PP-B01 version-one default engine payload. This literal is a compatibility
// fixture only; the backend neither renders it nor resolves template source.
const defaultPayload = shader => ({
  visualizer: { shader, scale: 10, skyboxPreset: 6 },
  controls: { target0: { x: 0, y: 0, z: 0 }, position0: { x: 0, y: 0, z: 5.5 }, zoom0: 1 },
  intent: {
    time_multiplier: 1, minimizing_factor: 0.8, power_factor: 8, pointerDownMultiplier: 0,
    base_speed: 0.2, easing_speed: 0.6, camTilt: 0, camOrientationMode: 0,
    camOrientationSpeed: 1, autoRotate: true, autoRotateSpeed: 0.2, fov: 75,
  },
  fx: {
    passOrder: [...passOrder],
    bloom: { enabled: false, strength: 1, radius: 0.2, threshold: 0.1 },
    toneMapping: { method: 0, exposure: 1.5 },
    passes: {
      rgbShift: false, dot: false, technicolor: false, luminosity: false, afterImage: false,
      sobel: false, glitch: false, colorify: false, halftone: false, gammaCorrection: false,
      kaleid: false, bleachBypass: false, toon: false, outputPass: true,
    },
    params: {
      rgbShift: { amount: 0.005, angle: 0 }, afterImage: { damp: 0.96 },
      colorify: { color: '#ffffff' }, kaleid: { sides: 6, angle: 0 },
    },
  },
  state: { size: 0, pointerDown: 0, currPointerDown: 0, currAudio: 0, time: 0, volume_multiplier: 0 },
  audioResponse: 'legacy',
});
const builtins = catalog.templates.map(template => {
  const shader = shaders.get(template.templateId);
  assert.equal(sha256(shader), template.sourceSha256, `Source fingerprint changed: ${template.templateId}`);
  return { sceneId: `template:${template.templateId}@${template.templateVersion}`, sceneData: defaultPayload(shader) };
});
const demos = buildSceneCatalog().map((item, index) => ({
  sceneId: `quality:${item.visualFamily}:${String(index % 10).padStart(2, '0')}`, sceneData: item.sceneData,
}));
const output = resolve(repo, 'src/test/resources/scene-corpus');
await mkdir(output, { recursive: true });
const fixtures = { 'builtin-presets.json': builtins, 'demo-quality.json': demos };
const provenance = {
  formatVersion: 1,
  frontendCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: frontend, encoding: 'utf8' }).trim(),
  templateDefinitionsPath: definitionPath,
  templateDefaultsPath: 'src/modules/player/templates/resolveScene.ts',
  demoDefinitionsPath: 'scripts/quality-scene-catalog.mjs',
  demoDefinitionsSha256: sha256(await readFile(resolve(repo, 'scripts/quality-scene-catalog.mjs'))),
  fixtures: {},
};
for (const [filename, records] of Object.entries(fixtures)) {
  const content = '[\n' + records.map(record => JSON.stringify(record)).join(',\n') + '\n]\n';
  await writeFile(resolve(output, filename), content, 'utf8');
  provenance.fixtures[filename] = {
    scenes: records.length,
    fileSha256: sha256(content),
    records: records.map(record => ({
      sceneId: record.sceneId,
      sceneSha256: sha256(JSON.stringify(record.sceneData)),
      shaderSha256: sha256(record.sceneData.visualizer.shader),
    })),
  };
}
await writeFile(resolve(output, 'provenance.json'), JSON.stringify(provenance, null, 2) + '\n', 'utf8');
console.log(`Created ${builtins.length} preset and ${demos.length} demo snapshots. No shader source was executed.`);
