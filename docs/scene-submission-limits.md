# Scene submission limits (PP-V01)

New scene content passes the same policy on `POST /api/scenes` and
`PUT /api/scenes/{id}`. The request filter bounds input before Spring creates a
request DTO or an unbounded map. `SceneService` then validates scene data before
thumbnail finalization, playlist attachment, entity mutation, or persistence.
Future fork, restore, revision, and import writers must use that same service
boundary. Metadata-only changes do not rewrite or revalidate legacy scene data.

The authoritative, versioned policy is
[`src/main/resources/scene-limits.v1.json`](../src/main/resources/scene-limits.v1.json).
The validator, request guard, and offline inventory tool load limits from that
file. New writes are rejected when they exceed a limit; values are not clamped,
silently removed, or converted from strings.

## Size and structure

| Limit | Version 1 value |
| --- | --- |
| Request body | 524,288 bytes (512 KiB), both encoded and decoded |
| Compact serialized `sceneData` | 262,144 UTF-8 bytes (256 KiB) |
| `visualizer.shader` text | 65,536 UTF-8 bytes (64 KiB) |
| Scene container nesting | 16, counting the scene root as level 1 |
| Items per array | 64 |
| Fields per object | 64 |
| Total scene object fields | 512 |
| Total scene JSON values/containers | 2,048 |
| Field name | 64 UTF-8 bytes |
| Enabled optional effects | 4, including bloom |

These are byte limits, not Java character counts. Multibyte text consumes its
actual UTF-8 length. Scene serialization includes JSON escaping; request size
also includes whitespace and metadata. The complete request gets one additional
container level around `sceneData`.

The filter reads at most one byte beyond the request limit, regardless of a
missing or incorrect `Content-Length`. It supports uncompressed JSON and a
single `gzip` content encoding; both compressed and expanded data are bounded.
Chunked requests are measured as they arrive through the servlet input stream.
Unsupported encodings or non-UTF-8 charsets return 415. Malformed gzip, invalid
UTF-8/JSON, duplicate JSON keys, and trailing JSON values are rejected. HTTP
framing, including conflicting transfer/length headers, remains the servlet
container's responsibility; the filter cannot interpret bytes outside its body.

## Accepted scene data

The custom envelope's `scene` object requires a nonblank string at
`visualizer.shader`. Accepted root branches are `visualizer`, `controls`,
`intent`, `fx`, `state`, `audioResponse`, and `audioResponseConfig`. Each branch
has an explicit nested allowlist, type, and numeric range in the policy.
Numbers must be finite. Colors use exactly `#RRGGBB`. A supplied `controls`
branch must include `position0`, `target0`, and `zoom0`; each vector requires
all three numeric coordinates. The entire branch may be omitted. Unknown fields, prototype
keys, source aliases, external asset URLs/imports, and arbitrary renderer
configuration are rejected.

PP-B02 requires a versioned template/custom envelope around new submissions;
see [scene documents](scene-documents.md). This policy validates the custom
envelope's inner engine data and the complete envelope's resource budgets.
Passing this policy never grants trust to shader source.

`fx.passOrder` accepts the 16 engine pass IDs listed in the policy, at most once
each. Omitted pass order uses the engine's existing order; subsets are permitted
and the engine supplies omitted passes. The length of this list is not the
enabled-effect count. Bloom and true optional flags count toward the four-effect
budget; required output/copy passes are excluded. Disabled effects must still
have valid parameter values. Omitted optional effects default off; output defaults
on. Tests cover the default scene and the complete built-in/demo corpus.

## Runtime agreement with PP-V02

| Runtime ceiling | Full player | Preview |
| --- | --- | --- |
| Render pixels | 2,073,600 | 230,400 |
| Longest edge | 1,920 pixels | 640 pixels |
| Device pixel ratio | 1.5 | 1.5 |
| Frames per second | 60 | 30 |
| Raymarch iterations | 200 | 200 |

These values are a checked-in agreement for PP-V02, not a claim that this
backend change constrains graphics execution. The initial 128-step candidate
was increased to 200 because six immutable built-in templates request 133–198
iterations. Lowering their effective limit would change their appearance.
PP-V02 must enforce these ceilings in the renderer, including when source-level
calls request more work. Submitted resolution, DPR, quality, or render-step
overrides are not part of the current engine scene contract and are rejected.

The server never evaluates or compiles source and does not use a source regex
as a security scanner or cost estimator. A small source string can still be
expensive or unsafe. Client preflight, runtime limits, and custom-code isolation
remain required by the later PP stories.

## Errors and compatibility

Body-size failures return `413 REQUEST_TOO_LARGE`. Scene/structural policy failures
return the existing `400 VALIDATION_ERROR` envelope with a `details` map naming
the affected field and limit. Malformed transport JSON returns
`400 MALFORMED_REQUEST`. Diagnostics do not echo source or submitted values;
overlong or unusual field names are represented by their parent path.

PP-B02 preserves existing documents with a server-owned legacy classification.
Unsupported source remains available through owner-only repair access. The
read-only audit and its measured compatibility report are documented in
[`scene-submission-inventory.md`](scene-submission-inventory.md). Operators can
run it against a database/API export before rollout. New content replacements
must comply, including when replacing an older record.

The local seed tools run the same Java validator before their first write and
also submit through the validated API. Run the seed tools only when intentionally
creating development fixtures; the inventory command itself performs no writes.
