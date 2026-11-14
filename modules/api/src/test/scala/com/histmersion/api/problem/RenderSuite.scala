package com.histmersion
package api
package problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.{External, Problem, Problems}
import util.problem.Problem.*
import Column.stored
import Answer.{answer, decoded, entry, problem, retryAfter, status}
import util.problem.Text.{sentence, sentences}

/** Every case of [[Problem]], through every renderer the api owns: its HTTP status, its English sentence, its frozen
  * stored sentence (which reads back as the case) and its JSON (which decodes back to the case). The renderers' matches
  * name every case, so a new case does not compile until each renderer has an answer for it; this table then fails
  * until the case has a row.
  */
class RenderSuite extends munit.FunSuite:

  /** The frozen sentences and the statuses: changing a sentence breaks every row an earlier version stored. */
  private val Frozen: Map[String, (Int, String)] = Map(
    "Malformed" -> (
      400,
      "edges / dst: 'Gimbutas 1956' is no com.histmersion.domain.library.Citekey: it fails Match[\"[a-z][a-z0-9_-]*\"] (Should match [a-z][a-z0-9_-]*)"
    ),
    "Unknown" -> (
      400,
      "unknown com.histmersion.domain.library.Source.Status 'dubious' (one of: 'current', 'contested')"
    ),
    "Unreadable" -> (
      400,
      "from: '13oo BCE' is unreadable as com.histmersion.Year (e.g. '1300 BCE', '530 CE', '-1300')"
    ),
    "Missing" -> (400, "rationale is required"),
    "Undeclared" -> (400, "staus is undeclared (one of: 'status', 'tags')"),
    "Wrapped" -> (400, "geometry: 'Feature' wraps a com.histmersion.api.catalogue.EventGeometry.Geojson in geometry"),
    "StoredOn" -> (
      400,
      "edges / add: com.histmersion.api.library.Edge 'kristiansen2005' --refutes--> 'gimbutas1956' is stored on 'kristiansen2005'"
    ),
    "Ambiguous" -> (400, "edits / 0: com.histmersion.api.research.Article.Edits.Anchor 'the Hittite texts' matches 2"),
    "Repeated" -> (400, "chain: com.histmersion.api.automation.Agents.ModelRef 'claude:opus' is repeated"),
    "Misplaced" -> (400, "status belongs under metadata"),
    "OutOfRange" -> (400, "com.histmersion.Latitude '91' is out of [-90, 90]"),
    "Inconsistent" -> (400, "lat, lon break Together"),
    "Reference" -> (400, "plugins / api-key: no secret reference: EnvName '1PASSWORD'"),
    "Entity" -> (404, "no com.histmersion.domain.library.Citekey 'anthony2007'"),
    "Copy" -> (404, "no open-access copy of 'doi:10.1017/S0003598X00000001'"),
    "Dangling" -> (
      404,
      "no com.histmersion.api.exchange.Bundle.Kind.Source '0193f1c4-7b2a-7c3e-9d41-5e6f7a8b9c0d' in BundleOrHere, referred to by finding anthony2007#1"
    ),
    "Held" -> (409, "held SameWork anthony2006 | doi: source anthony2007"),
    "Job" -> (409, "job '0193f1c4' is queued: NotStarted"),
    "Entry" -> (500, "Bundle entry entities/sources.ndjson: Differs Lines 4/3"),
    "AlreadyExists" -> (409, "com.histmersion.domain.catalogue.Place.Id 'pleiades_579885' already exists"),
    "Renamed" -> (409, "com.histmersion.domain.library.Citekey 'anthony2006' was renamed to 'anthony2007'"),
    "Stale" -> (
      412,
      "com.histmersion.domain.library.Citekey 'anthony2007' has moved on: sent 2026-11-01T10:00:00Z, stored 2026-11-02T08:30:00Z"
    ),
    "WrongState" -> (409, "com.histmersion.util.jobs.Job.Id '412' is running; it needs paused or failed"),
    "Busy" -> (503, "Backup is still running"),
    "Referenced" -> (
      409,
      "com.histmersion.domain.catalogue.Place.Id 'pleiades_579885' is referred to by 3 com.histmersion.domain.library.Finding"
    ),
    "Policy" -> (403, "refused: RunTokenRequired"),
    "Quota" -> (429, "DailySpend is spent: 5.25 of 4.5 until 2025-11-10T00:00:00Z"),
    "Disabled" -> (403, "events-gvp is switched off by histmersion.events.gvp.enabled"),
    "Http" -> (
      502,
      "core answered HTTP 429 at https://api.core.ac.uk/v3/search/works — {\"message\":\"rate limited\"}"
    ),
    "Challenged" -> (502, "archive-org challenged with HTTP 403 at https://archive.org/download/x/x.pdf"),
    "Limited" -> (429, "claude is limited until 2025-11-10T00:00:00Z"),
    "Unreachable" -> (
      502,
      "cannot reach core at https://api.core.ac.uk/v3/search/works — java.io.IOException: connection reset"
    ),
    "Timeout" -> (504, "core did not answer at https://api.core.ac.uk/v3/search/works within 30000 ms"),
    "BadReply" -> (
      502,
      "core's answer at https://api.core.ac.uk/v3/search/works is not Json — expected json value got '<html>' (line 1)"
    ),
    "File" -> (500, "could not Read /tmp/bundle.hmx — java.nio.file.NoSuchFileException: /tmp/bundle.hmx"),
    "Wrong" -> (500, "/srv/seed is not Directory"),
    "Corrupt" -> (500, "the Png data at terrarium tile 3/4/2 is damaged: Length 1024/1000"),
    "Process" -> (500, "calibredb exited with 2 — calibredb: library is locked"),
    "Database" -> (
      500,
      "the database refused to Migrate — org.postgresql.util.PSQLException: relation \"jobs\" does not exist"
    ),
    "Setting" -> (503, "histmersion.agents.providers.codex.command is wrong: Empty"),
    "Unavailable" -> (503, "this server has no Originals"),
    "Reload" -> (503, "reloading the settings failed at Close in plugins"),
    "Cycle" -> (503, "reload cycle: db → lease → db"),
    "Load" -> (500, "plugin media-met did not load: NullComponent"),
    "Rejected" -> (422, "media-met did not take '248284': NotOpen isPublicDomain is false"),
    "Vault" -> (500, "secret vault keepassxc failed: TimedOut 20000 ms"),
    "Dependency" -> (
      409,
      "doi-mirror-0.1.0.jar needs com.github.plokhotnyuk.jsoniter-scala:jsoniter-scala-core_3 2.42.0; it ships 2.41.5"
    ),
    "Said" -> (503, "gazetteer-awmc says (Unavailable): the AWMC server is in maintenance"),
    "Paused" -> (409, "paused for Allowance until 2025-11-10T00:00:00Z"),
    "NoModel" -> (409, "no model of the reviewer chain can run (passed over: claude-opus-5-5, gpt-6)"),
    "Blocked" -> (409, "held back: PoolFull review"),
    "Interrupted" -> (409, "interrupted"),
    "Cancelled" -> (409, "cancelled by Person"),
    "Draining" -> (409, "draining since 2025-11-10T00:00:00Z"),
    "Unpriced" -> (503, "model claude-opus-5-5 is unpriced (ApiKey claude-code)"),
    "CutShort" -> (409, "cut short: MergeApply"),
    "Unrecorded" -> (409, "unrecorded: MergeApply"),
    "Aborted" -> (409, "step draft cannot go on: CheckpointMismatch"),
    "Finding" -> (422, "finding: UncitedParagraph"),
    "Proposal" -> (422, "claims: TooManyClaims 12/15"),
    "Answer" -> (422, "the answer breaks SummaryGiven"),
    "Unexpected" -> (
      500,
      "backup failed unexpectedly (java.lang.IllegalStateException, problem 3f2a9c1e) — java.lang.IllegalStateException: pg_dump vanished"
    ),
    "Unclassified" -> (500, "unclassified: cannot open the bundle"),
    "Unrecognised" -> (500, "merge apply crashed: NullPointerException")
  )

  test("every case has a row, and every row a case"):
    assertEquals(examples.map(_.productPrefix).toSet, Frozen.keySet)
    assertEquals(examples.size, Frozen.size)

  examples.foreach { p =>
    val (code, frozen) = Frozen(p.productPrefix)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its status"):
      assertEquals(p.status.code, code)

    test(s"${p.group.productPrefix}.${p.productPrefix}: an English sentence"):
      assert(p.sentence.trim.nonEmpty)
      assertEquals(p.sentence, p.sentence.trim)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its frozen stored sentence, which reads back as the case"):
      assertEquals(p.stored, frozen)
      assertEquals(Column.read(frozen), p)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its JSON, which decodes back to the case"):
      val e = p.entry
      assertEquals(s"${e.group}", p.group.productPrefix)
      assertEquals(s"${e.`case`}", p.productPrefix)
      assertEquals(s"${e.text}", p.sentence)
      assertEquals(RawJson.decode[Answer.Entry](e.asJson.noSpaces).flatMap(_.problem), Right(p))
  }

  test("English sentences tell the cases apart"):
    assertEquals(examples.map(_.sentence).distinct.size, examples.size)

  test("a body lists every problem in order and decodes back to the same problems"):
    val one: Problems = examples.head
    val many: Problems = Problems.of(examples.head, examples.tail*)
    assertEquals(RawJson.decode[Answer](one.answer.asJson.noSpaces).flatMap(_.decoded), Right(one))
    assertEquals(RawJson.decode[Answer](many.answer.asJson.noSpaces).flatMap(_.decoded), Right(many))
    assertEquals(many.answer.problems.map(e => s"${e.`case`}"), examples.map(_.productPrefix))
    assertEquals(many.sentences, examples.map(_.sentence))
    assert(
      RawJson.decode[Answer]("""{"problems":[]}""").flatMap(_.decoded).isLeft,
      "a body with no problem is no answer"
    )
    assert(
      RawJson
        .decode[Answer]("""{"problems":[{"group":"Nope","case":"X","data":{},"text":""}]}""")
        .flatMap(_.decoded)
        .isLeft
    )

  test("the status of several problems is the first one's; a spent allowance says when to retry"):
    val many: Problems = Problems.of(NotFound.Entity(TypeName("x.Y"), Raw("k")), Invalid.Missing.here(Field("f")))
    assertEquals(many.status.code, 404)
    assertEquals(
      examples.collect { case q: Refused.Quota => q.retryAfter },
      Vector(Some(EpochMs(1762732800000L)))
    )
    assertEquals(Invalid.Missing.here(Field("f")).retryAfter, None)

  test(
    "the code words of every enum with fields read back from a column; no English sentence shows a type's code name"
  ):
    val reported = External.Reported("x: y")
    val variants: Vector[Problem] =
      Vector(Io.Damage.Checksum, Io.Damage.Truncated, Io.Damage.Undecodable)
        .map(Io.Corrupt(Io.Format.Tar, Where("entry 3"), _)) ++
        Vector(Config.Fault.Empty, Config.Fault.NoSecret, Config.Fault.Unset(SettingKey("HOME")))
          .map(Config.Setting(SettingKey("a.b"), _)) ++
        Vector(
          Config.Fault.Malformed(Raw("x y")),
          Config.Fault.Unknown(Raw("z")),
          Config.Fault.Conflicts(SettingKey("c.d")),
          Config.Fault.Block,
          Config.Fault.WrongKind(Config.ValueKind.Number, Config.ValueKind.Text),
          Config.Fault.Unreadable(reported),
          Config.Fault.Undeclared,
          Config.Fault.NotInDefaults,
          Config.Fault.OutsideRoot,
          Config.Fault.Credential(None),
          Config.Fault.Credential(Some(SettingKey("ANTHROPIC_API_KEY"))),
          Config.Fault.Rejected(Upstream.Name("europeana"), Upstream.Status(401)),
          Config.Fault.Literal,
          Config.Fault.Substituted,
          Config.Fault.Included(Io.Path("/etc/histmersion/extra.conf")),
          Config.Fault.NotReadBack,
          Config.Fault.KeyNeeded(External.PluginSaid("an api.data.gov key"))
        )
          .map(Config.Setting(SettingKey("a.b"), _)) ++
        Vector(
          Invalid.Form.Bare,
          Invalid.Form.EmptyValue,
          Invalid.Form.EnvName(Raw("1X")),
          Invalid.Form.NoEntry(Raw("keepassxc")),
          Invalid.Form.VaultName(Raw("Bad Vault"))
        ).map(Invalid.Reference.here(_)) ++
        Vector(
          Io.Damage.Unreadable(reported),
          Io.Damage.Newer(Raw("2"), Raw("1")),
          Io.Damage.Schema(Count(54L), Count(53L))
        ).map(Io.Corrupt(Io.Format.Json, Where("kit.json"), _)) ++
        Vector(
          Io.Fault.Missing,
          Io.Fault.Omitted,
          Io.Fault.Unlisted,
          Io.Fault.Repeated,
          Io.Fault.Foreign,
          Io.Fault.Ahead(Io.Path("manifest.json")),
          Io.Fault.Unreferenced,
          Io.Fault.Dangling(Count(1L)),
          Io.Fault.Dangling(Count(2L))
        ).map(Io.Entry(Io.Format.Bundle, Io.Path("blobs/ab12"), _)) ++
        Io.Measure.values.toVector.map(m =>
          Io.Entry(Io.Format.Bundle, Io.Path("blobs/"), Io.Fault.Differs(m, Raw("ab12"), Raw("cd34")))
        ) ++
        NotFound.Scope.values.toVector.map(
          NotFound.Dangling(TypeName("com.histmersion.api.exchange.Bundle.Kind.Place"), Raw("x_1"), Where("event e"), _)
        ) ++
        Vector(
          Conflict.Hold.Open,
          Conflict.Hold.SameWork(Raw("anthony2006"), Raw("title and year")),
          Conflict.Hold.Folds(Raw("S-1a2b"), Raw("map:anthony2006"), Raw("anthony2006")),
          Conflict.Hold.Merges,
          Conflict.Hold.Renames,
          Conflict.Hold.Renumbers,
          Conflict.Hold.Carries(Raw("keep-right")),
          Conflict.Hold.Stale,
          Conflict.Hold.Conditional(Raw("S-1a2b"), Raw("keep-left"), None),
          Conflict.Hold.Conditional(Raw("S-1a2b"), Raw("keep-left"), Some(Raw("open"))),
          Conflict.Hold.Collides,
          Conflict.Hold.PlanChanged(Raw("7c41e9a2")),
          Conflict.Hold.TextNotSent(Raw("file")),
          Conflict.Hold.CitekeyOfAnother(Raw("anthony2006")),
          Conflict.Hold.CitekeyTaken(Raw("guterbock1997"))
        ).map(Conflict.Held(Where("S-3c1e7a2b source anthony2007 — same work?"), _)) ++
        Vector(
          Conflict.Control.OnePiece(Raw("backup")),
          Conflict.Control.Unrecognised,
          Conflict.Control.Ended,
          Conflict.Control.AlreadyPaused,
          Conflict.Control.NotStarted,
          Conflict.Control.PausedBeforeRestart,
          Conflict.Control.NotPaused
        ).map(Conflict.Job(Raw("j: 1"), Conflict.State("running"), _)) ++
        Io.Format.values.toVector.map(Io.Corrupt(_, Where("x"), Io.Damage.Checksum)) ++
        Config.Capability.values.toVector.map(Config.Unavailable(_)) ++
        Vector(
          Plugin.Fault.Missing,
          Plugin.Fault.NullComponent,
          Plugin.Fault.BadDescriptor,
          Plugin.Fault.VersionMismatch,
          Plugin.Fault.Duplicate,
          Plugin.Fault.Threw(reported),
          Plugin.Fault.SpiMismatch(Raw("7"), Raw("1")),
          Plugin.Fault.ForeignSpi(Raw("2")),
          Plugin.Fault.MissingClass(Raw("org.example.Lib")),
          Plugin.Fault.SignerChanged(Plugin.Signer("ABCD 1234"), Plugin.Signer("EF01")),
          Plugin.Fault.NoProvider,
          Plugin.Fault.Taken(Raw("other.jar"))
        ).map(Plugin.Load(Plugin.Id("p"), _)) ++
        Vector(
          Plugin.Retrieval.NoVault,
          Plugin.Retrieval.NoEntry,
          Plugin.Retrieval.EmptyValue,
          Plugin.Retrieval.Locked,
          Plugin.Retrieval.Failed(External.Stderr("locked")),
          Plugin.Retrieval.Blank(Raw("db/entry")),
          Plugin.Retrieval.Absent(Raw("op://vault/item")),
          Plugin.Retrieval.Unread(Raw("db/entry"), Io.ExitCode(1), External.Stderr("Error: locked")),
          Plugin.Retrieval.NoKey(Vector(Raw("2"), Raw("1:123")), Vector.empty),
          Plugin.Retrieval.NoKey(Vector(Raw("2:9")), Vector(Raw("123"), Raw("456"))),
          Plugin.Retrieval.Key(Raw("2:9"), Plugin.Attempt.NotTouched, None),
          Plugin.Retrieval.Key(Raw("1"), Plugin.Attempt.WrongCredentials, Some(External.Stderr("Error: invalid")))
        ).map(Plugin.Vault(Plugin.Id("v"), _)) ++
        Plugin.Attempt.values.toVector.map(a =>
          Plugin.Vault(Plugin.Id("v"), Plugin.Retrieval.Key(Raw("2"), a, None))
        ) ++
        Vector(Run.Block.SpendCap, Run.Block.Draining, Run.Block.OutsideWindow, Run.Block.Waiting)
          .map(Run.Blocked(_)) ++
        Vector(
          Config.Unpriced(Run.Model("local:llama"), None),
          Config.Unpriced(Run.Model("openai/gpt-6"), Some(Config.Metered.Opencode)),
          Invalid.Undeclared.here(Field("x"), Vector.empty),
          Invalid.Wrapped.here(TypeName("a.B"), Raw("FeatureCollection"), Field("features"))
        ) ++
        Conflict.Activity.values.toVector.flatMap(a => Vector(Run.CutShort(a), Run.Unrecorded(a))) ++
        Vector(
          Content.Requirement.NoYearZero,
          Content.Requirement.CitesNoSource,
          Content.Requirement.UnknownCitekey,
          Content.Requirement.OutsidePeriod,
          Content.Requirement.StandIn,
          Content.Requirement.ExactTitle,
          Content.Requirement.ReplaceText,
          Content.Requirement.AnchorInArticle,
          Content.Requirement.QuoteGiven,
          Content.Requirement.QuoteInRecords(Vector(Raw("anthony2007"), Raw("haak2015"))),
          Content.Requirement.KnownCitekey(Raw("Anthony 2007")),
          Content.Requirement.EvidenceGiven,
          Content.Requirement.StatusNamed,
          Content.Requirement.NotUnassessed,
          Content.Requirement.EdgeTarget,
          Content.Requirement.NoSelfEdge,
          Content.Requirement.PositiveBce(Raw("-3300")),
          Content.Requirement.CeFromOne(Raw("0")),
          Content.Requirement.Calibrated(Raw("3270")),
          Content.Requirement.StartBeforeEnd(Raw("-1500"), Raw("-1600")),
          Content.Requirement.HistoricalReckoning,
          Content.Requirement.YearWithin(Raw("3000000")),
          Content.Requirement.WithinSourcePeriod(Raw("-3000"), Raw("-2900"), Raw("-1700"), Raw("-1200")),
          Content.Requirement.LinkTarget(Raw("ie-migration")),
          Content.Requirement.UnescapedLinks,
          Content.Requirement.SettingsGiven,
          Content.Requirement.KnownItems(Vector(Raw("goal one"), Raw("ie-migrations"))),
          Content.Requirement.ItemsNamed,
          Content.Requirement.HoldUntil,
          Content.Requirement.DateGiven(Raw("next week")),
          Content.Requirement.PriorityWithin(Raw("25"), Raw("20")),
          Content.Requirement.ExistingItems,
          Content.Requirement.FilterSelects,
          Content.Requirement.FilterRationale,
          Content.Requirement.PlanRationale,
          Content.Requirement.ArticleTitle,
          Content.Requirement.ArticleFilter,
          Content.Requirement.ArticlesGiven,
          Content.Requirement.SummaryGiven,
          Content.Requirement.StatusEvidence,
          Content.Requirement.NotesGiven,
          Content.Requirement.ClaimGiven,
          Content.Requirement.NoClaim,
          Content.Requirement.QuoteInRecord(Raw("anthony2007")),
          Content.Requirement.ReasonGiven,
          Content.Requirement.StatusUnstated(Raw("haak2015"), Raw("contested")),
          Content.Requirement.QuoteInWindow(Share(0.42)),
          Content.Requirement.CenturyAsYear(Raw("-11"), Count(11L)),
          Content.Requirement.PointerPhrase(Raw("this date")),
          Content.Requirement.PlacesResolve(
            Count(1L),
            Count(3L),
            Count(50L),
            Vector(Content.Unplaced(Raw("eastern shipping lanes"), Count(2L)), Content.Unplaced(Raw("x: y"), Count(3L)))
          ),
          Content.Requirement.TooManyArticles(Count(8L), Count(11L)),
          Content.Requirement.FilterTooNarrow(Raw("search {topics: [\"tin\"]}"), Count(1L), Count(5L)),
          Content.Requirement.FilterTooNarrow(Raw("search {query: \"it's: done 3/4\"}"), Count(3L), Count(5L)),
          Content.Requirement.FilterTooBroad(Raw("search {query: \"bronze\"}"), Count(900L), Count(400L)),
          Content.Requirement
            .FilterMisses(
              Raw("search {tags: [\"tin\"]}"),
              Count(2L),
              Count(5L),
              Count(40L),
              Count(60L),
              Vector(Raw("a1"), Raw("b2"))
            ),
          Content.Requirement.FilterMisses(
            Raw("search {tags: ['x' y]}"),
            Count(0L),
            Count(1L),
            Count(0L),
            Count(60L),
            Vector.empty
          )
        ).map(Content.Proposal(_, Where("findings"))) ++
        Vector(
          Content.Proposal(Content.Requirement.QuoteInRecords(Vector(Raw("a: b"))), Where("proposal for x: y")),
          Content.Proposal(Content.Requirement.StartBeforeEnd(Raw("1 in 2"), Raw("3")), Where("period"))
        ) ++
        Vector(Io.Ending.TimedOut(Millis(5L)), Io.Ending.NotStarted(reported)).map(Io.Process(Io.Tool("pg_dump"), _)) ++
        Io.Access.values.toVector.map(Io.File(_, Io.Path("/a b/c"), reported)) ++
        Vector(
          Io.Kind.File,
          Io.Kind.Directory,
          Io.Kind.Present,
          Io.Kind.Absent,
          Io.Kind.Executable,
          Io.Kind.Listed,
          Io.Kind.OnPath,
          Io.Kind.Inside(Io.Path("/srv/corpus"))
        ).map(Io.Wrong(Io.Path("/x"), _)) ++
        Conflict.Activity.values.toVector.map(Conflict.Busy(_)) ++
        Invalid.Exclusion.values.toVector
          .map(Invalid.Inconsistent.here(NonEmptyVector.of(Field("a"), Field("b")), _)) ++
        Vector(
          Plugin.Refusal.NoLicence,
          Plugin.Refusal.NoImage,
          Plugin.Refusal.NoName,
          Plugin.Refusal.NoLocation,
          Plugin.Refusal.NoDate,
          Plugin.Refusal.Duplicate,
          Plugin.Refusal.Restricted,
          Plugin.Refusal.LicenceNotAllowed(Raw("cc-by-nc")),
          Plugin.Refusal.NotOpen(Field("share_license_status"), Vector.empty),
          Plugin.Refusal.NoOpenImage(Field("usage.access"), Vector(Raw("Usage conditions apply"), Raw("CC BY")))
        ).map(Plugin.Rejected(Plugin.Id("p"), Raw("item 1"), _)) ++
        Upstream.Shape.values.toVector.map(
          Upstream.BadReply.here(Upstream.Name("x"), Upstream.Url("https://x.example"), _, External.Reported("eof"))
        ) ++
        Refused.Rule.values.toVector.map(Refused.Policy(_)) ++
        Run.PauseReason.values.toVector.map(Run.Paused(_, None)) ++
        Vector(
          Invalid.Unknown.here(TypeName("a.B"), Raw("x"), Vector.empty),
          Invalid.Unreadable.here(TypeName("a.B"), Raw("x"), Vector.empty),
          Invalid.OutOfRange.here(TypeName("a.B"), Raw("x"), Invalid.Bounds(None, Some(Raw("9")))),
          Conflict.WrongState(TypeName("a.B"), Raw("k"), Conflict.State("done"), Vector.empty),
          Run.NoModel(Run.Role("writer"), Vector.empty),
          Refused.Quota(Refused.Limit.RateLimit, Refused.Amount(1.5), Refused.Amount(2.5), None)
        ) ++ {
          val (met, url, at) =
            (Upstream.Name("met"), Upstream.Url("https://met.example/objects/7"), Vector(Field("object 7")))
          Vector(
            Upstream.Http(met, url, Upstream.Status(404), External.ReplyExcerpt("gone"), at),
            Upstream.Unreachable(met, url, External.Reported("reset"), at),
            Upstream.Challenged(met, url, Upstream.Status(403), at),
            Upstream.Limited(met, None),
            Upstream.Timeout(met, url, Millis(5L), at),
            Upstream.BadReply(met, url, Upstream.Shape.Json, External.Reported("eof"), at)
          )
        }
    variants.foreach(v => assertEquals(Column.read(v.stored), v, v.stored))
    (examples ++ variants).filterNot(_.group == Group.Stored).map(_.sentence).foreach { s =>
      assertEquals(testsupport.TypeNamesShown.in(s), None, s"a type's code name in: $s")
    }

  test("English names what an answer should have been as a noun, and every reply shape differently"):
    def said(shape: Upstream.Shape) =
      Upstream.BadReply
        .here(Upstream.Name("x"), Upstream.Url("https://x.example/f"), shape, External.Reported("eof"))
        .sentence
    assertEquals(said(Upstream.Shape.Range), "x's answer at https://x.example/f is not a byte range — eof")
    assertEquals(said(Upstream.Shape.Json), "x's answer at https://x.example/f is not JSON — eof")
    assertEquals(said(Upstream.Shape.Image), "x's answer at https://x.example/f is not an image — eof")
    assertEquals(Upstream.Shape.values.map(said).distinct.length, Upstream.Shape.values.length)
    assert(Upstream.Shape.values.forall(s => !said(s).contains(s" ${s.productPrefix} ")))

  test("a text no case writes reads as it was, and writes back the same"):
    List(
      "cancelled",
      "interrupted ",
      "no such file: /tmp/x",
      "paused for Nothing",
      "enrich: all models for role writer are cooling down",
      ""
    ).foreach { text =>
      assertEquals(Column.read(text), Stored.Unrecognised(Stored.Text(s"$text")))
      assertEquals(Column.read(text).stored, text)
    }
