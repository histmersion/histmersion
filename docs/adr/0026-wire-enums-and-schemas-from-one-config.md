# 0026. A wire enum of words is a string; codecs and schemas read one config, and a refused input is a problem

- Status: accepted (queue row 34, 2026-11-23)
- Date: 2026-11-23

## Context

Kindlings derives a record's JSON codec (`JsonCodec`) and its tapir schema (`KindlingsSchema`) from the same type, so
they agree only when both derivations are told the same `JsoniterConfig`. The codec is always given its config
(`JsonCodec.config`). The schema macro looks the config up implicitly where it expands, and finds Kindlings' own
`JsoniterConfig.default` (a companion given) wherever no other one is in scope. That default writes an enum whose cases
all lack fields as strings and wraps nothing, while the codec wrote `{"Owner":{}}`; an `Option` field and a collection
field were also described differently. Files under `package com.histmersion` / `package api.research` (the research
state, the structured log) and every file without `import com.histmersion.api.given` derived their schemas this way,
so `openapi.json` declared `"Owner"` where the server sent `{"Owner":{}}`.

A request the server could not decode (an unknown `who=` value, an unreadable number, a broken JSON body) was answered
by tapir's default handler: 400 with the plain text `Invalid value for: query parameter who`, unlike every other refusal,
which is the problem body (`Api.Error`).

## Decision

- An enum whose cases all lack fields is written as its case's name (`"Owner"`). A sum type with any case that has
  fields is wrapped in its case's name, `{"Case":{…}}`, a case without fields as `{"Case":{}}`. The rule is the codec
  config's (`withEnumAsStrings` beside `withCirceLikeObjectEncoding`); an enum of words still reads the wrapped form
  that earlier versions stored, so stored JSON, bundles and checkpoints read back.
- There is one config, `JsonCodec.schemas`; every `JsoniterConfig` given is that value (the api package's, which
  `import com.histmersion.api.given` brings, the MCP tool inputs', and two package-private ones of the agents' tool
  packages). `ContractsSuite` rule `schema-config` fails a file that derives a schema (names `KindlingsSchema` outside
  an import) without one of them in scope — imported, or given in its own package — and a `JsoniterConfig` given that
  is anything but `JsonCodec.schemas`. (A given in the root package, seen by every file, would need no import, but
  the api module keeps its files in `com.histmersion.api`.)
- The OpenAPI document marks an `Option` as nullable, as the codecs write `None` as `null`, and documents a decode
  failure as the problem body.
- `WireContractSuite` proves the agreement for every JSON body of every endpoint in `Api.all`: from each body's schema
  in the OpenAPI document it generates instances until every enum value, every `oneOf` branch and every nullable
  alternative has been used, decodes each with the endpoint's codec, encodes the value back and validates the result
  against the schema.
- A decode failure is answered by `DecodeFailures.handler`: tapir's default decides whether to answer and with which
  status (400, 413, 415, or the next endpoint), and the body is `Api.Error` with the failure as a problem
  (`Invalid.Unknown` for a value outside an enumeration, `Invalid.Missing`, `Invalid.Unreadable`, `Invalid.Unparsable`
  for a JSON body, `Invalid.OutOfRange` for a bound).

## Consequences

Wire output changed for every enum of words that had no `WireEnum` codec (unreleased: allowed). A new file deriving a
schema without the config fails the contract rule instead of silently deriving with Kindlings' default.

## Code

`modules/api/src/main/scala/com/histmersion/api/Write.scala` (the api package's given),
`modules/json/src/main/scala/com/histmersion/util/json/JsonCodec.scala` (`config`),
`modules/server/src/main/scala/com/histmersion/util/problem/DecodeFailures.scala`,
`modules/server/src/main/scala/com/histmersion/server/Composition.scala` (`decodeFailureHandler`),
`modules/server/src/test/scala/com/histmersion/server/WireContractSuite.scala`,
`modules/server/src/test/scala/com/histmersion/server/OpenApiDocument.scala`.
