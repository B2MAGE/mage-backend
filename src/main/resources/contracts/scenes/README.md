# Scene contract resources

SB-01 adds `kind: "builder"` with explicit `builderVersion: 1`. See
[the shared Builder format](builder-v1.md) for stable IDs, operations, settings,
bindings, limits, migration policy and the storage-versus-rendering boundary.
SB-02 adds `builder-rendering.v1.json`, applies its expanded workload policy at
the API boundary, and authorizes validated `builder-v1` playback independently
of the custom-source switch. The contract resources are copied from the frontend repository.

`scene-v1.schema.json`, `template-catalog.v1.json`, and `builder-rendering.v1.json` are exact copies of the
frontend's `contracts/scenes` resources. Shared conformance cases are copied to
`src/test/resources/contracts/scenes/fixtures.json`. Keep these files synchronized
when adding a supported schema or template version. The catalog contains IDs,
versions, and source checksums; the API does not need or execute template shaders.

`SceneDocumentValidator.validateAndNormalize` is the write boundary. It rejects
legacy bare engine data for new submissions, validates the explicit template, builder or
custom envelope, and fills published defaults from the schema without mutating the
submitted document. Custom documents additionally pass the existing submission
policy; the whole envelope counts toward byte, depth, field, and node budgets.
`validateContract` exists for structural conformance tests and must not replace
the write boundary: a transport-valid custom object can still violate resource
or required-engine-field rules.

The validator implements only the checked-in schema's required features:
local `$defs`/`$ref`, the root template/custom/builder `oneOf`, `anyOf`, `not`, JSON types,
`const`, `enum`, object `properties`/`required`/`additionalProperties`/
`propertyNames`, array `items`/`maxItems`/`uniqueItems`, numeric `minimum`/`maximum`, string
`pattern`/`minLength`/`maxLength`, and recursive `default` materialization.
`x-uniqueBy` enforces unique audio mapping targets, Builder IDs and bound properties; `x-maxOptionalEffects` counts
bloom, tint and enabled optional passes together. These application-specific
keywords are also enforced by the frontend parser and its schema conformance tests.
Title, description, dialect, and ID are annotations. It rejects unknown keywords,
external references, reference siblings, unsupported types, and nested `oneOf`
at initialization. It is not intended as a general JSON Schema implementation.

Validation does not grant execution permission, inspect custom source for safety,
or promote custom source to template trust when its contents match a catalog hash.
Server-owned classification and the independent availability controls remain the
responsibility of the service and persistence layers.
