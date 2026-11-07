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

More in core, from round 2, to compare with 7e197c2b:
- `PlanGoal.WorkAnswer.doi` is a constrained `Doi`: same class as the model-answer citekeys. Every model-answer type needs its fields raw until normalised.
- `ManagerPolicy.released`: an `untilSource` that is not a valid citekey now counts as "source does not exist".
- `SourceMatcher.sha` uses `Sha256.option`: a malformed original sha no longer counts as a strong key.
- `seed:`/`watch:` manager-id format now exists twice (`ManagerRow.Id` and api `Goal.Ref`).
- `MergeFold.hull` picks min or max year instead of `math.min`/`max`.

More in core, from round 3, to compare with 7e197c2b:
- `Citekey.option` drops invalid citekeys: `SourcePlan` aliases can vanish; `CurrencyCheckRules` gives `Ignore` for an invalid citing key.
- `AcquireMatch.doiKeyOf`: a DOI trimmed to an empty suffix becomes `None`. `AgentVerify.linkProblems`: an invalid wikilink counts as unknown without asking `isCitekey`/`isSlug`.
- `CandidateDedupe` builds the arXiv key with `Doi.option`, so an id failing the DOI pattern is skipped.
- `AttestationPlan` lines 95-96: a `Local.Rev` passed as `ForeignRev` and a body sha as `leftRev`; check these against master rather than wrapping.
- Positional types kept because other files name them: `EntityOwnBundle`, `TombstoneOwnBundle`, `ChunkMarkdown`, `ChunkMaxChars`, `FoldS`, `TokensS`, `OfS`, `ValidCk`, `NormaliseS`, `NormaliseResult`.

Model-answer fields done right so far: `AcquisitionSweepRules.Pick.doi`, `AgentProposals.ProposalAnswer.citekey` (raw, parsed at the check). Found and fixed, generic-call class: `ActivityRules` `e.goal.contains(g)`, `e.by.contains(...)`.

From the core finisher, to compare with 7e197c2b: `MergePlanner` folds, renames and relinks go through `Citekey.option`, dropping a bundle citekey that fails `CitekeyRule` where master passed it through (bundles from another install could carry one: test this). The evidence check runs `Citekey.option` before `isCitekey`, so an invalid model citekey can get a different problem text.

## Blockers before any install

- Stored JSON must still load: `VerifiedFindings` lost its defaults ("checkpoint older than the counts"). Check every record stored as JSON (checkpoints, agent state, run outputs, bundles) that lost a default or gained a constraint in this migration, and test the decoders on real saved data in a restored copy.
- `parseRetractionWatch` no longer normalises DOIs, and a repair agent moved that into `AcquireSuite`. Restore it in main and remove it from the test: a retracted paper must not be missed.

Also check: `ModelChains.headroom` returns `Option[LimitState.FreesAt]` for a token headroom; `EventPrecision`, `Correction.Status`, `HintEffect` constants are plain strings; `AcquisitionControllers:357` has a mangled `Ident.doi(r.doi)`.

## From db and plugin-host, round 1 (to review against master before install)

- Reads through constrained types fail on rows the old code accepted: `.query[Citekey|Place.Id|Slug|Run.Id|Sha256]` in SourceRepo, PlaceRepo, PointAreaRepo, ArticleRepo, JobRepo, SourceDeleteRepo, SourceCheckRepo.stale, AcquireCheckRepo.heldByDoi, UndatedSiteRepo; goals, goal_links, goal_schedules slugs; `applyUnsafe` in SourceRepo.write, FindingHitRow.assignedPlaceIds, JobRepo.insertRun, PlaceRepo.rename; ApprovalRow.jobId as UUID.
- Dropped or changed: SourceCheckRepo.EdgeRows and SourceBundleRepo.unresolvedEdgesIn drop unknown edge kinds; Edge.Kind read with `getOrElse(References)`; SourceDeleteRepo no longer trims or filters blank sha256; neededBlobs drops malformed shas.
- SpiValues.latitude/longitude/area lost the range check by taking constrained types, but plugin values are unconstrained: the check must stay where a plugin's value enters the host, and the tests with 91.0 and 400 must keep passing.
- `math.max(1, limit)` became `limit.value` on PageSize: check nothing builds a PageSize with assume or from 0.
- AgentRepo reports using `api` types (`RunInfo`, `Goal.Slug`, ...) in rows: G7 review.
- Concepts split, bridged: Plugin.Info.Id vs util.plugins.Plugin.Id; Goal slug domain vs api; Job.Id / RunInfo.Id / Run.Id; Approval.State.Wire api vs domain; api Year vs DeepTime.SignedYear; HttpAnswer.Code vs HttpPolicy.StatusCode; AcquireConfig.UserAgent vs HttpPolicy.UserAgent; ProviderAsset.Bytes vs NewFigure.Bytes; ReportMaxItems vs Lint.ItemLimit; Place.Entry.SiteType vs SiteTypes.SiteType.Id; PlanQuotaRepo RefreshMode vs PlanQuota.RefreshMode.Wire; Pool.RefusalUntil vs EpochMillis; MediaGeoRow.Uid vs MediaItemRow.Uid.
