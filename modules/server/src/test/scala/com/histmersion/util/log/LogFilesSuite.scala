package com.histmersion
package util.log

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.api.log.LogEvent.What
import com.histmersion.config.LogConfig

import java.nio.file.{Files, Path}
import java.time.Instant

/** The rolling files: one per day and class, every event once, read back by the same query rules as the table, and
  * pruned by the same retention. Pure apart from a temporary directory.
  */
class LogFilesSuite extends munit.FunSuite:
  private val dir = FunFixture[Path](_ => Files.createTempDirectory("hm-log-files"), d => deleteTree(d))

  private def deleteTree(d: Path): Unit =
    val s = Files.walk(d)
    try s.sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p): Unit)
    finally s.close()

  private def event(id: String, at: String, what: What = ok) =
    LogEvent(LogEvent.Id(id), LogEvent.At(at), LogEvent.Trace("t"), None, LogEvent.Actor.Server, Vector.empty, what)

  private val ok = What.RequestsCounted(LogEvent.Operation("GET /api/health"), LogEvent.Count(1), LogEvent.Count(0))
  private val bad = What.ProblemRaised(None, LogEvent.ProblemCase("Internal.Unexpected"))

  private def all(files: LogFiles): Vector[LogEvent] =
    files.page(LogQuery.parse(LogPage.Filter(limit = Some(LogPage.Limit(1000))), Instant.EPOCH).toOption.get)._1

  dir.test("each event goes to its day's file of its class, one line each; nothing is lost or doubled") { d =>
    val files = LogFiles(() => Some(d))
    val events = Vector(
      event("01", "2026-11-19T23:59:59.000000Z"),
      event("02", "2026-11-20T00:00:00.000000Z"),
      event("03", "2026-11-20T00:00:01.000000Z", bad)
    )
    assert(files.append(events))
    assertEquals(
      files.files.map(_.getFileName.toString).sorted,
      List("events-2026-11-19.jsonl", "events-2026-11-20.jsonl", "problems-2026-11-20.jsonl")
    )
    assertEquals(all(files).map(_.id), Vector("03", "02", "01"))
  }

  dir.test("a line that does not read (one being written) is skipped, the rest are read") { d =>
    val files = LogFiles(() => Some(d))
    assert(files.append(Vector(event("01", "2026-11-20T00:00:00.000000Z"))))
    Files.writeString(d.resolve("events-2026-11-20.jsonl"), "{\"id\":", java.nio.file.StandardOpenOption.APPEND)
    assertEquals(all(files).map(_.id), Vector("01"))
  }

  dir.test("without a directory nothing is written and nothing read") { _ =>
    val files = LogFiles(() => None)
    assert(!files.append(Vector(event("01", "2026-11-20T00:00:00.000000Z"))))
    assertEquals(all(files), Vector.empty)
  }

  dir.test("retention: ordinary days older than keep-days go, problem days are kept keep-problem-days") { d =>
    val files = LogFiles(() => Some(d))
    assert(
      files.append(
        Vector(
          event("old", "2026-10-01T00:00:00.000000Z"),
          event("oldproblem", "2026-10-01T00:00:00.000000Z", bad),
          event("new", "2026-11-19T00:00:00.000000Z")
        )
      )
    )
    val gone = prune(files, 30, 90, Long.MaxValue)
    assertEquals(gone.map(_.getFileName.toString), List("events-2026-10-01.jsonl"))
    assertEquals(all(files).map(e => s"${e.id}").sorted, Vector("new", "oldproblem"))
  }

  dir.test("the size cap deletes the oldest days first and never today's") { d =>
    val files = LogFiles(() => Some(d))
    assert(
      files.append(
        Vector(
          event("a", "2026-11-18T00:00:00.000000Z"),
          event("b", "2026-11-19T00:00:00.000000Z"),
          event("c", "2026-11-20T00:00:00.000000Z")
        )
      )
    )
    val gone = prune(files, 30, 90, 0L)
    assertEquals(gone.map(_.getFileName.toString), List("events-2026-11-18.jsonl", "events-2026-11-19.jsonl"))
    assertEquals(all(files).map(_.id), Vector("c"))
  }

  private val Now = Instant.parse("2026-11-20T12:00:00Z")

  private def prune(files: LogFiles, keepDays: Int, keepProblemDays: Int, maxBytes: Long) =
    files.prune(
      Now,
      LogConfig.KeepDays(keepDays).before(Now),
      LogConfig.KeepDays(keepProblemDays).before(Now),
      LogConfig.MaxBytes(maxBytes)
    )

  test("the size cap of the table: the live rows over the cap, at the average row size"):
    def excess(bytes: Long, rows: Long, dead: Long, max: Long) =
      LogRetention.excessRows(bytes, rows, dead, LogConfig.MaxBytes(max))
    assertEquals(excess(bytes = 1000, rows = 10, dead = 0, max = 2000), 0)
    assertEquals(excess(bytes = 1000, rows = 10, dead = 0, max = 500), 5)
    // half the space is dead rows waiting to be reused: only the live half counts
    assertEquals(excess(bytes = 1000, rows = 5, dead = 5, max = 500), 0)
    assertEquals(excess(bytes = 0, rows = 0, dead = 0, max = 0), 0)

  test("the settings' rules: the cut-off, the backlog, the counted paths, when a retention run is due"):
    assertEquals(LogConfig.KeepDays(30).before(Now), Instant.parse("2026-10-21T12:00:00Z"))
    assertEquals(LogConfig.KeepDays(-1).before(Now), Now)
    assertEquals(LogConfig.Backlog(2).keep(Vector(1, 2, 3)), (Vector(2, 3), 1))
    assertEquals(LogConfig.Backlog(5).keep(Vector(1)), (Vector(1), 0))
    val counted = LogConfig.Counted(" /api/health , /web/ ,,").prefixes
    assertEquals(counted, List(LogConfig.CountedPrefix("/api/health"), LogConfig.CountedPrefix("/web/")))
    assert(counted.exists(_.covers("/web")) && counted.exists(_.covers("/web/app.js")))
    assert(!counted.exists(_.covers("/api/sources")))
    assert(LogConfig.PruneEveryMinutes(60).due(0L, 3600000L) && !LogConfig.PruneEveryMinutes(60).due(0L, 3599999L))
    assertEquals(LogConfig.BatchSize(500).after(1), 499)

  test(
    "a staged write statement becomes its Written event: the trigger's id, time, trace and label; jobs and sources as subjects"
  ):
    val e = LogWriter.written(
      LogRepo.Staged(
        LogEvent.Id("01a1f981-4912-7849-a9dc-9b021af12285"),
        LogEvent.At("2026-11-20T09:15:02.123456Z"),
        LogEvent.Trace("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f"),
        Some(LogEvent.Label("agent-x")),
        LogEvent.Table("jobs"),
        "update",
        Some(LogEvent.Count(1L)),
        List(LogEvent.Key("j1")),
        List(LogEvent.Key("smith2020"))
      )
    )
    assertEquals(e.id, "01a1f981-4912-7849-a9dc-9b021af12285")
    assertEquals(e.label, Some("agent-x"))
    assertEquals(
      e.subjects.map(_.ref),
      Vector("source:smith2020", "job:j1")
    )
    assertEquals(
      e.what,
      What.Written(LogEvent.Table("jobs"), LogEvent.Change.Update, Some(LogEvent.Count(1)), Vector(LogEvent.Key("j1")))
    )
