package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.{External, Problem, Problems}
import util.problem.Problem.*
import Column.stored
import Answer.{answer, decoded, entry, problem, retryAfter, status}
import Text.{sentence, sentences}

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
    "Missing" -> (400, "rationale is required"),
    "OutOfRange" -> (400, "com.histmersion.Latitude '91' is out of [-90, 90]"),
    "Inconsistent" -> (400, "lat, lon break Together"),
    "Entity" -> (404, "no com.histmersion.domain.library.Citekey 'anthony2007'"),
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
    "Load" -> (500, "plugin media-met did not load: NullComponent"),
    "Rejected" -> (422, "media-smithsonian did not take 'edanmdm:nmnhanthro_8371234': NoImage"),
    "Vault" -> (500, "secret vault keepassxc failed: TimedOut 20000 ms"),
    "Said" -> (503, "gazetteer-awmc says (Unavailable): the AWMC server is in maintenance"),
    "Paused" -> (409, "paused for Allowance until 2025-11-10T00:00:00Z"),
    "NoModel" -> (409, "no model of the reviewer chain can run (passed over: claude-opus-5-5, gpt-6)"),
    "Blocked" -> (409, "held back: PoolFull review"),
    "Interrupted" -> (409, "interrupted"),
    "Cancelled" -> (409, "cancelled by Person"),
    "Draining" -> (409, "draining since 2025-11-10T00:00:00Z"),
    "Aborted" -> (409, "step draft cannot go on: CheckpointMismatch"),
    "Finding" -> (422, "finding: UncitedParagraph"),
    "Proposal" -> (422, "claims: TooManyClaims 12/15"),
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
    val many: Problems = Problems.of(NotFound.Entity(TypeName("x.Y"), Raw("k")), Invalid.Missing(Field("f")))
    assertEquals(many.status.code, 404)
    assertEquals(
      examples.collect { case q: Refused.Quota => q.retryAfter },
      Vector(Some(EpochMs(1762732800000L)))
    )
    assertEquals(Invalid.Missing(Field("f")).retryAfter, None)

  test("the code words of every enum with fields read back from a column"):
    val reported = External.Reported("x: y")
    val variants: Vector[Problem] =
      Vector(Io.Damage.Checksum, Io.Damage.Truncated, Io.Damage.Undecodable)
        .map(Io.Corrupt(Io.Format.Tar, Where("entry 3"), _)) ++
        Vector(Config.Fault.Empty, Config.Fault.NoSecret, Config.Fault.Unset(SettingKey("HOME")))
          .map(Config.Setting(SettingKey("a.b"), _)) ++
        Vector(
          Config.Fault.Malformed(Raw("x y")),
          Config.Fault.Unknown(Raw("z")),
          Config.Fault.Conflicts(SettingKey("c.d"))
        )
          .map(Config.Setting(SettingKey("a.b"), _)) ++
        Vector(
          Plugin.Fault.Missing,
          Plugin.Fault.NullComponent,
          Plugin.Fault.BadDescriptor,
          Plugin.Fault.VersionMismatch,
          Plugin.Fault.Duplicate,
          Plugin.Fault.Threw(reported)
        ).map(Plugin.Load(Plugin.Id("p"), _)) ++
        Vector(
          Plugin.Retrieval.NoVault,
          Plugin.Retrieval.NoEntry,
          Plugin.Retrieval.EmptyValue,
          Plugin.Retrieval.Locked,
          Plugin.Retrieval.Failed(External.Stderr("locked"))
        ).map(Plugin.Vault(Plugin.Id("v"), _)) ++
        Vector(Run.Block.SpendCap, Run.Block.Draining, Run.Block.OutsideWindow, Run.Block.Waiting)
          .map(Run.Blocked(_)) ++
        Vector(
          Content.Requirement.NoYearZero,
          Content.Requirement.CitesNoSource,
          Content.Requirement.UnknownCitekey,
          Content.Requirement.OutsidePeriod
        ).map(Content.Proposal(_, Where("findings"))) ++
        Vector(Io.Ending.TimedOut(Millis(5L)), Io.Ending.NotStarted(reported)).map(Io.Process(Io.Tool("pg_dump"), _)) ++
        Io.Access.values.toVector.map(Io.File(_, Io.Path("/a b/c"), reported)) ++
        Io.Kind.values.toVector.map(Io.Wrong(Io.Path("/x"), _)) ++
        Conflict.Activity.values.toVector.map(Conflict.Busy(_)) ++
        Refused.Rule.values.toVector.map(Refused.Policy(_)) ++
        Run.PauseReason.values.toVector.map(Run.Paused(_, None)) ++
        Vector(
          Invalid.Unknown(TypeName("a.B"), Raw("x"), Vector.empty),
          Invalid.OutOfRange(TypeName("a.B"), Raw("x"), Invalid.Bounds(None, Some(Raw("9")))),
          Conflict.WrongState(TypeName("a.B"), Raw("k"), Conflict.State("done"), Vector.empty),
          Run.NoModel(Run.Role("writer"), Vector.empty),
          Refused.Quota(Refused.Limit.RateLimit, Refused.Amount(1.5), Refused.Amount(2.5), None)
        )
    variants.foreach(v => assertEquals(Column.read(v.stored), v, v.stored))

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
