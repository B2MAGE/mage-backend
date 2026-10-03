# Scene contract resources

`scene-v1.schema.json` and `template-catalog.v1.json` are exact copies of the
frontend's `contracts/scenes` resources. Shared conformance cases are copied to
`src/test/resources/contracts/scenes/fixtures.json`. Keep these files synchronized
when adding a supported schema or template version. The catalog contains IDs,
versions, and source checksums; the API does not need or execute template shaders.

`SceneDocumentValidator.validateAndNormalize` is the write boundary. It rejects
legacy bare engine data for new submissions, validates the explicit template or
custom envelope, and fills template defaults from the schema without mutating the
submitted document. Custom documents additionally pass the existing submission
policy; the whole envelope counts toward byte, depth, field, and node budgets.
`validateContract` exists for structural conformance tests and must not replace
the write boundary: a transport-valid custom object can still violate resource
or required-engine-field rules.

The validator implements only the checked-in schema's required features:
local `$defs`/`$ref`, the root template/custom `oneOf`, `anyOf`, `not`, JSON types,
`const`, `enum`, object `properties`/`required`/`additionalProperties`/
`propertyNames`, array `items`, numeric `minimum`/`maximum`, string
`pattern`/`minLength`/`maxLength`, and recursive `default` materialization.
Title, description, dialect, and ID are annotations. It rejects unknown keywords,
external references, reference siblings, unsupported types, and nested `oneOf`
at initialization. It is not intended as a general JSON Schema implementation.

Validation does not grant execution permission, inspect custom source for safety,
or promote custom source to template trust when its contents match a catalog hash.
Server-owned classification and the independent availability controls remain the
responsibility of the service and persistence layers.
