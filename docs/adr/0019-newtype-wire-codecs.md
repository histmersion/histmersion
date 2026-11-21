# 0019. Unconstrained newtypes cross the wire unchecked; only they take a text codec

- Status: accepted
- Date: 2026-11-21

## Context

Every newtype gets its JSON codec, key codec and tapir schema from its base type's (ADR-0009). The wire contract's
newtypes (the `api` module) are unconstrained (`Pure`): the client copies judge nothing, and the server validates where
an api value becomes a domain value. The `api` module is also compiled to Scala.js for the web page, so whatever its
codecs call ends up in the page's bundle. Query parameters and path segments need tapir text codecs too.

## Decision

- A newtype's JSON and schema are its base type's; a constrained newtype checks its constraint on decoding. An
  unconstrained (`Pure`) newtype decodes as its base type does, with no check: there is nothing to check, and not calling
  Iron's `either` keeps every companion's runtime constraint out of the web page's bundle.
- Only an unconstrained newtype has a tapir text codec (`NewtypeCodecs.plain`), so a query parameter or path segment is
  read as its base type's text with no check; the server checks once, where the api value becomes a domain value.
- A `Problem` inside a wire record is written typed, as a failed request's body lists one (`Answer.Entry`).

## Consequences

A new query parameter of a wire newtype needs no codec of its own. A constrained newtype cannot be a query parameter
directly: the endpoint takes the wire type, and the server converts.

## Code

`modules/api/src/main/scala/com/histmersion/api/json/NewtypeCodecs.scala` (`plain`), `api/log/LogApi.scala`.
