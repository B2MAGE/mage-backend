/**
 * Bounded, audio-reactive scenes for the local review dataset.
 * Every shape has a nonzero idle size, and the audio input is clamped before it
 * reaches geometry. No infinite grids, ray-direction deformations, or random
 * scene construction: these scenes stay in front of the camera at every phase.
 */
const PALETTES = [
  ['Lagoon', [0.12, 0.82, 0.86], [0.4, 0.38, 0.95], [0.8, 0.98, 1]],
  ['Ember', [0.96, 0.37, 0.12], [0.96, 0.7, 0.26], [1, 0.89, 0.65]],
  ['Orchid', [0.64, 0.35, 0.94], [0.96, 0.38, 0.7], [0.86, 0.77, 1]],
  ['Glacier', [0.17, 0.51, 0.94], [0.21, 0.85, 0.94], [0.79, 0.94, 1]],
  ['Fern', [0.27, 0.79, 0.41], [0.69, 0.85, 0.27], [0.85, 0.98, 0.65]],
  ['Rose', [0.95, 0.32, 0.5], [0.98, 0.61, 0.49], [1, 0.82, 0.85]],
  ['Dusk', [0.49, 0.39, 0.92], [0.93, 0.53, 0.26], [0.89, 0.82, 0.98]],
  ['Copper', [0.83, 0.46, 0.23], [0.27, 0.72, 0.67], [0.98, 0.83, 0.63]],
  ['Electric', [0.36, 0.83, 0.92], [0.84, 0.32, 0.91], [0.96, 0.9, 0.38]],
  ['Moonstone', [0.62, 0.73, 0.91], [0.6, 0.91, 0.82], [0.97, 0.96, 0.9]],
];

const PASS_ORDER = ['glitchPass', 'bloom', 'RGBShift', 'dotShader', 'technicolorShader',
  'luminosityShader', 'afterImagePass', 'sobelShader', 'colorifyShader', 'halftonePass',
  'gammaCorrectionShader', 'kaleidoShader', 'copyShader', 'bleachBypassShader', 'toonShader', 'outputPass'];

// The shader's built-in PBR adds bright ambient/edge lighting before output
// conversion. Author darker, saturated albedo so it retains the palette there.
const color = rgb => `color(${rgb.map(channel => Number((channel ** 2 * 0.65).toFixed(5))).join(', ')});`;
const decimal = value => Number(value.toFixed(5));

function buildShader(family, variation, palette) {
  const parts = [];
  const phase = decimal(variation * 0.23);
  const motion = decimal(0.12 + (variation % 4) * 0.025);
  const begin = (tone, transforms = '') => {
    parts.push(`reset(); rotateY(time * ${motion} + ${phase}); rotateX(0.22 + sin(time * 0.13 + ${phase}) * 0.12); ${transforms} ${color(palette[tone + 1])}`);
  };
  switch (family) {
    case 0: // Three independent orbital paths around a visible core.
      for (let ring = 0; ring < 3; ring++) {
        begin(ring, `rotateX(${decimal(Math.PI / 2 + ring * 0.57)}); rotateZ(${decimal(ring * 0.8)});`);
        parts.push(`torus((${decimal(0.88 + ring * 0.14)} + beat * 0.12), ${decimal(0.032 + (variation % 3) * 0.009)});`);
      }
      begin(2); parts.push('sphere(0.23 + beat * 0.2);');
      break;
    case 1: // Open nested cubes, never solid screen-filling boxes.
      for (let frame = 0; frame < 3; frame++) {
        begin(frame, `rotateZ(${decimal(frame * 0.25)} + time * ${decimal((frame - 1) * 0.035)});`);
        parts.push(`boxFrame(vec3(${decimal(0.4 + frame * 0.29)} + beat * 0.1), ${decimal(0.025 + frame * 0.008)});`);
      }
      begin(2); parts.push('sphere(0.11 + beat * 0.07);');
      break;
    case 2: // Seven spheres in a compact spatial cluster.
      begin(2); parts.push('sphere(0.34 + beat * 0.18);');
      for (let ball = 0; ball < 6; ball++) {
        const angle = ball * Math.PI / 3;
        begin(ball % 3, `displace(${decimal(Math.cos(angle) * 0.85)}, ${decimal(Math.sin(angle) * 0.85)}, ${decimal((ball % 2 ? 1 : -1) * 0.28)});`);
        parts.push(`sphere(${decimal(0.18 + (ball + variation) % 3 * 0.025)} + beat * 0.13);`);
      }
      break;
    case 3: // Two linked rings with contrasting orientations.
      begin(0, 'displace(-0.35, 0, 0); rotateX(PI / 2 + 0.2);');
      parts.push('torus(0.69 + beat * 0.1, 0.09 + beat * 0.045);');
      begin(1, 'displace(0.35, 0, 0); rotateZ(PI / 2 - 0.25);');
      parts.push('torus(0.69 + beat * 0.1, 0.09 + beat * 0.045);');
      begin(2); parts.push('sphere(0.15 + beat * 0.1);');
      break;
    case 4: // A bounded tower of five horizontal rings.
      for (let ring = 0; ring < 5; ring++) {
        begin(ring % 3, `displace(0, ${decimal((ring - 2) * 0.38)}, 0);`);
        parts.push(`torus(${decimal(0.52 + (2 - Math.abs(ring - 2)) * 0.16)} + beat * 0.15, 0.045 + beat * 0.025);`);
      }
      break;
    case 5: // Radial beads around a fine, face-on halo.
      begin(2, 'rotateX(PI / 2);'); parts.push('torus(0.96 + beat * 0.09, 0.022);');
      for (let bead = 0; bead < 8; bead++) {
        const angle = bead * Math.PI / 4 + variation * 0.04;
        begin(bead % 3, `displace(${decimal(Math.cos(angle) * 0.96)} * (1 + beat * 0.09), ${decimal(Math.sin(angle) * 0.96)} * (1 + beat * 0.09), 0);`);
        parts.push(`sphere(${decimal(0.105 + bead % 2 * 0.035)} + beat * 0.09);`);
      }
      break;
    case 6: // Three solid crystals, separated enough to read in a small card.
      for (let shard = 0; shard < 3; shard++) {
        begin(shard, `displace(${decimal((shard - 1) * 0.65)}, ${decimal(shard === 1 ? 0.18 : -0.13)}, 0); rotateZ(${decimal((shard - 1) * -0.3)}); rotateY(PI / 4);`);
        parts.push(`box(vec3(0.18 + beat * 0.06, ${decimal(shard === 1 ? 0.84 : 0.62)} + beat * 0.18, 0.18 + beat * 0.06));`);
      }
      break;
    case 7: // Layered concentric hoops, with a distinct flat silhouette.
      for (let hoop = 0; hoop < 4; hoop++) {
        begin(hoop % 3, `rotateX(PI / 2 + sin(time * 0.15 + ${decimal(hoop * 0.45)}) * 0.16);`);
        parts.push(`torus(${decimal(0.28 + hoop * 0.27)} + beat * ${decimal(0.04 + hoop * 0.04)}, ${decimal(0.028 + hoop * 0.006)});`);
      }
      break;
    case 8: // Five floating capped columns with different responsive heights.
      for (let pillar = 0; pillar < 5; pillar++) {
        begin(pillar % 3, `displace(${decimal((pillar - 2) * 0.43)}, 0, ${decimal((pillar % 2) * 0.13)});`);
        parts.push(`cylinder(0.11 + beat * 0.02, ${decimal(0.3 + (2 - Math.abs(pillar - 2)) * 0.19)} + beat * ${decimal(0.13 + pillar * 0.025)});`);
      }
      break;
    case 9: // A bright sphere suspended in a rotating open cage.
      begin(0, 'rotateZ(time * 0.06 + 0.3);'); parts.push('boxFrame(vec3(0.84 + beat * 0.09), 0.035 + beat * 0.018);');
      begin(1, 'rotateX(PI / 2);'); parts.push('torus(0.61 + beat * 0.14, 0.055);');
      begin(2); parts.push('sphere(0.28 + beat * 0.21);');
      break;
    default: throw new Error(`Unknown visual family ${family}`);
  }
  return [
    'setMaxIterations(100); setStepSize(0.7);',
    'let size = input(); let pointerDown = input();',
    'let beat = min(max(size, 0), 1);',
    // Modest material settings preserve visible color instead of mirror-black shapes.
    'metal(0.03); shine(0.15);',
    ...parts,
  ].join('\n');
}

export const VISUAL_FAMILIES = [
  'orbital-rings', 'nested-cube-frames', 'sphere-cluster', 'linked-tori', 'ring-tower',
  'radial-beads', 'crystal-boxes', 'concentric-hoops', 'floating-pillars', 'caged-core',
];

export function buildSceneCatalog() {
  return Array.from({ length: 100 }, (_, index) => {
    const family = Math.floor(index / 10);
    const variation = index % 10;
    const palette = PALETTES[(variation + family * 3) % PALETTES.length];
    return {
      visualFamily: VISUAL_FAMILIES[family],
      paletteName: palette[0],
      sceneData: {
        visualizer: { shader: buildShader(family, variation, palette), skyboxPreset: 6, scale: 1.25 },
        controls: { target0: { x: 0, y: 0, z: 0 }, position0: { x: 0, y: 0.2, z: 5 }, zoom0: 1 },
        intent: {
          time_multiplier: 0.65 + variation * 0.025,
          minimizing_factor: 1, power_factor: 2, pointerDownMultiplier: 0,
          base_speed: 0.12, easing_speed: 0.55,
          camTilt: 0, camOrientationMode: 0, camOrientationSpeed: 0,
          autoRotate: false, autoRotateSpeed: 0, fov: 50,
        },
        fx: {
          passOrder: [...PASS_ORDER],
          bloom: { enabled: true, strength: 0.12, radius: 0.2, threshold: 0.8 },
          toneMapping: { method: 4, exposure: 0.55 },
          passes: {
            rgbShift: false, dot: false, technicolor: false, luminosity: false,
            afterImage: false, sobel: false, glitch: false, colorify: false,
            halftone: false, gammaCorrection: false, kaleid: false, outputPass: true,
          },
          params: {
            rgbShift: { amount: 0, angle: 0 }, afterImage: { damp: 0.8 },
            colorify: { color: '#ffffff' }, kaleid: { sides: 6, angle: 0 },
          },
        },
        state: { size: 0, pointerDown: 0, currPointerDown: 0, currAudio: 0, time: variation * 0.7, volume_multiplier: 0 },
      },
    };
  });
}
