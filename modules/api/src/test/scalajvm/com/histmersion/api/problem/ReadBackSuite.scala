package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.{External, Problem}
import util.problem.Problem.*
import util.problem.Text.{sentence, word}
import Answer.entry
import json.NewtypeCodecs.{problemCodec, vector}

/** What the store holds, read back on the JVM. A column keeps every problem as its derived entry, in an array: every
  * case's entry, generated from the cases' examples, reads back as the case. A text a master version stored (a bare
  * string, in a column the migration converted or inside a record) reads through [[StoredShapes]]: the shapes a master
  * writer wrote as their case, any other text kept whole.
  */
class ReadBackSuite extends munit.FunSuite:

  examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: its entry, as a column keeps it, reads back as the case"):
      val written = p.entry.asJson.noSpaces
      assertEquals(RawJson.decode[Problem](written), Right(p))
      assertEquals(RawJson.decode[Vector[Problem]](s"[$written,$written]"), Right(Vector(p, p)), "a column's list")
      assertEquals(
        RawJson.parse(written).map(_.asObject.flatMap(_("text")).flatMap(_.asString)),
        Right(Some(p.sentence)),
        "readable in SQL through text"
      )
  }

  /** The text the master writers wrote for a problem of a shape [[StoredShapes]] reads (master `Stopped.of`, lines
    * 77–82: `<reason>: <detail>`, `interrupted`, the drain's sentence; `CandidateSearch` line 125: `not asked this
    * sweep — <why>`), each detail as its writer wrote it: a literal of [[StoredShapes.Details]], or the text as it was.
    */
  private def masterText(p: Problem): Option[String] =
    def detail(why: Problem): Option[String] =
      StoredShapes.Details
        .collectFirst { case (text, `why`) => text }
        .orElse(why match
          case Stored.Unrecognised(t) => Some(s"$t")
          case _                      => None)
    p match
      case Run.Interrupted        => Some(StoredShapes.Interrupted)
      case Run.Draining(None)     => Some(StoredShapes.Drained)
      case Upstream.Unasked(why)  => detail(why).map(d => s"${StoredShapes.NotAsked}$d")
      case Run.Halted(cause, why) => detail(why).map(d => s"${cause.word}: $d")
      case Stored.Unrecognised(t) => Some(s"$t")
      case _                      => None

  /** The shapes a master version wrote, each with the master writer (file:line on `master`) and the case it reads as.
    */
  private val MasterShapes: Vector[(String, Problem)] = Vector(
    // JobRepo.scala:238 (failInterrupted, JobState.InterruptedSentence); Engine.scala:232 (reconcile, Stopped.of)
    "interrupted" -> Run.Interrupted,
    // AcquireRecheckService.scala:413 (Stopped.DrainingText)
    "stopped while the autopilot drains: it resumes once the drain is lifted" -> Run.Draining(None),
    // Engine.scala:431 then :452 (a restart's pause: `interrupted: interrupted`)
    "interrupted: interrupted" -> Run.Halted(Run.PauseCause.Interrupted, Run.Interrupted),
    // Engine.scala:514 then :452 (a running run paused on request)
    "requested: paused on request" ->
      Run.Halted(Run.PauseCause.Requested, Run.Paused(Run.PauseReason.Requested, None)),
    // Engine.scala:591 (a queued run paused)
    "requested: paused before it started" -> Run.Halted(Run.PauseCause.Requested, Run.Unstarted),
    // IngestQueue.scala:167-168 (an upload a restart lost)
    "interrupted: the upload is no longer held by this process" ->
      Run.Halted(Run.PauseCause.Interrupted, Run.UploadUnheld),
    // RunCtx.scala:392 then Engine.scala:452, the sentence of ModelPicker.scala:353 (a chain out of headroom)
    "plan-quota: enrich: all models for role enricher are below their plan floor or allowance; frees at 10:00 (x)" ->
      Run.Halted(
        Run.PauseCause.PlanQuota,
        Stored.Unrecognised(
          Stored.Text(
            "enrich: all models for role enricher are below their plan floor or allowance; frees at 10:00 (x)"
          )
        )
      ),
    "allowance: write: all models for role writer are below their allowance; never (y)" ->
      Run.Halted(
        Run.PauseCause.Allowance,
        Stored.Unrecognised(Stored.Text("write: all models for role writer are below their allowance; never (y)"))
      ),
    // RunCtx.scala:518 then Engine.scala:452
    "budget: enrich: the run's budget is exhausted" ->
      Run.Halted(Run.PauseCause.Budget, Stored.Unrecognised(Stored.Text("enrich: the run's budget is exhausted"))),
    // RunCtx.scala:560 then Engine.scala:452
    "provider: anthropic answered 529" ->
      Run.Halted(Run.PauseCause.Provider, Stored.Unrecognised(Stored.Text("anthropic answered 529"))),
    // Engine.scala:180 then :452 (the watchdog)
    "stalled: no sign of life for 31 min at step 4 (stall 1 of 3)" ->
      Run.Halted(
        Run.PauseCause.Stalled,
        Stored.Unrecognised(Stored.Text("no sign of life for 31 min at step 4 (stall 1 of 3)"))
      ),
    // CandidateSearch.scala:125 (a watch's last_error: an index left out of the sweep)
    "not asked this sweep — CORE asked to wait until 2025-11-10T00:00:00Z" ->
      Upstream.Unasked(Stored.Unrecognised(Stored.Text("CORE asked to wait until 2025-11-10T00:00:00Z")))
  )

  MasterShapes.foreach { (text, p) =>
    test(s"a master text reads as its case, which its writer wrote as that text; kept whole as an entry: '$text'"):
      assertEquals(StoredShapes.read(text), p)
      assertEquals(masterText(p), Some(text), "the master writer's text again")
      val bare = Json.fromString(text).noSpaces
      assertEquals(RawJson.decode[Problem](bare), Right(p), "written bare inside a record")
      assertEquals(RawJson.decode[Problem](p.entry.asJson.noSpaces), Right(p), "written back as its entry")
  }

  test("a master pause's words are its sentence, word for word"):
    MasterShapes
      .collect { case (text, p @ Run.Halted(_, _: Stored.Unrecognised)) => (text, p) }
      .foreach((text, p) => assertEquals(p.sentence, text))
    MasterShapes
      .collect { case (text, p @ Upstream.Unasked(_)) => (text, p) }
      .foreach((text, p) => assertEquals(p.sentence, text))
    assertEquals(Run.Draining(None).sentence, "stopped while the autopilot drains: it resumes once the drain is lifted")

  test("a master pause for want of a model names its role"):
    assertEquals(
      StoredShapes.exhaustedRole("enrich: all models for role enricher are below their allowance; never (x)"),
      Some(Run.Role("enricher"))
    )
    assertEquals(
      StoredShapes.exhaustedRole("all models for role writer are below their allowance"),
      Some(Run.Role("writer"))
    )
    assertEquals(StoredShapes.exhaustedRole("all models for role writer"), None)
    assertEquals(StoredShapes.exhaustedRole("the run's budget is exhausted"), None)

  test("any other text a master version stored is kept whole: as it is, and in its entry's data and text"):
    List(
      "cancelled",
      "interrupted ",
      "the manager was stopped",
      "no such file: /tmp/x",
      "paused for Nothing",
      "enrich: all models for role writer are cooling down",
      "the ChatGPT plan does not allow ordinary usage now (ordinaryUsageAllowed: false)",
      "converter down",
      "IllegalStateException: pg_dump vanished\n\tat x.Y(Y.scala:1)\n" + ("x" * 1500),
      ""
    ).foreach { text =>
      val read = StoredShapes.read(text)
      read match
        case Stored.Unrecognised(kept) => assertEquals(s"$kept", text)
        case other                     => fail(s"'$text' reads as $other")
      assertEquals(masterText(read), Some(text))
      val bare = Json.fromString(text).noSpaces
      assertEquals(RawJson.decode[Problem](bare), Right(read))
      val written = read.entry.asJson.noSpaces
      assertEquals(written, s"""{"group":"Stored","case":"Unrecognised","data":{"text":$bare},"text":$bare}""")
      assertEquals(RawJson.decode[Problem](written), Right(read))
    }

  test("what an exception reported is kept whole in its entry, every line, uncut"):
    val reported = External.Reported("java.io.IOException: reset\n" + ("y" * 1500) + "\nthird line")
    val p: Problem = Upstream.CallThrew(Upstream.Name("anthropic:claude-opus"), reported)
    assertEquals(RawJson.decode[Problem](p.entry.asJson.noSpaces), Right(p))

  test("an article plan stored with its dropped proposals as text reads back with each text whole, shown as it was"):
    import com.histmersion.api.research.Article.Plans
    val texts = List(
      "'zzagent_old': an article with this slug exists (or did)",
      "'zzagent_thin': search {query: \"Uluburun copper Anatolia nowhere\"} returns 0 sources; at least 2",
      "not proposed: article 'Bad Slug': 'Bad Slug' is not a slug"
    )
    val stored = s"""{"goal":"zzagent-goal","rationale":"r","articles":[],"dropped":${texts
        .map(t => Json.fromString(t).noSpaces)
        .mkString("[", ",", "]")}}"""
    val plan = RawJson.decode[Plans.ArticlePlan](stored)
    assertEquals(plan.map(_.dropped.map(_.sentence)), Right(texts))
    assertEquals(plan.map(_.dropped), Right(texts.map(StoredShapes.read)))
    assertEquals(
      plan.map(p => s"${Plans.planBlock(p)}"),
      Right("r\n\n" + texts.map(t => s"- dropped: $t").mkString("\n"))
    )
    // a plan written now keeps each dropped proposal's case, and reads back as it
    val typed = Plans.ArticlePlan(
      Plans.ArticlePlan.Goal("g"),
      Plans.ArticlePlan.Rationale("r"),
      Nil,
      List(Content.Proposal(Content.Requirement.SlugFree, Where("'old'")))
    )
    assertEquals(RawJson.decode[Plans.ArticlePlan](typed.asJson.noSpaces).map(_.dropped), Right(typed.dropped))
    assertEquals(
      typed.asJson.noSpaces,
      """{"goal":"g","rationale":"r","articles":[],"dropped":[{"group":"Content","case":"Proposal",""" +
        """"data":{"rule":{"SlugFree":{}},"at":"'old'"},"text":"'old': an article with this slug exists (or did)"}]}"""
    )

  test("an entry of a case this build does not know is a failure on the JVM: a column is read without a fallback"):
    // only the browser shows such an entry by its sentence (E19); the store and the CLI never guess it (G91)
    List(
      """{"group":"Run","case":"Nope","data":{},"text":"x"}""",
      """{"group":"Conflict","case":"Invented","data":{"x":1},"text":"the server's own sentence"}""",
      """{"group":"Galaxy","case":"Far","data":{},"text":"another sentence"}"""
    ).foreach(e => assert(RawJson.decode[Problem](e).isLeft, e))
