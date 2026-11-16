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
import json.NewtypeCodecs.{list, problemCodec}

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
      "edges / dst: 'Gimbutas 1956' is no Citekey: it fails Match[\"[a-z][a-z0-9_-]*\"] (Should match [a-z][a-z0-9_-]*)"
    ),
    "Unknown" -> (
      400,
      "unknown Source.Status 'dubious' (one of: 'current', 'contested')"
    ),
    "Unreadable" -> (
      400,
      "from: '13oo BCE' is unreadable as Year (e.g. '1300 BCE', '530 CE', '-1300')"
    ),
    "Missing" -> (400, "rationale is required"),
    "Undeclared" -> (400, "staus is undeclared (one of: 'status', 'tags')"),
    "Wrapped" -> (400, "geometry: 'Feature' wraps a EventGeometry.Geojson in geometry"),
    "StoredOn" -> (
      400,
      "edges / add: Edge 'kristiansen2005' --refutes--> 'gimbutas1956' is stored on 'kristiansen2005'"
    ),
    "Ambiguous" -> (400, "edits / 0: Article.Edits.Anchor 'the Hittite texts' matches 2"),
    "Repeated" -> (400, "chain: Agents.ModelRef 'claude:opus' is repeated"),
    "Misplaced" -> (400, "status belongs under metadata"),
    "OutOfRange" -> (400, "Latitude '91' is out of [-90, 90]"),
    "Inconsistent" -> (400, "lat, lon break Together"),
    "Reference" -> (400, "plugins / api-key: no secret reference: EnvName '1PASSWORD'"),
    "Broken" -> (400, "cron: breaks CronField | 61 * * * * | Minute | NotNumber | 61 | 0 | 59"),
    "Entity" -> (404, "no Citekey 'anthony2007'"),
    "Copy" -> (404, "no open-access copy of 'doi:10.1017/S0003598X00000001'"),
    "Dangling" -> (
      404,
      "no Bundle.Kind.Source '0193f1c4-7b2a-7c3e-9d41-5e6f7a8b9c0d' in BundleOrHere, referred to by finding anthony2007#1"
    ),
    "Held" -> (409, "held SameWork anthony2006 | doi: source anthony2007"),
    "Job" -> (409, "job '0193f1c4' is queued: NotStarted"),
    "Entry" -> (500, "Bundle entry entities/sources.ndjson: Differs Lines 4/3"),
    "AlreadyExists" -> (409, "Place.Id 'pleiades_579885' already exists"),
    "Renamed" -> (409, "Citekey 'anthony2006' was renamed to 'anthony2007'"),
    "Stale" -> (
      412,
      "Citekey 'anthony2007' has moved on: sent 2026-11-01T10:00:00Z, stored 2026-11-02T08:30:00Z"
    ),
    "WrongState" -> (409, "Job.Id '412' is running; it needs paused or failed"),
    "Busy" -> (503, "Backup is still running"),
    "Referenced" -> (
      409,
      "Place.Id 'pleiades_579885' is referred to by 3 Finding"
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

    test(s"${p.group.productPrefix}.${p.productPrefix}: its frozen stored sentence"):
      assertEquals(p.stored, frozen)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its JSON, which decodes back to the case"):
      val e = p.entry
      assertEquals(s"${e.group}", p.group.productPrefix)
      assertEquals(s"${e.`case`}", p.productPrefix)
      assertEquals(s"${e.text}", p.sentence)
      assertEquals(RawJson.decode[Answer.Entry](e.asJson.noSpaces).flatMap(_.problem), Right(p))

    test(s"${p.group.productPrefix}.${p.productPrefix}: inside a record, typed with its sentence, and read back"):
      val written = (p: Problem).asJson
      p match
        case u: Stored.Unrecognised => assertEquals(written, Json.fromString(u.stored))
        case _                      => assertEquals(written, p.entry.asJson)
      assertEquals(RawJson.decode[Problem](written.noSpaces), Right(p))
  }

  test("a problem inside a record keeps the type of the problems it carries"):
    val copy: Problem = NotFound.Copy(
      Raw("10.1/x"),
      Vector(NotFound.Answer(NotFound.Fetcher("core"), NotFound.Kind.Refused, Problem.Run.Interrupted))
    )
    val written = copy.asJson.noSpaces
    assert(written.contains("\"why\":{\"group\":\"Run\",\"case\":\"Interrupted\""), written)
    assertEquals(RawJson.decode[Problem](written), Right(copy))

  test("a problem written bare by an earlier version that no case writes stays bare"):
    // any other text (an English sentence, an old message) is the stored text as it was, written back byte for byte
    List("converter down", "no legal open-access PDF found for 10.1/x", "").foreach { text =>
      val bare = Json.fromString(text).noSpaces
      val read = RawJson.decode[Problem](bare)
      read match
        case Right(unrecognised: Stored.Unrecognised) => assertEquals(unrecognised.stored, text)
        case other                                    => fail(s"$bare reads as $other")
      assertEquals(read.map(_.asJson.noSpaces), Right(bare))
    }
    assert(RawJson.decode[Problem]("""{"group":"Run","case":"Nope","data":{},"text":"x"}""").isLeft)

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

  test("a missing copy with the chain's answers lists them, in its sentence and in its stored sentence"):
    val copy = NotFound.Copy(
      Raw("10.1/x"),
      Vector(
        NotFound.Answer(
          NotFound.Fetcher("unpaywall"),
          NotFound.Kind.Miss,
          NotFound.Copy(Raw("doi:10.1/x"), Vector.empty)
        ),
        NotFound.Answer(
          NotFound.Fetcher("landing"),
          NotFound.Kind.Refused,
          Upstream.Challenged(
            Upstream.Name("landing"),
            Upstream.Url("https://x.example/f"),
            Upstream.Status(403),
            Vector.empty
          )
        )
      )
    )
    assertEquals(
      copy.sentence,
      "no legal open-access PDF found for 10.1/x — unpaywall: no copy — no legal open-access PDF found for doi:10.1/x; " +
        "landing: refused — landing answered HTTP 403 at https://x.example/f with a bot-protection challenge, not the page"
    )
    assertEquals(
      copy.stored,
      "no open-access copy of '10.1/x' — unpaywall: Miss — no open-access copy of 'doi:10.1/x'; " +
        "landing: Refused — landing challenged with HTTP 403 at https://x.example/f"
    )
    assert(
      Column.read(copy.stored) match
        case _: Stored.Unrecognised => true
        case _                      => false
      ,
      "the answers are not parsed back"
    )
    assertEquals(Column.read(copy.stored).stored, copy.stored)
    assertEquals(copy.status.code, 404)

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
