# Obligations that must not get lost

## Validation moved from the API layer to the server (decision #8868)

Each check an API-layer test used to make must exist as a server test once the server compiles. A G8 row is not done until these exist.

- `TargetSuite`: a target with `lat: 91` is refused with a 400 where the server turns the API value into a domain value.
- plugin-api `ValuesSuite`: coordinate, DOI and Wikidata id checks (three expectations, from the handover).

## Audit before the refactor is called done: places the compiler cannot check

1. Generic calls across types: `Option.contains`, `indexOf`, `Set`/`Map` lookups, `==`, `getOrElse` widening to a union. Known: `MergeApplyJob.of` (`error.contains(JobState.Interrupted)`), `Storage.onVolume` (`e.volume.contains(volume)`).
2. Code changed to plain types or bridged with `X(y.value)`, where a swap no longer shows.
3. String interpolation of an enum or newtype, which prints `toString`. Known: `Edge.Kind` printed `Challenges` for `challenges`.

Suspected wrong merges to check: `PeriodO.int` returns `Year` for `yearPublished`; `Queue.createdAtOf` and `Tree.timeTime` carry mangled wrappers.

4. A newtype with its own codec can lose it to the generic `NewtypeCodecs` given, silently dropping normalisation (found: `ItemKey`'s trimming decoder in `Target`). Check every hand-written codec on a newtype is the one actually used.

## Behaviour changes made by repair agents, to review against 7e197c2b

- `BundleTar.parseHeader` reports a negative octal size as unreadable; master accepted it.
- `s2Next` uses `Offset.option`, so a negative value is `None` where it threw before.

## Types agents reverted to primitives on public signatures, to restore

`BundleEntities` (problems, `payloads`, `decode`), `SecretForm`, `Jats` (`runText`, attributes), `BundleReader.Loaded.problems`, `Compatible`, `checkFiles`, `ProviderKind.names`, `AboutMarkdown.age`.

Found and fixed, generic-call class: `RepositoryLanding` `g.contains(d)` compiled as a character test and was always false.
Review too: `StorageConfig.lowFreeBytes` uses `ByteCount.option(...).getOrElse(zero)`, so a negative setting silently becomes zero.

5. `X.option` / `X.either` on a type that lost its constraint now always succeeds, silently dropping validation (decision #8868's side effect). Found: `WorkLookup.key` with plugin-api's `Doi`; restored at the call. Check every such call against 7e197c2b: a check it relied on must move to the boundary.

Test input to review: `BundleTar.header(..., ByteCount.assume(-1L))` keeps the old `isLeft` assertion past the constraint; compare with the `parseHeader` behaviour note above.

## Regressions to restore in core

- Model answers (`EvidenceAnswer`, `SpotCheckAnswer`, `RejectionAnswer`): citekey fields became refined `Citekey`, so stripping `[[x]]`, trimming and lower-casing are gone and a bracketed citekey fails decoding. These answers are a boundary: accept raw text, normalise, then produce the `Citekey`. Check against 7e197c2b.
- `ArticleDraft.notUsed` holds `"ck: reason"` lines typed as `Citekey`, and `problems`, `section`, `text`, `filter` are wrapped the same way: give them real concepts.
- `MergePlanner` `matched0` went to `List[String]`; `AgentVerify` is reached through private String helpers in `ReviewArticle` (G3 will move these onto types).

Found and fixed, generic-call class: `Merged.deferred.contains(f.id)` was always false.
