package com.histmersion
package util.log

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.api.log.LogEvent.{Subject, What}
import com.histmersion.api.time.TimeBound

import java.time.Instant
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class LogQuerySuite extends munit.FunSuite:
  private val now = Instant.parse("2026-11-20T12:00:00Z")
  private val traceA = LogEvent.Trace("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f")
  private val traceB = LogEvent.Trace("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e40")

  private def event(id: LogEvent.Id, at: LogEvent.At, trace: LogEvent.Trace, subjects: Vector[Subject] = Vector.empty) =
    LogEvent(
      id,
      at,
      trace,
      None,
      LogEvent.Actor.Client,
      subjects,
      What.RequestAnswered(
        LogEvent.Operation("/api/sources/{citekey}"),
        LogEvent.Method("GET"),
        LogEvent.Status(200),
        LogEvent.Millis(3),
        None,
        None
      )
    )

  private def parse(f: LogPage.Filter) = LogQuery.parse(f, now)

  test("an empty filter is the newest page of everything, from the database"):
    val q = parse(LogPage.Filter()).fold(p => fail(p.sentence), identity)
    assertEquals(q.limit, LogPage.Limit(100))
    assertEquals(q.store, LogQuery.Store.Database)
    assertEquals(q.subjects, Vector.empty)

  test("the subjects, the kind (any case), the bounds and the store are read; the limit is clamped"):
    val q = parse(
      LogPage.Filter(
        job = Some(LogEvent.Key("j1")),
        source = Some(LogEvent.Key("smith2020")),
        kind = Some(LogEvent.Kind("written")),
        since = Some(TimeBound("1h")),
        limit = Some(LogPage.Limit(5000)),
        from = Some(LogPage.From("FILE"))
      )
    ).fold(p => fail(p.sentence), identity)
    assertEquals(q.subjects, Vector(Subject.Job(LogEvent.Key("j1")), Subject.Source(LogEvent.Key("smith2020"))))
    assertEquals(q.kind, Some(LogEvent.Kind("Written")))
    assertEquals(q.since, Some(Instant.parse("2026-11-20T11:00:00Z")))
    assertEquals(q.limit, LogPage.Limit.max)
    assertEquals(q.store, LogQuery.Store.File)

  test("an unknown kind, store, a trace that is no UUID and a broken cursor are refused, each naming what it read"):
    assert(parse(LogPage.Filter(kind = Some(LogEvent.Kind("Nope")))).left.exists(_.sentence.contains("Nope")))
    assert(parse(LogPage.Filter(from = Some(LogPage.From("tape")))).left.exists(_.sentence.contains("tape")))
    assert(parse(LogPage.Filter(trace = Some(LogEvent.Trace("abc")))).left.exists(_.sentence.contains("abc")))
    assert(parse(LogPage.Filter(before = Some(LogPage.Cursor("garbage")))).left.exists(_.sentence.contains("garbage")))

  test("a page: matching events newest first, cut at the limit, the cursor leads to the rest and nothing twice"):
    val (id1, id2, id3, id4, id5) = (
      LogEvent.Id("0193a5f2-0000-7000-8000-000000000001"),
      LogEvent.Id("0193a5f2-0000-7000-8000-000000000002"),
      LogEvent.Id("0193a5f2-0000-7000-8000-000000000003"),
      LogEvent.Id("0193a5f2-0000-7000-8000-000000000004"),
      LogEvent.Id("0193a5f2-0000-7000-8000-000000000005")
    )
    val events = Vector(
      event(id1, LogEvent.At("2026-11-20T10:00:00.000001Z"), traceA),
      event(id2, LogEvent.At("2026-11-20T10:00:00.000002Z"), traceB),
      event(id3, LogEvent.At("2026-11-20T10:00:00.000003Z"), traceA),
      event(id4, LogEvent.At("2026-11-20T10:00:00.000003Z"), traceA),
      event(id5, LogEvent.At("2026-11-20T10:00:00.000004Z"), traceA)
    )
    val first = parse(LogPage.Filter(trace = Some(traceA), limit = Some(LogPage.Limit(2))))
      .fold(p => fail(p.sentence), identity)
    val (rows, next) = first.page(events)
    assertEquals(rows.map(_.id), Vector(id5, id4))
    val second = parse(LogPage.Filter(trace = Some(traceA), limit = Some(LogPage.Limit(2)), before = next))
      .fold(p => fail(p.sentence), identity)
    val (rest, end) = second.page(events)
    assertEquals(rest.map(_.id), Vector(id3, id1))
    assertEquals(end, None)

  test("a subject filter needs every subject named; a trace's events are found by it alone"):
    val e = event(
      LogEvent.Id("01"),
      LogEvent.At("2026-11-20T10:00:00.000001Z"),
      traceA,
      Vector(Subject.Job(LogEvent.Key("j1")))
    )
    val ten = LogPage.Limit(10)
    assert(LogQuery.ofSubject(Subject.Job(LogEvent.Key("j1")), ten).matches(e))
    assert(!LogQuery.ofSubject(Subject.Job(LogEvent.Key("j2")), ten).matches(e))
    assert(LogQuery.ofTrace(traceA, ten).matches(e))
    assert(!LogQuery.ofTrace(traceB, ten).matches(e))

  test("ids are UUIDv7: the time in the first 48 bits, version 7, the RFC variant; they sort by time"):
    val a = LogEvent.Trace.v7(1700000000000L, -1L, -1L)
    val b = LogEvent.Trace.v7(1700000000001L, 0L, 0L)
    val text = s"$a"
    assert(a.wellFormed, text)
    assertEquals(text.charAt(14), '7')
    assert("89ab".contains(text.charAt(19)), text)
    assertEquals(java.lang.Long.parseLong(text.replace("-", "").take(12), 16), 1700000000000L)
    assert(a < b)
    assertEquals(s"${LogEvent.Id.v7(1700000000000L, -1L, -1L)}", text)

  test("times are written to the microsecond in UTC, and the day is the file's"):
    val at = LogEvent.At.of(Instant.parse("2026-11-20T09:15:02.123456789Z"))
    assertEquals(at, LogEvent.At("2026-11-20T09:15:02.123456Z"))
    assertEquals(at.day, "2026-11-20")
    assertEquals(LogEvent.At.of(Instant.parse("2026-11-20T09:15:02Z")), LogEvent.At("2026-11-20T09:15:02.000000Z"))
