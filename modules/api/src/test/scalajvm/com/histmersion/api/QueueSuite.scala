package com.histmersion
package api

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Queue.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{LintRule, tolerated}

/** The bookkeeping parser/renderer (plan §5.2 stream 2.8), tested against the REAL `meta/{ACQUIRE,TASKS,LOG}.md` from
  * the research workspace, copied verbatim into `src/test/resources/meta/`. Pure: no database, no server.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class QueueSuite extends munit.FunSuite:

  private def fixture(name: String): String =
    val is = getClass.getClassLoader.getResourceAsStream(s"meta/$name")
    assert(is != null, s"missing fixture meta/$name")
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString
    finally is.close()

  /** Parsed rows as the database would hand them back: ids in insertion order. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def store(kind: Kind, text: FileText): List[QueueItem] =
    Queue.parse(kind, text).zipWithIndex.map { (r, i) =>
      QueueItem(
        QueueItem.Id(i + 1),
        kind.label,
        r.text,
        r.topic,
        r.status.getOrElse(QueueItem.Status(Open)),
        r.reason,
        r.createdAt,
        None
      )
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def roundTrip(kind: Kind, text: FileText): String = Queue.render(kind, store(kind, text)).value

  /** A readable line diff, so a failure says WHICH line moved rather than dumping 4 kB twice. */
  private def diff(a: String, b: String): String =
    val (as, bs) = (a.linesIterator.toVector, b.linesIterator.toVector)
    (0 until math.max(as.size, bs.size))
      .flatMap { i =>
        (as.lift(i), bs.lift(i)) match
          case (x, y) if x == y => None
          case (x, y)           =>
            Some(s"line ${i + 1}:\n  expected: ${x.getOrElse("<eof>")}\n  actual:   ${y.getOrElse("<eof>")}")
      }
      .take(10)
      .mkString("\n")

  // ── the three real files ─────────────────────────────────────────────────────────────────────

  test("ACQUIRE.md round-trips byte for byte"):
    val original = fixture("ACQUIRE.md")
    val out = roundTrip(Kind.Acquire, FileText(original))
    assertEquals(out, original, diff(original, out))

  test("LOG.md round-trips byte for byte"):
    val original = fixture("LOG.md")
    val out = roundTrip(Kind.Log, FileText(original))
    assertEquals(out, original, diff(original, out))

  test("TASKS.md round-trips (one blank line the original omits before a bold group)"):
    val original = fixture("TASKS.md")
    val out = roundTrip(Kind.Tasks, FileText(original))
    // the renderer is canonical, not byte-preserving: it always separates a heading from a preceding
    // block, where the hand-written file does so everywhere EXCEPT under `## 📋 Backlog`.
    assertEquals(
      out.linesIterator.filter(_.trim.nonEmpty).toList,
      original.linesIterator.filter(_.trim.nonEmpty).toList,
      diff(original, out)
    )
    assertEquals(out.count(_ == '\n') - original.count(_ == '\n'), 1)

  test("export is a fixpoint: re-importing an export reproduces it exactly"):
    Kind.values.foreach { kind =>
      val once = roundTrip(kind, FileText(fixture(kind.file.value)))
      val twice = roundTrip(kind, FileText(once))
      assertEquals(twice, once, s"${kind.file}: " + diff(once, twice))
    }

  // ── what parsing actually captured ───────────────────────────────────────────────────────────

  test("ACQUIRE: outstanding items are open, completed ones are done, prose is a note"):
    val rows = Queue.parse(Kind.Acquire, FileText(fixture("ACQUIRE.md")))
    val open = rows.filter(_.status.contains(Open))
    val done = rows.filter(_.status.contains(Done))
    assert(open.exists(_.text.value.contains("Cynthia Eller")), "the Eller line is an open item")
    assert(done.exists(_.text.value.contains("24 foundational books")), "the completed section is done")
    assert(open.forall(_.topic.exists(_.value.startsWith("🟡 Outstanding"))))
    assert(done.forall(_.topic.exists(_.value.startsWith("✅ Completed"))))
    // the file's own preamble survives as a note with no topic
    assert(rows.exists(r => r.status.contains(Note) && r.topic.isEmpty && r.text.value.startsWith("# 📥 ACQUIRE")))

  test("ACQUIRE: a whole-line bold group becomes the third level of the topic path"):
    val rows = Queue.parse(Kind.Acquire, FileText(fixture("ACQUIRE.md")))
    val eller = rows.find(_.text.value.contains("Cynthia Eller")).get
    val parts = Queue.path(eller.topic)
    assertEquals(parts.size, 3)
    assertEquals(parts(0), "🟡 Outstanding")
    assert(parts(1).startsWith("Historiography of grand theories"), parts(1))
    assertEquals(parts(2), "**Highest value — get first**")

  test("TASKS: a bold line that is NOT the whole line stays prose"):
    val rows = Queue.parse(Kind.Tasks, FileText(fixture("TASKS.md")))
    val drained = rows.find(_.text.value.startsWith("**Original ingestion queues")).get
    assertEquals(drained.status, Some(Note))
    // …while the trailing `:**` group heading IS a heading
    assert(rows.exists(_.topic.exists(_.value.endsWith("**Enrichment / compile follow-ups:**"))))

  test("TASKS: the currency-checkpoint table is kept verbatim as one note"):
    val rows = Queue.parse(Kind.Tasks, FileText(fixture("TASKS.md")))
    val table = rows.find(_.text.value.contains("| subtopic | checkpoint held | since |")).get
    assertEquals(table.status, Some(Note))
    assert(table.text.value.contains("| IE homeland (ling.+arch.) |"), "the whole table is one block")
    assert(table.text.value.contains("**Genre note:**"), "trailing prose stays in the same block")

  test("LOG: entries keep their heading as the topic and their date as createdAt"):
    val rows = Queue.parse(Kind.Log, FileText(fixture("LOG.md")))
    val bullets = rows.filterNot(_.status.contains(Note))
    assert(bullets.nonEmpty)
    assert(bullets.forall(_.createdAt.isDefined), "every bullet inherits its entry's date")
    // a date RANGE heading keeps its first day
    assert(rows.exists(_.createdAt.contains("2026-06-24T00:00:00Z")), rows.flatMap(_.createdAt).distinct.mkString(","))
    assert(rows.exists(_.topic.contains("2026-06-24/25 — Frontier passes + reviews-first strategy")))

  test("LOG: a bullet's indented continuation lines belong to it"):
    val rows = Queue.parse(Kind.Log, FileText(fixture("LOG.md")))
    val merged = rows.find(_.text.value.startsWith("Merged the scattered working files")).get
    assert(merged.text.value.contains("\n  acquisition queue)"), merged.text)

  // ── the renderer on its own ──────────────────────────────────────────────────────────────────

  test("an empty list renders a usable file, and that file re-imports to nothing but its preamble"):
    Kind.values.foreach { kind =>
      val out = Queue.render(kind, Nil).value
      assert(out.startsWith("# "), out.take(40))
      val rows = Queue.parse(kind, FileText(out))
      assert(rows.forall(_.status.contains(Note)), rows.toString)
    }

  test("checkbox lists render [ ] / [x]; the log renders plain bullets"):
    def item(status: QueueItem.Status) = QueueItem(
      QueueItem.Id(1),
      QueueItem.List("x"),
      QueueItem.Text("a thing"),
      Some(QueueItem.Topic("Topic")),
      status,
      None,
      None
    )
    assert(Queue.render(Kind.Acquire, List(item(QueueItem.Status(Open)))).value.contains("- [ ] a thing"))
    assert(Queue.render(Kind.Acquire, List(item(QueueItem.Status(Done)))).value.contains("- [x] a thing"))
    assert(Queue.render(Kind.Log, List(item(QueueItem.Status(Open)))).value.contains("- a thing"))
    assert(!Queue.render(Kind.Log, List(item(QueueItem.Status(Open)))).value.contains("[ ]"))

  test("a topic path renders ## / ### / bold, one level per segment; a heading hugs its content"):
    val out = Queue
      .render(
        Kind.Tasks,
        List(
          QueueItem(
            QueueItem.Id(1),
            QueueItem.List("tasks"),
            QueueItem.Text("x"),
            Some(QueueItem.Topic(s"A${Sep}B$Sep**C**")),
            QueueItem.Status(Open),
            None,
            None
          )
        )
      )
      .value
    // heading → heading is separated; heading → item hugs
    assert(out.endsWith("\n## A\n\n### B\n\n**C**\n- [ ] x\n"), out)

  test("later items join an existing topic group instead of repeating its heading"):
    val out = Queue
      .render(
        Kind.Tasks,
        List(
          QueueItem(
            QueueItem.Id(1),
            QueueItem.List("tasks"),
            QueueItem.Text("first"),
            Some(QueueItem.Topic("A")),
            QueueItem.Status(Open),
            None,
            None
          ),
          QueueItem(
            QueueItem.Id(2),
            QueueItem.List("tasks"),
            QueueItem.Text("other"),
            Some(QueueItem.Topic("B")),
            QueueItem.Status(Open),
            None,
            None
          ),
          QueueItem(
            QueueItem.Id(3),
            QueueItem.List("tasks"),
            QueueItem.Text("second"),
            Some(QueueItem.Topic("A")),
            QueueItem.Status(Open),
            None,
            None
          )
        )
      )
      .value
    assertEquals(out.linesIterator.count(_ == "## A"), 1)
    assert(out.indexOf("- [ ] second") > out.indexOf("- [ ] first"))

  test("log entries are ordered newest first, so an added line lands on top"):
    val out = Queue
      .render(
        Kind.Log,
        List(
          QueueItem(
            QueueItem.Id(1),
            QueueItem.List("log"),
            QueueItem.Text("old"),
            Some(QueueItem.Topic("2026-01-01 — old")),
            QueueItem.Status(Open),
            None,
            Some(QueueItem.CreatedAt("2026-01-01T00:00:00Z")),
            None
          ),
          QueueItem(
            QueueItem.Id(2),
            QueueItem.List("log"),
            QueueItem.Text("new"),
            Some(QueueItem.Topic("2026-08-25 — new")),
            QueueItem.Status(Open),
            None,
            Some(QueueItem.CreatedAt("2026-08-25T00:00:00Z")),
            None
          )
        )
      )
      .value
    assert(out.indexOf("## 2026-08-25 — new") < out.indexOf("## 2026-01-01 — old"), out)

  // ── the terminal listing ─────────────────────────────────────────────────────────────────────

  test("the `queue <list>` listing shows ids and hides note blocks"):
    val items = List(
      QueueItem(
        QueueItem.Id(7),
        QueueItem.List("acquire"),
        QueueItem.Text("buy a book"),
        Some(QueueItem.Topic("Books")),
        QueueItem.Status(Open),
        None,
        None
      ),
      QueueItem(
        QueueItem.Id(8),
        QueueItem.List("acquire"),
        QueueItem.Text("# preamble"),
        None,
        QueueItem.Status(Note),
        None,
        None
      )
    )
    val md = Queue.markdown(Kind.Acquire, items).value
    assert(md.contains("`#7`"), md)
    assert(md.contains("1 item(s)"), md)
    assert(!md.contains("preamble"), md)

  test("the listing strips a bold group's own ** so the heading is not doubled"):
    val md = Queue
      .markdown(
        Kind.Acquire,
        List(
          QueueItem(
            QueueItem.Id(1),
            QueueItem.List("acquire"),
            QueueItem.Text("x"),
            Some(QueueItem.Topic(s"🟡 Outstanding$Sep**Highest value**")),
            QueueItem.Status(Open),
            None,
            None
          )
        )
      )
      .value
    assert(md.contains("**🟡 Outstanding > Highest value**"), md)
    assert(!md.contains("****"), md)

  test("an empty list says so rather than rendering an empty table"):
    assertEquals(Queue.markdown(Kind.Tasks, Nil), "_tasks: nothing queued._")

  test("Kind.parse accepts exactly the three list names"):
    assertEquals(Kind.all, List("acquire", "tasks", "log"))
    assertEquals(Kind.parse(Queue.Kind.Label(" Acquire ")).map(_.file), Some("ACQUIRE.md"))
    assertEquals(Kind.parse(Queue.Kind.Label("notes")), None)

  // ── input validation (these used to reach the INSERT unchecked) ──────────────────────────────

  test("status is whitelisted: open | done | note"):
    assertEquals(Queue.statusOf(Some(QueueItem.Status("done"))), Right(Some("done")))
    assertEquals(Queue.statusOf(Some(QueueItem.Status("  OPEN "))), Right(Some("open")))
    assertEquals(Queue.statusOf(None), Right(None))
    assertEquals(Queue.statusOf(Some(QueueItem.Status(""))), Right(None))
    assertEquals(
      Queue.statusOf(Some(QueueItem.Status("pending"))),
      Left(
        Problem.Invalid.Unknown.here(
          Problem.TypeName.of(QueueItem.Status),
          Problem.Raw("pending"),
          Vector(Problem.Raw("open"), Problem.Raw("done"), Problem.Raw("note"))
        )
      )
    )

  test("created_at must be an ISO instant — it is cast to timestamptz in SQL"):
    assertEquals(
      Queue.createdAtOf(Some(QueueItem.CreatedAt("2026-08-25T00:00:00Z"))),
      Right(Some("2026-08-25T00:00:00Z"))
    )
    assertEquals(Queue.createdAtOf(None), Right(None))
    val e = Queue.createdAtOf(Some(QueueItem.CreatedAt("yesterday"))).swap.toOption.get
    assert(
      e match
        case Problem.Invalid.Unreadable(t, raw, _, _) =>
          t == Problem.TypeName.of(QueueItem.CreatedAt) && raw == Problem.Raw("yesterday")
        case _ => false
      ,
      e
    )
    assert(Queue.createdAtOf(Some(QueueItem.CreatedAt("2026-08-25"))).isLeft, "a bare date is not an instant")
    assert(Queue.createdAtOf(Some(QueueItem.CreatedAt("'); drop table queue_items --"))).isLeft)

  test("a patch changes only what it carries; a blank topic or reason clears it"):
    val cur = QueueItem(
      QueueItem.Id(7),
      QueueItem.List("acquire"),
      QueueItem.Text("a book"),
      Some(QueueItem.Topic("Books")),
      QueueItem.Status(Open),
      Some(Queue.Rationale("for the review")),
      None,
      None
    )
    assertEquals(Queue.applyPatch(cur, QueuePatch()), cur)
    assertEquals(
      Queue.applyPatch(cur, QueuePatch(text = Some(QueueItem.Text("a better book")))),
      cur.modify(_.text).setTo(QueueItem.Text("a better book"))
    )
    assertEquals(Queue.applyPatch(cur, QueuePatch(topic = Some(QueueItem.Topic("  Papers ")))).topic, Some("Papers"))
    assertEquals(Queue.applyPatch(cur, QueuePatch(topic = Some(QueueItem.Topic("  ")))).topic, None)
    assertEquals(Queue.applyPatch(cur, QueuePatch(reason = Some(Queue.Rationale("")))).reason, None)
    assertEquals(Queue.applyPatch(cur, QueuePatch(status = Some(QueueItem.Status(" DONE ")))).status, Done)
    // an item edited in the web UI keeps its other fields
    val edited = Queue.applyPatch(cur, QueuePatch(text = Some(QueueItem.Text("x")), topic = Some(QueueItem.Topic(""))))
    assertEquals((edited.status, edited.reason, edited.id), (Open, Some(Rationale("for the review")), 7))

  test("a patched text may be absent but never blank"):
    assertEquals(Queue.textOf(None), Right(None))
    assertEquals(Queue.textOf(Some(QueueItem.Text("a thing"))), Right(Some("a thing")))
    assertEquals(Queue.textOf(Some(QueueItem.Text(" \n "))), Left(Problem.Invalid.Missing.here(Problem.Field("text"))))

  // ── fulfilling an acquisition item ─────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def item(
      id: QueueItem.Id,
      list: QueueItem.List = QueueItem.List("acquire"),
      status: QueueItem.Status = QueueItem.Status(Open),
      reason: Option[Queue.Rationale] = None
  ) =
    QueueItem(id, list, QueueItem.Text(s"item ${id.value}"), Some(QueueItem.Topic("linear_b")), status, reason)

  test("a fulfilled item is ticked off as ingested as [[citekey]], keeping why it was wanted"):
    assertEquals(
      Queue.fulfilledReason(None, Citekey("lambert2013_babylonian")),
      Queue.Rationale("ingested as [[lambert2013_babylonian]]")
    )
    assertEquals(
      Queue.fulfilledReason(Some(Queue.Rationale("  ")), Citekey("a2020_b")),
      Queue.Rationale("ingested as [[a2020_b]]")
    )
    assertEquals(
      (Queue
        .fulfilledReason(
          Some(Queue.Rationale("Nagy 2001 argues against it")),
          Citekey("finkelberg2000_cypria")
        )),
      Queue.Rationale("Nagy 2001 argues against it · ingested as [[finkelberg2000_cypria]]")
    )
    // recording the same source twice adds nothing
    assertEquals(
      (Queue
        .fulfilledReason(Some(Queue.Rationale("ingested as [[a2020_b]]")), Citekey("a2020_b"))),
      Queue.Rationale("ingested as [[a2020_b]]")
    )
    assertEquals(
      Queue.fulfil(item(QueueItem.Id(567)), Citekey("lambert2013_babylonian")),
      QueuePatch(
        status = Some(QueueItem.Status(Done)),
        reason = Some(Queue.Rationale("ingested as [[lambert2013_babylonian]]"))
      )
    )

  test("an upload may name only an existing, open acquisition item"):
    assertEquals(Queue.fulfillable(QueueItem.Id(7), Some(item(QueueItem.Id(7)))), Right(item(QueueItem.Id(7))))
    val itemId = Problem.TypeName.of(QueueItem.Id)
    assertEquals(
      Queue.fulfillable(QueueItem.Id(7), None),
      Left(Problem.NotFound.Entity(itemId, Problem.Raw("7")))
    )
    assertEquals(
      Queue.fulfillable(QueueItem.Id(7), Some(item(QueueItem.Id(7), list = QueueItem.List("tasks")))),
      Left(
        Problem.Conflict.WrongState(
          itemId,
          Problem.Raw("7"),
          Problem.Conflict.State("tasks"),
          Vector(Problem.Conflict.State("acquire"))
        )
      )
    )
    assertEquals(
      Queue.fulfillable(QueueItem.Id(7), Some(item(QueueItem.Id(7), status = QueueItem.Status(Done)))),
      Left(
        Problem.Conflict.WrongState(
          itemId,
          Problem.Raw("7"),
          Problem.Conflict.State("done"),
          Vector(Problem.Conflict.State("open"))
        )
      )
    )
    assert(Queue.fulfillable(QueueItem.Id(7), Some(item(QueueItem.Id(7), status = QueueItem.Status(Note)))).isLeft)
