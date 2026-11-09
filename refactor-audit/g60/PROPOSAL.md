# G60: one closed `Problem` for the whole app

Status: design proposal for review. Nothing in the tree is changed by it.
Evidence: `refactor-audit/g60/sites.tsv` (2,510 construction sites in 527 main files, extracted by script).

## 0. Summary

- Today a failure is a sentence. Five unrelated `Problem` string newtypes, `Api.Error` (message + code string), `Cli.Error` (message + exit code + two flags) and about 24 per-feature enums all end in text, and code that needs to branch re-reads that text (`Api.Error.isNotFound` looks at a code string, `Stopped.cause` parses a stored sentence).
- Proposal: one enum `Problem` with **11 groups and about 40 cases**, each case holding typed data. Text is produced at six edges only (HTTP JSON, CLI, MCP, log, stored column, web). A failure that can have several causes is `Problems = Problem | NonEmptyVector[Problem]`.
- Of the 2,510 sites, **1,714 map to a case**, 87 are not failures at all (explanations typed as `Problem`), 651 only re-wrap or prefix another failure's text, and 52 I could not sort by rule.
- **219 sites carry outside text** (exception messages, stderr, reply bodies, plugin text). Those become five small newtypes inside the cases that are shown only at an edge and never branched on.
- The new module is a cross-built `problem` (JVM + JS) that depends on `newtype` only. Plugins reach it through `plugin-api`.
- The rule-based grouping is a first pass. I spot-checked about 190 sites and found roughly 80% in the right case; the rest are mis-sorted at the margin between neighbouring cases (for example `Invalid.Unknown` against `Invalid.Missing`), not across groups. The group list is stable; the per-case counts are +-20%. Treat the counts as sizing, not as a bill of quantities.

## 1. What is there now

| thing | count (main code) |
|---|--:|
| `Problem(...)` constructions | 1,757 (+ 33 of two nested `Problem` newtypes: `Finding.Problem`, `DownloadedGazetteer.Problem`) |
| `Api.Error(...)` constructions | 333 (plus helpers `Write.invalid`, `Write.notFound` and about 20 private `bad`/`refused`/`invalid` wrappers) |
| `Cli.Error(...)` constructions | 112 |
| closed-enum case constructions (`Failure.*`, `SettingError.*`, `Cause.*`, `PauseReason.*`, `Why.*`, `Refusal.*`, ...) | 247 |
| `Either[Problem, _]` / `Either[Api.Error, _]` / `Either[Cli.Error, _]` in signatures | 905 / 677 / 322 |
| tests that build a `Problem(` | 198 files, 884 lines (not in `sites.tsv`) |

The figure of 3,138 in the brief includes tests and the definitions. Counting only constructions in `src/main` gives the table above. `Api.Error` and `Cli.Error` sites build their inner `Problem(...)` in the same expression; I counted each such expression once.

Three more facts that shape the design:

1. **`Problem` is used as "a reason", not only as a failure.** 87 sites (`GoalFocus`, `MediaMatch`, `CatalogMerge`, `SourcePlan`, `MergePlanner`, `EventProviders`, `ReviewManagerPolicy`) are narrations: "merged with another record", "its place id is ambiguous on the map", "back in the queue". They explain a decision that succeeded. They need their own per-feature types, not a place in `Problem`.
2. **At least 537 sites only convert between the five `Problem` copies** (`Problem(e.message.value)`, `x.transformInto[Problem]`); another 114 prefix a context to another failure's text (`s"$name: $why"`). They disappear when the types unify; they are not design decisions.
3. **The wire already has a small code vocabulary** (`not_found`, `invalid`, `conflict`, `precondition_failed`, `maintenance`, `settings_changed`, `settings_unavailable`, `invalid_citekey`, `invalid_period`, `invalid_status`) mapped to 400/404/409/412/503 by six hand-written `errorOut` helpers (`writeErrors`, `errors`, `opsErrors`, `agentErrors`, `ingestErrors`, `fetchErrors`). The group and case replace those codes.

Sites by layer (`group` column of `sites.tsv`):

| layer | sites | re-wrap or prefix only | narration or unsorted | map to a case |
|---|--:|--:|--:|--:|
| server | 1029 | 332 | 35 | 662 |
| core | 314 | 44 | 84 | 186 |
| api | 234 | 52 | 14 | 168 |
| plugin | 197 | 58 | 2 | 137 |
| cli | 167 | 41 | 0 | 126 |
| plugin-host | 153 | 33 | 1 | 119 |
| db | 97 | 21 | 1 | 75 |
| web | 69 | 25 | 2 | 42 |
| config | 62 | 3 | 0 | 59 |
| plugin-vaults | 48 | 17 | 0 | 31 |
| files | 35 | 3 | 0 | 32 |
| plugin-testkit | 34 | 10 | 0 | 24 |
| plugin-api | 29 | 3 | 0 | 26 |
| plugin-resolver | 22 | 6 | 0 | 16 |
| upstreams | 12 | 3 | 0 | 9 |
| values | 8 | 0 | 0 | 8 |

## 2. The taxonomy

**Rules for a case.** It holds only newtypes, enums and numbers from a closed vocabulary; no `String`, no message. Outside text is one of the five `External` newtypes (section 4). A case names the thing in the domain's words (`Entity.Kind.Source`, not "a thing").

**The constraint on the vocabulary.** The `problem` module sits below `values` and `api`, so it cannot see `Citekey`, `Slug` or `Year`. A case therefore names what failed with four generic types defined in the module:

| type | what it is |
|---|---|
| `Entity.Kind` | closed enum: Source, Article, Event, Place, Goal, Job, Run, Approval, Manager, Item, Asset, Merge, Plugin, File, Period, Version, ... (about 25) |
| `Entity.Key` | newtype over String: the key of any of them (citekey, slug, id, path); `api` adds `Citekey.asKey` style conversions |
| `Field` | newtype over String: the wire name of a request or settings field, or a path to one (`edges[3].dst`) |
| `Rule` | closed enum of the format and range rules the Iron constraints already name (Year, Latitude, Longitude, Citekey, Slug, Doi, Cron, Window, Size, Json, Url, ...); each constraint declares its `Rule` through a given, so `X.either(raw)` can return `Invalid.Malformed(field, raw, rule)` |

### Groups and cases

Counts are rows of `sites.tsv`; the three sites per case are real (`file:line`, current template).


#### Invalid (442 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Malformed` | `field: Field, raw: Raw, rule: Rule` | 190 | `ArticlePatches.scala:197` “--period: '{v}' is not start,end”<br>`CritiqueRef.scala:34` “'{s}' is not a pair — write it as <src>><dst>, the critic firs…”<br>`Secret.scala:100` “env:{rest.trim} is not an environment variable name” |
| `Unknown` | `field: Field, raw: Raw, known: Vocabulary` | 89 | `ListFilters.scala:316` “unknown {what} '{v}' (one of: {known.mkString}{,})})”<br>`Approvals.scala:102` “unknown decision '{decision}' (approve or reject)”<br>`ManagerSupervisor.scala:936` “unknown item kind '{itemKind}'” |
| `Missing` | `field: Field` | 111 | `Citekey.scala:23` “citekey is required”<br>`FilterInference.scala:81` “the rationale is empty — say why these sources are the article…”<br>`AgentsTools.scala:518` “`mode` needs `mode`: working, finish or pause” |
| `OutOfRange` | `field: Field, value: Raw, bounds: Bounds` | 33 | `EventGeometry.scala:14` “lat {lat} is not between -90 and 90”<br>`Note.scala:61` “a note is at most {MaxChars} characters (this one: {t.length})”<br>`ManagersControllers.scala:296` “rounds must be between 2 and {agents.roundsCap} (agents.review…” |
| `Inconsistent` | `fields: NonEmptyVector[Field], rule: Exclusion` | 19 | `AgentsTools.scala:305` “give both `goal` and `kind`, or neither to list the schedules”<br>`EventGeometry.scala:25` “lat and lon go together”<br>`Basemap.scala:129` “basemap.vector.drop: {mixed.mkString}{,})} both drops kinds an…” |

#### NotFound (164 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Entity` | `kind: Entity.Kind, key: Entity.Key` | 164 | `EventRepo.scala:327` “No event '{id}'”<br>`EnrichManagerKind.scala:59` “No source '{raw.value.trim}'”<br>`AssetLinkService.scala:49` “No asset '{in.citekey}/{in.assetName}'” |

#### Conflict (88 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `AlreadyExists` | `kind, key` | 31 | `PlaceRepo.scala:1069` “'{newId.value}' is already the id of another place”<br>`BackgroundJob.scala:63` “{what} is already running ({current.fold}{?})(_.value)}) — see…”<br>`Queue.scala:191` “acquisition item {id} is already done” |
| `Renamed` | `kind, asked: Key, now: Key` | 12 | `SourcesControllers.scala:199` “'{ck}' was renamed to '{now}' — write to '{now}' instead”<br>`PlaceRepo.scala:1076` “'{newId.value}' is an old id of '{aliasOf.get.value}' and stil…”<br>`GoalRepo.scala:161` “'{newSlug.value}' is an old slug of '{aliasOf.get.value}' and …” |
| `Stale` | `kind, key, sent: Stamp, stored: Stamp` | 12 | `Source.scala:709` “'{existing.citekey}' has moved on: you sent ifUpdatedAt='{want…”<br>`Approvals.scala:136` “approval {a.id} was decided meanwhile”<br>`PersonalControllers.scala:175` “the note changed since it was opened (now {was.iso}); open it …” |
| `WrongState` | `kind, key, is: State, wanted: Vector[State]` | 16 | `Engine.scala:634` “run {id} is {r.status}; it has already finished”<br>`ManagersControllers.scala:622` “item #{i.id} is {i.status}; only a settled item can be re-open…”<br>`JobControlRules.scala:41` “job {job.id} is not paused” |
| `Busy` | `activity: Activity` | 15 | `MaintenanceControllers.scala:75` “a backup is still running”<br>`MergeApplyJobs.scala:32` “a merge apply is still running — follow it with `GET /api/merg…”<br>`Calibre.scala:170` “the Calibre GUI is running — quit it first (calibredb needs ex…” |
| `Referenced` | `kind, key, by: NonEmptyVector[Ref]` | 2 | `PlaceOverrideRepo.scala:253` “'{label.value}' [{id.value}] is referred to by {refs.map((w, c…”<br>`SourceRepo.scala:519` “finding id '{id}' already exists — '{newCk.value}' would colli…” |

#### Refused (86 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Policy` | `rule: PolicyRule` | 47 | `RunTokenGate.scala:49` “a live run token is required”<br>`Opencode.scala:103` “opencode on a ChatGPT login is refused while the server listen…”<br>`Query.scala:191` “'{kw}' is not allowed here — `query` runs ONE read-only statem…” |
| `Quota` | `limit: Limit, used: Amount, cap: Amount, until: Option[Instant]` | 39 | `Engine.scala:547` “today's agent spend reached agents.budgets.daily-usd ({cfg.bud…”<br>`ClaudeCodeClient.scala:146` “claude -p stopped by the quota guard: {why}”<br>`CoreQuota.scala:140` “Refusal.OverBudget” |

#### Upstream (118 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Http` | `upstream: Upstream.Id, url: Url, status: HttpStatus, body: Excerpt` | 38 | `HttpDownload.scala:35` “HTTP {code} from {url}”<br>`JavaRangeHttp.scala:104` “{url}: HTTP {r.statusCode}”<br>`CoreApi.scala:56` “CoreFailure.Failed({url}: HTTP {a.status} {String(a.body.value…” |
| `Unreachable` | `upstream, url, reported: Reported` | 48 | `Client.scala:137` “cannot reach Histmersion at {serverUrl} ({e.getClass.getSimple…”<br>`HostCollection.scala:88` “source collection '{name.value}' failed: {e.getClass.getSimple…”<br>`SttpHttp.scala:32` “Failure.Transport({why.transformInto[Problem]})” |
| `Timeout` | `upstream, after: Duration` | 4 | `HostAiHttp.scala:91` “Failure.TimedOut({reported})”<br>`SendFailure.scala:18` “Failure.TimedOut({_})” |
| `BadReply` | `upstream, expected: ReplyShape, reported: Reported` | 28 | `Lpf.scala:317` “invalid JSON: {e.getMessage}”<br>`PeriodO.scala:62` “not JSON: {e.getMessage}”<br>`BasemapService.scala:1331` “the list of builds is not JSON: {e.getMessage}” |

#### Io (173 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `File` | `op: FileOp, path: Path, reported: Reported` | 67 | `BundleService.scala:112` “bundle export failed: {e.message}”<br>`ApiClient.scala:430` “could not read the server's answer: {e.getMessage}”<br>`ArticleCli.scala:168` “{failed.size} of {total} article(s) could not be exported” |
| `Wrong` | `path: Path, expected: PathKind` | 39 | `Store.scala:184` “{where} is not on disk”<br>`ConfigLayers.scala:145` “{file} is not a file”<br>`SeedControllers.scala:41` “not a directory on the server: {dir}” |
| `Corrupt` | `format: Format, at: Where, damage: Damage` | 32 | `BundleTar.scala:80` “{name}: header checksum mismatch”<br>`TerrariumPng.scala:147` “the PNG's image data holds {n} bytes, not {expected}”<br>`BundleFormat.scala:77` “rev {r} does not match its content ({revOf(obj)})” |
| `Process` | `tool: Tool, how: Exited(code, Stderr) / TimedOut(after) / NotStarted(Reported)` | 30 | `Codex.scala:303` “{argv.head} did not finish in 10 minutes”<br>`AgentDoctor.scala:426` “'claude auth status' did not answer JSON: {json.value.take(200…”<br>`Calibre.scala:593` “calibredb returned unparseable JSON: {e.getMessage}” |
| `Database` | `op: DbOp, reported: Reported` | 5 | `Db.scala:180` “{Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}”<br>`Migrate.scala:210` “{Option(e.getMessage).getOrElse(e.toString)}”<br>`QueryAccess.scala:89` “{Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}” |

#### Config (130 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Setting` | `key: SettingKey, why: SettingFault` | 105 | `Codex.scala:160` “agents.providers.codex.command is empty”<br>`Supervisor.scala:77` “runtime.data-dir is empty and HOME is unset (set HISTMERSION_D…”<br>`BuiltinGazetteers.scala:78` “{e.title} ({name}) is switched off (histmersion.plugins.{e.plu…” |
| `Unavailable` | `capability: Capability` | 19 | `ContentStore.scala:31` “this server stores no originals”<br>`Plugin.scala:139` “this host has no acquisition chain”<br>`AgentTrees.scala:87` “no runtimes directory: set agents.runtimes.dir (HISTMERSION_AG…” |
| `Reload` | `stage: ReloadStage, scope: Name` | 6 | `Hot.scala:463` “{name} (generation {generation}): its scope did not close with…”<br>`HotPlan.scala:42` “hot graph: dependency cycle {cycle(left, byName).mkString}{→})…”<br>`Hot.scala:276` “config reload: nothing changed — {e}” |

#### Plugin (233 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Load` | `plugin: PluginId, fault: LoadFault` | 97 | `PluginHost.scala:375` “secret {ref.shown}: no secret vault '{name}' is registered (en…”<br>`PluginHost.scala:515` “source fetcher '{id}' is null”<br>`Pom.scala:83` “not a POM: no <project>” |
| `Rejected` | `plugin: PluginId, item: Entity.Key, refusal: ItemRefusal` | 108 | `OpenContext.scala:102` “no licence on the record”<br>`Smithsonian.scala:138` “no image on the record”<br>`Cliopatria.scala:60` “feature #{ordinal} has no Name” |
| `Vault` | `vault: VaultId, failure: VaultFailure` | 28 | `Vaults.scala:67` “{vault}: '{ref}' has an empty value”<br>`YubiKey.scala:111` “keepassxc-cli did not answer within {waited.toSeconds} s”<br>`KeePassXcVault.scala:262` “{name}: could not {what} with a YubiKey — {parts}{tail}” |

#### Run (107 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Paused` | `reason: PauseReason, detail: Detail` | 39 | `RunCtx.scala:492` “{name}: {e.message} — fix the provider, then resume”<br>`Engine.scala:514` “paused on request”<br>`Stopped.scala:78` “Cause.Paused({reason}, {detail})” |
| `NoModel` | `role: AgentRole, tried: Vector[Tried]` | 17 | `ModelPicker.scala:343` “PickFailure.Unavailable(no model of the {role.wire} chain can …”<br>`AgentsControllers.scala:470` “no model of the {role.wire} chain can run: {tried .collect { c…”<br>`ModelPicker.scala:291` “the {p.title} is below its floor: {why}” |
| `Blocked` | `why: Block.Why` | 20 | `SchedulerPolicy.scala:586` “Why.PoolFull({p}, {res.poolCap.getOrElse(p, Resources.PoolCapV…”<br>`DrainService.scala:66` “Why.Draining({SchedulerPolicy.Block.Since(since.value)}, {Opti…”<br>`ManagerPolicy.scala:224` “Why.Budget({SchedulerPolicy.Block.SpendCap.Manager( SchedulerP…” |
| `Interrupted / Cancelled / Draining` | (no fields) / `by: Actor` / `since: Instant` | 22 | `Engine.scala:408` “the pipeline's version changed since this run started; start a…”<br>`JobControlRules.scala:37` “job {job.id} was paused before the server restarted: start it …”<br>`Engine.scala:444` “cancelled”<br>`BackgroundJob.scala:161` “stopped”<br>`Stopped.scala:82` “Cause.Draining” |
| `Aborted` | `step: StepName, cause: AbortCause` | 9 | `Engine.scala:520` “the run disappeared”<br>`RunCtx.scala:114` “step {seq} is '{recorded}' in the checkpoint but '{name}' now …”<br>`ModelPicker.scala:277` “{ModelPicker.CoolingWhy}; skipped until {Instant.ofEpochMilli(…” |

#### Content (144 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Finding` | `cause: Finding.Cause` | 55 | `ReviewArticle.scala:730` “a hand-written Sources cited section (the page generates it)”<br>`ReviewArticle.scala:1156` “{Finding.Problem.Cause.UncitedParagraph.getMessage}”<br>`ReviewArticlePipeline.scala:542` “a formatting error the last edits introduced: {e}” |
| `Proposal` | `rule: ProposalRule, at: Where` | 89 | `AgentVerify.scala:216` “{value} CE: there is no year 0 (or a negative CE year); use BC…”<br>`AutopilotPolicy.scala:182` “the text cites no source”<br>`CompileArticle.scala:153` “at most {maxClaims} claims per source (you gave {claims.size})” |

#### Internal (29 sites)

| case | typed fields | sites | three real sites |
|---|---|--:|---|
| `Unexpected` | `where: Where, exception: ExceptionName, reported: Reported` | 29 | `BackupJobs.scala:52` “backup crashed: {e.getMessage}”<br>`MergeApplyJobs.scala:49` “merge apply crashed: {e.getClass.getSimpleName}: {e.getMessage…”<br>`Coursier.scala:32` “{Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}” |

### Not in `Problem`

| bucket | sites | what happens to it |
|---|--:|---|
| re-wrap of another layer's `Problem` | 537 | deleted by unifying the types |
| context prefix on another failure (`{name}: {why}`) | 114 | becomes a path or context on the case: `Problems.at(Field)` / `within(Entity)` |
| narration (`Narration.Reason`) | 87 | per-feature closed types (`MediaMatch.Reason`, `GoalFocus.Note`, ...), outside `Problem` |
| not sorted by the rules | 52 | listed under `Unsorted` in `sites.tsv` for a person to place; most are web/cli wording or short adjectival fragments |

`RetryLater` (6 sites, `IngestBacklog.Failure.Retry`, `OaBacklog`) is backoff bookkeeping, not a failure: it stays a local type in the workflow that owns it.

### Two cases worth a decision

- **`Config.Setting` is large (105)** because "switched off", "no API key", "command is empty" and "HOME unset" all read as a setting that is wrong. If the user would rather see "switched off" as a refusal, `Refused.Disabled(feature, setting)` is a natural sixth `Refused` case; about 25 of the 105 would move.
- **`Plugin` is the largest group (233)** but 108 of them are `Rejected`: a plugin's item (an image, a place, a catalogue record) lacks a field or a licence. That is a per-item outcome in a batch, normally accumulated (`Problems`), not a request failure.

## 3. Fold-ins: the existing enums and per-feature ADTs

Three choices: **case** (it becomes a case or a nested enum of the shared ADT), **nested** (the shared ADT holds it as payload; the enum moves into the `problem` module), **local** (it stays where it is and converts to a case at its boundary).

| existing | decision | why |
|---|---|---|
| `Plugin.Http.Failure`, `Http.Failure` (server), `AiHttp.Failure` | **case**: `Upstream.Http`, `Unreachable`, `Timeout`, `BadReply` | They are three copies of the same idea, joined by two conversion seams (`SttpHttp`, `HttpPlugin`) that disappear. `refused` and `within(context)` become extensions. |
| `Processes.Failure` (TimedOut, NotStarted) | **case**: `Io.Process` with `Exited(code, Stderr)` added | A process that exits non-zero is a `Result` today, so callers read stderr as text; the third variant carries it typed. |
| `AiFailure` (Error, LimitReached) | **case**: `Upstream.*` and `Refused.Quota(until)` | The pool logic "pass the model over until X" matches on `Quota.until`, not on a type of its own. |
| `CoreFailure` (upstreams) | **case**: `Upstream.*`, `Refused.Quota` (`Skipped`) | Same reasoning. |
| `Halt.Stopped.Cause` / `api.automation.Stopped` | **case**: `Run.Paused`, `NoModel` (was `Exhausted`), `Interrupted`, `Draining`; `Failed(text)` becomes `Problem.Stored.Unrecognised` | It is the pattern commit `c8ccadd47`/`99792f448` set up; the codec survives (section 5). |
| `SchedulerPolicy.Block.Why`, `Block.Refusal` | **local** (convert to `Run.Blocked(kind)` where a request fails) | It is a scheduler explanation with 20+ fields the panel shows; most uses are not failures. Moving it would pull `Usd`, `Since`, `Stalls` into the tiny module. |
| `ReviewArticle.Finding.Problem.Cause` | **nested**: `Content.Finding(cause)` | Five small cases, already closed and round-tripped (`74e21c4d6`); the stored ledger needs them from api, db and web alike. |
| `KeePassXcMessage`, `YubiKey.Failure` | **local**; their output converts to `Plugin.Vault(vault, VaultFailure)` | `Next`/`Stop`/`NoEntry` steer retry inside the vault plugin; the host only needs the typed result. `KeePassXcMessage` stays the one place that classifies outside stderr. |
| `PluginMedia.Refusal` | **nested**: `Plugin.Rejected(..., ItemRefusal)` | A closed list of why an item is not imported; the enum moves to the module. |
| `MergeApplyJob.Failure` (Interrupted, Other) | **case**: `Run.Interrupted`; `Other` becomes `Internal.Unexpected` | A restart-cut-short job is already a case; `Other` is an exception text. |
| `SettingError` (config) | **nested**: `Config.Setting(key, SettingFault)` | Nine cases, closed, all about a key. |
| `Period.Error` (values) | **case**: `Invalid.Inconsistent` / `OutOfRange` | Its `EndsBeforeStart(start, end)` is an `OutOfRange` with bounds. |
| `Store.Error`, `MediaSources.Error`, `QueueError`, `PickFailure`, `CoverRefusal`, `Source.Refusal` (api) | **case**: `NotFound.Entity`, `Io.*`, `Run.NoModel`, `Refused.Policy`, `Invalid.*` | Several bake the message into the constructor (`case NotFound(...) extends Error(Problem(s"$where: no such file"))`); that is the pattern to remove. |
| `IngestBacklog.Failure` (Retry, ToAcquire, ...) | **local** | Workflow state: what to do with the item next. `ToAcquire(why)` converts to `NotFound.Entity(Original, ...)`. |
| `Api.Error` | **replaced** by `Problems` on the wire | Section 5. |
| `Cli.Error` | **shrinks** to `Cli.Failure(problems, origin)` | Exit code is a rendering concern derived from the origin; `notFound` becomes a query on the problems; `workingDirDenied` becomes `Refused.Policy(WorkingDirDenied)`. |
| `ApiFailure` (web) | **shrinks** to `status + Problems`; its three built-in texts become `Refused.Policy(ApiKeyRequired)`, `Upstream.Unreachable(Server)` and `Upstream.BadReply(Server)` | Today these are the only strings the web invents. |

The five `Problem` newtypes, the 12 `object Problems extends Newtype[String, Pure]` fields in `api` records, `Api.Error.Code` and `Api.Error.Field` are deleted.

## 4. Outside text

Five newtypes, all in the `problem` module, all unconstrained, none ever branched on (the existing `G5textBranch` rule applies to them):

| newtype | holds | captured where | shown how |
|---|---|---|---|
| `External.Reported` | an exception's `<ClassName>: <message>`, first line, at most 300 chars | the one `Problem.from(Throwable, where)` function; today 33 `Option(e.getMessage)...` copies | HTTP/CLI/MCP: after the case's own sentence, after an em dash. Log: in full plus stack. |
| `External.Stderr` | a process's standard-error tail, at most 500 chars | `Processes.run`, `Subprocess`, `PlanPools` probes, Calibre | after the sentence, indented; never in the JSON `text` of a group `Internal`. |
| `External.ReplyExcerpt` | the first 200 bytes of an upstream's reply body | `HttpPolicy`/`PolicyHttp`/`CoreApi` (today a `.take(200/300/500)` of such text appears 79 times) | same as `Stderr`. |
| `External.PluginSaid` | text a plugin supplied (a reason, a title) | the host, when it wraps a plugin's `Problem.Said` | with the plugin's id in front; never contains a secret (existing SPI rule). |
| `External.ModelSaid` | text from a model answer that failed validation | `AgentVerify`, `Engine` schema errors | quoted, cut to 200 chars. |

Of the 219 sites flagged `external=yes`, 171 are plain `Problem(...)`, 10 `Cli.Error`, 6 `Api.Error`, the rest enum cases. By group: Upstream 41, Io 41, Plugin 29, Internal 23, Invalid 13 (mostly "not JSON: {e.getMessage}"), Content 9, Config 7, Storage 5. The column is a heuristic: it flags splices that name `getMessage`, `getSimpleName`, `err`, `body`, `.take(n)` and the like. A further 900 rows have `relay=yes`: they pass on another failure's text, which can be outside text one hop away. Typing those failures at their source (the `Upstream`/`Io` cases above) removes the question.

Two more rules: an `External` value is captured **once**, at the call that met the exception, reply or process; nothing downstream rebuilds a `Problem` from `.value` of a failure (the 537 re-wraps). And `Internal.Unexpected` shows its `Reported` text only in the log, never in an HTTP body.

## 5. Rendering at the edges

Each renderer is a boundary adapter. I propose one new kind in `architecture/boundaries.tsv`, `render`, with these files registered (`.value` of an `External` is allowed only there):

| edge | file (new) | module |
|---|---|---|
| English sentence per case | `api/ProblemText.scala` | api (used by server, cli, web: they depend on api, not on core) |
| HTTP status and error body | `api/ProblemHttp.scala` (replaces `Api.writeErrors`, `errors`, `opsErrors`, `agentErrors`, `ingestErrors`, `fetchErrors`) | api |
| stored column wording and reader | `api/ProblemStored.scala` (moves the `Stopped` and `Finding.Problem.cause` codecs here) | api |
| CLI line and exit code | `cli/CliFailure.scala` (replaces `Cli.Error`) | cli |
| MCP tool error | `server/util/mcp/McpOut.scala` (existing, calls `ProblemText`) | server |
| log line | `server/ProblemLog.scala` | server |
| web error line | `web/ErrorLine.scala` (existing, takes `Problems`) | web |

### HTTP

Status by group. 409 (`Conflict`), 412, 503 and 404 exist today; 403, 422, 429, 502 and 504 are new. "No compatibility engineering" applies, so the snapshots (`openapi.json`, `mcp-tools.json`) are regenerated.

| group | status | note |
|---|---|---|
| Invalid | 400 | |
| NotFound | 404 | |
| Conflict | `Stale` 412 (today's `precondition_failed`); `Busy` while a restore or vacuum runs 503 (today's `maintenance`); the others 409 | |
| Refused | `Policy` 403, `Quota` 429 with `Retry-After` when `until` is known | |
| Upstream | `Http`/`BadReply`/`Unreachable` 502, `Timeout` 504 | |
| Io, Internal | 500 | `Internal` shows no `Reported` text |
| Config | 503 | today's `settings_unavailable` |
| Plugin | `Load` 500, `Rejected` 422 | |
| Run | 409 | the run state is the conflict |
| Content | 422 | |

JSON body, replacing `{"message": ..., "code": ...}`:

```json
{ "problems": [ { "group": "Invalid", "case": "OutOfRange",
                  "data": { "field": "lat", "value": "91", "bounds": { "min": -90, "max": 90 } },
                  "text": "lat 91 is not between -90 and 90" } ] }
```

`data` is the derived codec of the case (Kindlings, as everywhere else). `text` is the English sentence from the same renderer, kept so `curl` and old tooling stay readable; clients that render for themselves ignore it. One error type for every endpoint means one `errorOut(problemOut)` helper instead of six.

### CLI

`error: <text>` on stderr, one line per problem when there are several. Exit code from where the failure came from, not from its group:

| origin | exit | today |
|---|--:|---|
| local: bad flag, unreadable local file, `Invalid.*`, `Io.Wrong` found by the CLI itself | 1 | `Usage` |
| a `Problems` the server answered (any status) | 2 | `Refused` |
| `Upstream.Unreachable(Server)` or a reply that is not `Problems` | 3 | `Unreachable` |

`workingDirDenied` and `notFound` stop being flags: both are questions of the form `problems.exists(_.isNotFound)`.

### Log lines

`scribe` lines become `group.case key=value ...` followed by the English sentence, for example `Upstream.Http upstream=core status=429 url=... : CORE answered 429`. `External` values appear in full only here. The 61 files that call `scribe` today (38 of those lines splice `e.getMessage`) call `ProblemLog` for failures.

### Stored text

These columns hold failure text today (found by name in `V109__schema.sql`; each writer still has to be read to confirm):

| column | now | proposal |
|---|---|---|
| `jobs.error` | `Stopped` sentence, with a read-back codec (`99792f448`) | keep; `ProblemStored` writes the case's frozen sentence and reads back with the same round-trip test |
| `review_ledgers.ledger` (jsonb) | findings with `Finding.Problem` sentences (`74e21c4d6`) | keep; `Content.Finding.Cause` is the reader |
| `agent_managers.status_note` | manager status sentence; `ManagerRepo` filters pauses by the codec's prefix | keep; same codec, prefix owned by `ProblemStored` |
| `jobs.detail`, `jobs.result` (jsonb) | job output, some with error fields | keep JSON; failure fields become typed `Problems` JSON, old rows decode through `ProblemStored` |
| `agent_steps.error`, `candidate_watches.last_error`, `upstream_quota.blocked_why`, `plan_quota_refreshes.detail`, `goal_schedules.last_note`, `goal_focus.why`/`left_why`, `manager_items.outcome`, `approvals.note`/`result`, `acquire_checks.reason`, `queue_items.reason`, `agent_messages.tool_result` | free sentences | keep text; written by `ProblemStored`, read by it, unrecognised sentences read as `Problem.Stored.Unrecognised(text)` |

The rule that makes this safe is the one already in `Stopped.cause`: **a sentence is read as a case only if writing that case gives back exactly the same sentence.** So the frozen wording of each case is a contract (a table-driven test fixes it), and every row an older version wrote reads as it was. `Problem.Stored.Unrecognised(StoredText)` is a permanent case no new code constructs (an inventory rule forbids it); it is the only place old free text lives. User data needs no migration. Columns are not re-typed to jsonb.

### The web UI

The web depends on `api` only. It decodes `problems` from the JSON, shows `ProblemText(problem)` through `ErrorLine`, and may highlight the input named by `data.field` for `Invalid.*`; it validates nothing itself. Its three own failures (no network, 401/403, undecodable body) are `Upstream.Unreachable`, `Refused.Policy(ApiKeyRequired)`, `Upstream.BadReply`, built in `ApiFailure.of`.

## 6. `Problems = Problem | NonEmptyVector[Problem]`

### Operations

```scala
type Problems = Problem | NonEmptyVector[Problem]
extension (ps: Problems)
  def toVector: Vector[Problem]            // one list to render and to serialise
  def toNonEmpty: NonEmptyVector[Problem]
  def ++(more: Problems): Problems         // combine
  def at(field: Field): Problems           // prefix a path to every Invalid.*: replaces "item 7: ..."
  def exists(p: Problem => Boolean): Boolean   // isNotFound, workingDirDenied
extension [A](e: Either[Problems, A])
  def and[B](f: Either[Problems, B]): Either[Problems, (A, B)]    // runs both, accumulates both failures
extension [A, B](xs: Iterable[A])
  def traverseAll(f: A => Either[Problems, B]): Either[Problems, Vector[B]]   // all failures, not the first
```

plus a tuple form, `(a, b, c).allOrProblems` (up to 8), the one place that replaces hand-built `List[Problem]` joins. `Either` is covariant, so every function that returns `Either[Problem, A]` already conforms to `Either[Problems, A]` and migrates with no change.

### Own type or `cats.data.NonEmptyVector`

- `cats-core` 2.13 is on the class path of `api` (JVM and JS, through circe-core), hence of `web`, `server`, `cli` and `core`. `NonEmptyVector` would be available there.
- `plugin-api` does **not** have it: its stated dependencies are sconfig, kindlings-sconfig and iron, and the Kindlings sconfig POM names no cats. The plugin platform is deliberately minimal (AGENTS.md: "no Histmersion class on a plugin's class path"), and the `problem` module must be below it.

Recommendation: **a small own `NonEmptyVector[A]`** in the module (an opaque type over `Vector`, about 40 lines: `head`, `tail`, `++`, `map`, `toVector`, `of`), with a `CanEqual` and the circe codec in `api` (a JSON array). It keeps the module free of cats, keeps JS size down, and a `toCats` conversion in `api` costs one line if ever needed. If the user prefers cats, the module's dependency list gains `cats-core` (cross-built, available) and `plugin-api` gains it too.

### Pitfalls of the union

| pitfall | avoid by |
|---|---|
| `strictEquality`: `==` on `Problems` has no `CanEqual` | define `given CanEqual[Problems, Problems]` in `Problem`'s companion (implicit scope of a union includes its members); better, forbid `==` on `Problems` and compare `toVector` |
| matching `case Left(ps: NonEmptyVector[Problem])` warns about erasure under `-source:future` | never match on the union directly; give `fold(one)(many)` and `toVector`; a ContractsSuite rule flags `: NonEmptyVector[` patterns outside the module |
| `Left(p)` infers `Left[Problem, Nothing]`, which is fine, but `if ... Left(a) else Left(NonEmptyVector.of(b, c))` infers a LUB of `Product & Serializable` | write the expected type (`: Either[Problems, A]`) or use `Problems.of(...)` |
| enum `Problem` plus a value class `NonEmptyVector`: a boxed union at runtime | own `NonEmptyVector` as an opaque Vector (no boxing); runtime test `isInstanceOf[NonEmptyVector[?]]` must stay unambiguous, so `Problem` is never a `Vector` |
| JSON: a union needs a decoder that tells object from array | always send an array of problems on the wire (`problems`); decode a single `Problem` object as a one-element array for tolerance |

### First beneficiaries (hand accumulation today)

| pattern | count |
|---|--:|
| sites that join a list of messages with `"; "` into one `Problem` (`errs.mkString("; ")`: `Agents.scala:160`, `Managers.scala:362`, `ModelChains.scala:45/249/266`, `PlanQuota.scala:152`, ...) | 15 |
| `errs`/`errors`/`problems` `.mkString(...)` anywhere in main | 27 |
| signatures `Either[List/Vector/Seq[...], _]` (28 over `Problem` or `String`; 18 in `core`: `FilterInference`, `PlanArticles`, `EnrichSource`, `HintProposals`, `PlanGoal`, `CompileArticle`) | 34 |
| `.mkString("; ")` in main (not all failures) | 131 |
| `cats.data.Validated` in main | 13 (7 in `cli` through decline, 6 in `core`) |

The agent-proposal checks in `core` (`Content.Proposal`, 89 sites) are the natural first user: they already collect `List[Problem]` and a model gets all the reasons back in one round.

## 7. Module placement and plugins

### The module

```
newtype  (JVM + JS)  <-  problem  (JVM + JS)  <-  values, api, plugin-api, plugin-resolver, ...
```

- `lazy val problem = projectMatrix.in(file("modules/problem"))`, `dependsOn(newtype)`, `jvmPlatform` and `jsPlatform`, Iron only (the same shape as `newtype`).
- It holds: `Problem` and its cases, the vocabulary (`Entity`, `Field`, `Rule`, `Limit`, `PolicyRule`, `Capability`, ...), the five `External` newtypes, `NonEmptyVector`, `Problems` and its operations, and `Problem.from(Throwable, where)`. It holds **no** codec, no text, no schema: those are in `api` (circe/Tapir/Kindlings) and the renderers.
- Everything is written for Scala.js: no `java.nio.file.Path` (a `Path` newtype over String), no `java.time.Instant` (epoch-millisecond newtypes such as the existing `UntilEpochMs`), no JVM-only reflection.
- `values`, `api`, `config`, `files`, `core`, `db`, `server`, `cli`, `web` see it through their existing dependencies; `pluginApi` gains `dependsOn(problem.jvm(scala3))` so a plugin compiled against `plugin-api` sees `Problem` without any other dependency. That is an SPI change (the one to make together with the `Plugin.Http.Failure` and `AiHttp.Failure` removal).
- The enum has about 40 cases and cases of nested enums (`FileOp`, `Format`, `Damage`, `PolicyRule`, `LoadFault`, ...). Kindlings derivation for it lives in `api`; compile cost is the thing to measure first (a spike, not a guess).

### How a plugin's own failure fits

Three options considered: (a) plugins define their own error types, (b) plugins map to the shared cases, (c) one `Plugin` case with typed payload.

Recommendation: **(b) first, (c) as the escape hatch.**

- A plugin whose HTTP call fails already gets `Upstream.*` from `Plugin.Http`; a plugin that reads a file gets `Io.*`; one that rejects an item says `Plugin.Rejected(item, ItemRefusal)`. These are the cases the SPI returns today under other names.
- When no shared case fits, a plugin returns `Problem.said(kind: Said.Kind, text)` where `Said.Kind` is a closed enum (Unavailable, Rejected, Misconfigured, Failed) and the text is an `External.PluginSaid`. The host wraps it with the plugin id: `Plugin.Said(plugin, kind, text)` is a case in the `Plugin` group.
- A plugin does not get to add cases: a closed list is the point. A third-party plugin that wants more distinctions uses `Said.Kind` or asks for a case, which is an SPI version bump.

## 8. Migration order and mechanical checks

### Order

| step | what | sites touched (roughly) |
|---|---|--:|
| 0 | `problem` module, enum, `Problems`, `External`, renderers, `ProblemStored` with the frozen sentences, suites (case table, JSON, stored round-trip, status, exit code) | new code |
| 1 | replace the five `Problem` string newtypes and `Api.Error`/`Cli.Error` messages with the one type. To keep the tree building, a **temporary** case `Problem.Unclassified(text: Legacy.Text)` takes every site that is not yet classified (`Problem("...")` is rewritten mechanically to it). The 537 re-wraps are deleted. | all 2,510, plus 884 test lines |
| 2 | leaf layers classified: `values`, `config`, `files`, `plugin-api`, `plugin-resolver`, `upstreams`, `plugin-vaults` | ~220 |
| 3 | plugin host, testkit and the bundled plugins (SPI break, kit rebuild) | ~490 |
| 4 | `api` (renders and wire) and `core` | ~550 |
| 5 | `db` and `server`; `errorOut` unified; MCP and log renderers | ~1,130 |
| 6 | `cli` and `web` | ~240 |
| 7 | delete `Problem.Unclassified`, `Api.Error`, `Cli.Error`, the six `errorOut` helpers; regenerate `openapi.json`, `mcp-tools.json`, `toolbox.json`, `plugin-testkit-api.txt`, `contracts.tsv` | |

Each step 2 to 6 is one slice, deployed alone, as with the step-3 refactor. Step 1 is the only flag day; it is mechanical (Scalafix: `Problem(...)` to `Problem.Unclassified(...)`, drop the re-wraps) and compiles before anything else changes.

### Mechanical checks (each is a number that goes to 0 or a rule that holds)

| check | where | start | end |
|---|---|--:|--:|
| `G60fromString`: a `Problem` built from a `String` or an interpolation (`Unclassified` or the old apply) | `GuidelineInventory`, ratchet | 2,510 | 0 |
| `G5problem` (second top-level `Problem`): extend to "exactly one `Problem` definition in the repo" | existing rule + `ContractsSuite` `concept` | 5 | 1 |
| a case with a `String`, `Int` or `Boolean` field | new `ContractsSuite` rule over the enum | 1 (`Unclassified`) | 0 |
| `External.*` `.value` outside registered `render` files | `ContractsSuite` `unwrap` extended | n/a | 0 |
| a hand-joined list of problems (`mkString` into a `Problem`) | inventory | 15 | 0 |
| `Either[Api.Error, _]` / `Either[Cli.Error, _]` | inventory | 677 / 322 | 0 |
| `Api.Error.Code(`, `Cli.Error(`, `exitCode =` | grep in inventory | 178 / 112 / 112 | 0 |
| every case has a status, exit code, English sentence, log line, stored wording and a JSON round-trip | table-driven suite over `Problem.examples` (one value per case); the renderers' matches are exhaustive and the compiler refuses a missing case | | holds |
| `G5textBranch` also for the `External` newtypes | existing rule | 0 | 0 |
| `Problem.Stored.Unrecognised` constructed in main | inventory | n/a | 0 |
| `Narration.*` as `Problem` | inventory | 87 | 0 |

### Risks

1. **The flag day (step 1) touches about 3,400 lines** in 700+ files, including tests; sbt cached results can hide a failure (REPAIR-RULES: only `<project>/testOnly *` counts).
2. **Scala.js link size and compile time** of an enum with about 40 cases plus nested enums and their derived codecs. Spike before step 1.
3. **The SPI changes** (`Plugin.Http.Failure`, `AiHttp.Failure`, `Processes.Failure`, `AiFailure` go): every first-party plugin and the kit builder change; third-party plugins need a rebuild.
4. **Skills, prompts and MCP clients read error text.** Rendered sentences should stay identical where possible; a grep over `.claude`, `skills/` and `docs/` for quoted sentences belongs in step 0.
5. **Classification quality**: my rules placed about four sites in five. The person doing each slice re-sorts its sites; `sites.tsv` is the starting list, not the answer.
6. **Generic `Entity.Key`/`Field`** lose a little type safety against `Citekey`/`Slug` in the cases; the cost is that the module cannot depend on `api`.

### Stored-data obligations

- Every sentence an older version could have written reads back: frozen wording per case, tested against the sentences in `jobs.error`, `status_note` and the ledgers (take a restored `*_test` DB copy of the live database as the fixture, not an invented sample).
- A sentence that is not recognised reads as `Stored.Unrecognised`; writing it returns the same text.
- No column changes type; no Flyway migration is needed for the text columns.
- `jobs.detail`/`jobs.result` and API records with `error` fields change shape (typed `Problems`); old rows decode through the stored reader, new rows write the new shape.
- Reading the `status_note` pause prefix (`ManagerRepo`) must keep working during the transition; the prefix moves to `ProblemStored` unchanged.

## 9. Open questions

| # | question | recommendation |
|---|---|---|
| 1 | Group count: 11 groups (Storage folded into `Io.Database`, 5 sites) or 12 with `Storage` separate? | 11. A database failure is mostly an `Internal` bug or a lost connection; separate it later if the count grows. |
| 2 | Does the HTTP body carry `text` as well as `data`? | Yes. Costs a few bytes and keeps `curl` and MCP readable; one renderer produces it. |
| 3 | Own `NonEmptyVector` or `cats.data.NonEmptyVector`? | Own, because `plugin-api` has no cats and must not gain it. |
| 4 | Plugin failures: shared cases plus `Plugin.Said` (text from the plugin in an `External` newtype)? | Yes (option b+c in section 7). |
| 5 | Move the 87 narration sites out of `Problem` into per-feature types? | Yes; they are not failures. |
| 6 | Accept the temporary `Problem.Unclassified` for step 1, ratcheted to 0? | Yes; without it the first slice is the whole repo. |
| 7 | New HTTP statuses 403, 422, 429, 502, 504 (and a `Retry-After`)? | Yes (no compatibility constraint). |
| 8 | Should `Internal` hide exception text from HTTP/MCP responses? | Hide it, log it, return a short problem id. Today `backup crashed: {e.getMessage}` is shown to the caller. |
| 9 | `Refused.Disabled(feature, setting)` as a case, or leave "switched off" under `Config.Setting`? | Add it; "switched off" is not a broken setting. |
| 10 | Generic `Entity.Kind/Key` and `Field` in the module, or move `Citekey`, `Slug`, `Year` below it? | Generic. Moving them drags `values` and Iron constraints into a "tiny" module. |
| 11 | Wording of the logs: `group.case key=value` plus sentence? | Yes; the keys come from the case's own field names, so there is no second vocabulary. |
| 12 | Who re-sorts the 52 `Unsorted` and the roughly 20% of mis-sorted sites? | The slice owner, as part of steps 2 to 6; the inventory counts only what remains. |
