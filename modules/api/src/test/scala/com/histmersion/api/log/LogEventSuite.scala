package com.histmersion
package api.log

import com.histmersion.testsupport.ComparedByValue.given
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import com.histmersion.api.log.LogEvent.{Subject, What}

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
    What.Lost(LogEvent.Count(3), LogEvent.Loss.QueueFull)
  )

  test("every case reads back as it was written (the table's and the file's one JSON)"):
    every.foreach { w =>
      val e = event(w)
      assertEquals(readFromString[LogEvent](writeToString(e)), e)
    }

  test("every case's name is a kind the filter knows, and no other"):
    assertEquals(every.map(w => s"${w.kind}").sorted, LogEvent.Kinds.sorted)

  test("failures are kept as long as problems: a 5xx answer, an unreachable or 5xx provider, a problem, a loss"):
    assertEquals(every.map(_.failing), List(false, false, true, false, true, true))
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
    assertEquals(Subject.Problem(LogEvent.Key("ab12")).ref, "problem:ab12")
    assertEquals(Subject.Source(LogEvent.Key("smith2020")).ref, "source:smith2020")

  test("the Markdown line names the step, its subjects and its trace"):
    val line = LogMarkdown.line(event(every.head))
    assert(line.contains("GET /api/search → 200 in 12 ms from 127.0.0.1 searching “bronze age”"), line)
    assert(line.contains("source:smith2020, job:j1"), line)
    assert(line.contains("trace `0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f` [session-7]"), line)
    val file = LogMarkdown.page(LogPage(Vector.empty, None, LogPage.Origin.File), _ => "")
    assert(file.contains("Read from the log file"), file)
