package com.histmersion
package api.log

import com.histmersion.testsupport.ComparedByValue.given
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import com.histmersion.api.log.LogEvent.{Subject, What}

import java.time.Instant

class LogEventSuite extends munit.FunSuite:
  private def event(what: What) =
    LogEvent(
      LogEvent.Id("01a1f981-4912-7849-a9dc-9b021af12285"),
      LogEvent.At("2026-11-20T09:15:02.123456Z"),
      LogEvent.Trace("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f"),
      Some(LogEvent.Label("session-7")),
      LogEvent.Actor.Client,
      Vector(Subject.Source(LogEvent.Key("smith2020")), Subject.Job(LogEvent.Key("j1"))),
      what
    )

  private val every: List[What] = List(
    What.RequestAnswered(
      LogEvent.Operation("/api/search"),
      LogEvent.Method("GET"),
      LogEvent.Status(200),
      LogEvent.Millis(12),
      Some(LogEvent.Client("127.0.0.1")),
      Some(LogEvent.Search("bronze age"))
    ),
    What.RequestsCounted(LogEvent.Operation("GET /api/health"), LogEvent.Count(40), LogEvent.Count(0)),
    What.ProviderCalled(
      LogEvent.Upstream("api.crossref.org"),
      LogEvent.Host("api.crossref.org"),
      None,
      LogEvent.Millis(5)
    ),
    What.Written(LogEvent.Table("jobs"), LogEvent.Change.Insert, Some(LogEvent.Count(2)), Vector(LogEvent.Key("j1"))),
    What.ProblemRaised(Some(LogEvent.ProblemId("ab12cd34")), LogEvent.ProblemCase("Internal.Unexpected")),
    What.Lost(LogEvent.Count(3), LogEvent.Loss.QueueFull),
    What.ToolCalled(LogEvent.Tool("search")),
    What.WritesCounted(LogEvent.Table("agent_leases"), LogEvent.Change.Update, LogEvent.Count(12))
  )

  test("every case reads back as it was written (the table's and the file's one JSON)"):
    every.foreach { w =>
      val e = event(w)
      assertEquals(readFromString[LogEvent](writeToString(e)), e)
    }

  test("every case's name is a kind the filter knows, and no other"):
    assertEquals(every.map(_.kind).sorted, LogEvent.Kinds.sorted)

  test("failures are kept as long as problems: a 5xx answer, an unreachable or 5xx provider, a problem, a loss"):
    assertEquals(every.map(_.failing), List(false, false, true, false, true, true, false, false))
    assert(
      What
        .RequestAnswered(
          LogEvent.Operation("/x"),
          LogEvent.Method("GET"),
          LogEvent.Status(503),
          LogEvent.Millis(1),
          None,
          None
        )
        .failing
    )

  test("a subject's ref is what the table's subjects column holds"):
    assertEquals(Subject.Problem(LogEvent.ProblemId("ab12")).ref, "problem:ab12")
    assertEquals(LogEvent.ProblemId("ab12").subject.ref, "problem:ab12")
    assertEquals(Subject.Source(LogEvent.Key("smith2020")).ref, "source:smith2020")
    assertEquals(LogEvent.Trace("t1").request.ref, "request:t1")

  test("the Markdown line names the step, its subjects and its trace"):
    val line = event(every.head).markdown
    assert(line.startsWith("- 2026-11-20 09:15:02.123 · "), line)
    assert(line.contains("GET /api/search → 200 in 12 ms from 127.0.0.1 searching “bronze age”"), line)
    assert(line.contains("source:smith2020, job:j1"), line)
    assert(line.contains("trace `0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f` [session-7]"), line)
    assertEquals(every.map(_.markdown).last, "12 update statement(s) on agent_leases, counted")
    val file = LogPage(Vector.empty, None, LogPage.Origin.File).markdown(_ => "")
    assert(file.contains("Read from the log file"), file)

  test("the filters' values: blank is none, a trace is read in lower case and must be a UUID, a kind in any case"):
    assertEquals(LogEvent.Key("  ").present, None)
    assertEquals(LogEvent.Key(" j1 ").present, Some(LogEvent.Key("j1")))
    assertEquals(LogEvent.Trace(" 0193A5F2-7C1E-7B3A-9F0E-2D4C6B8A1E3F ").present.map(_.wellFormed), Some(true))
    assert(!LogEvent.Trace("abc").wellFormed)
    assertEquals(LogEvent.Kind("written").known, Some(LogEvent.Kind("Written")))
    assertEquals(LogEvent.Kind("nope").known, None)
    assertEquals(LogEvent.Label.fromHeader("x" * 300).map(l => s"$l".length), Some(LogEvent.Label.MaxLength))
    assertEquals(LogEvent.At("2026-11-20T09:15:02.123456Z").day, "2026-11-20")

  test("a page's size: clamped to 1 … the most, cut, one more to see whether an older page follows"):
    assertEquals(LogPage.Limit.clamped(None), LogPage.Limit(100))
    assertEquals(LogPage.Limit.clamped(Some(LogPage.Limit(0))), LogPage.Limit(1))
    assertEquals(LogPage.Limit.clamped(Some(LogPage.Limit(99999))), LogPage.Limit.max)
    assertEquals(LogPage.Limit(2).cut(Vector(1, 2, 3)), (Vector(1, 2), Vector(3)))
    assert(LogPage.Limit(2).below(3) && !LogPage.Limit(2).below(2))
    val id = LogEvent.Id("01a1f981-4912-7849-a9dc-9b021af12285")
    assertEquals(
      LogPage.Cursor.of(LogEvent.At("2026-11-20T09:15:02.123456Z"), id).place,
      Some((Instant.parse("2026-11-20T09:15:02.123456Z"), id))
    )
    assertEquals(LogPage.Cursor("garbage").place, None)
    assertEquals(LogPage.Cursor("not-a-time~01a1f981-4912-7849-a9dc-9b021af12285").place, None)
    assertEquals(LogPage.Cursor("2026-11-20T09:15:02.123456Z~not-an-id").place, None, "only an id the server minted")
    assertEquals(LogEvent.Id.parse("01A1F981-4912-7849-A9DC-9B021AF12285"), None)
    assert(LogPage.From("FILE").names("File"))
    assertEquals(
      LogPage.Filter(problem = Some(LogEvent.ProblemId("ab12"))).problemAlone,
      Some(LogEvent.ProblemId("ab12"))
    )
    assertEquals(
      LogPage.Filter(problem = Some(LogEvent.ProblemId("ab12")), job = Some(LogEvent.Key("j1"))).problemAlone,
      None
    )
