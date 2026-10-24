package com.histmersion.api


import com.histmersion.api.Queue.*

/** The bookkeeping parser/renderer (plan §5.2 stream 2.8), tested against the REAL
  * `meta/{ACQUIRE,TASKS,LOG}.md` from the research workspace, copied verbatim into
  * `src/test/resources/meta/`. Pure: no database, no server. */
class QueueSuite extends munit.FunSuite:

  private def fixture(name: String): String =
    val is = getClass.getClassLoader.getResourceAsStream(s"meta/$name")
    assert(is != null, s"missing fixture meta/$name")
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString finally is.close()

  /** Parsed rows as the database would hand them back: ids in insertion order. */
  private def store(kind: Kind, text: String): List[QueueItem] =
    Queue.parse(kind, text).zipWithIndex.map { (r, i) =>
      QueueItem(i + 1, kind.label, r.text, r.topic, r.status.getOrElse(Open), r.reason, r.createdAt, None)
    }

  private def roundTrip(kind: Kind, text: String): String = Queue.render(kind, store(kind, text))

  /** A readable line diff, so a failure says WHICH line moved rather than dumping 4 kB twice. */
  private def diff(a: String, b: String): String =
    val (as, bs) = (a.linesIterator.toVector, b.linesIterator.toVector)
    (0 until math.max(as.size, bs.size))
      .flatMap { i =>
        (as.lift(i), bs.lift(i)) match
          case (x, y) if x == y => None
          case (x, y)           => Some(s"line ${i + 1}:\n  expected: ${x.getOrElse("<eof>")}\n  actual:   ${y.getOrElse("<eof>")}")
      }
      .take(10)
      .mkString("\n")

  // ── the three real files ─────────────────────────────────────────────────────────────────────

  test("ACQUIRE.md round-trips byte for byte"):
    val original = fixture("ACQUIRE.md")
    val out      = roundTrip(Kind.Acquire, original)
    assertEquals(out, original, diff(original, out))

  test("LOG.md round-trips byte for byte"):
    val original = fixture("LOG.md")
    val out      = roundTrip(Kind.Log, original)
    assertEquals(out, original, diff(original, out))

  test("TASKS.md round-trips (one blank line the original omits before a bold group)"):
    val original = fixture("TASKS.md")
    val out      = roundTrip(Kind.Tasks, original)
    // the renderer is canonical, not byte-preserving: it always separates a heading from a preceding
    // block, where the hand-written file does so everywhere EXCEPT under `## 📋 Backlog`.
    assertEquals(out.linesIterator.filter(_.trim.nonEmpty).toList,
      original.linesIterator.filter(_.trim.nonEmpty).toList, diff(original, out))
    assertEquals(out.count(_ == '\n') - original.count(_ == '\n'), 1)

  test("export is a fixpoint: re-importing an export reproduces it exactly"):
    Kind.values.foreach { kind =>
      val once  = roundTrip(kind, fixture(kind.file))
      val twice = roundTrip(kind, once)
      assertEquals(twice, once, s"${kind.file}: " + diff(once, twice))
    }

  // ── what parsing actually captured ───────────────────────────────────────────────────────────

  test("ACQUIRE: outstanding items are open, completed ones are done, prose is a note"):
    val rows = Queue.parse(Kind.Acquire, fixture("ACQUIRE.md"))
    val open = rows.filter(_.status.contains(Open))
    val done = rows.filter(_.status.contains(Done))
    assert(open.exists(_.text.contains("Cynthia Eller")), "the Eller line is an open item")
    assert(done.exists(_.text.contains("24 foundational books")), "the completed section is done")
    assert(open.forall(_.topic.exists(_.startsWith("🟡 Outstanding"))))
    assert(done.forall(_.topic.exists(_.startsWith("✅ Completed"))))
    // the file's own preamble survives as a note with no topic
    assert(rows.exists(r => r.status.contains(Note) && r.topic.isEmpty && r.text.startsWith("# 📥 ACQUIRE")))

  test("ACQUIRE: a whole-line bold group becomes the third level of the topic path"):
    val rows = Queue.parse(Kind.Acquire, fixture("ACQUIRE.md"))
    val eller = rows.find(_.text.contains("Cynthia Eller")).get
    val parts = Queue.path(eller.topic)
    assertEquals(parts.size, 3)
    assertEquals(parts(0), "🟡 Outstanding")
    assert(parts(1).startsWith("Historiography of grand theories"), parts(1))
    assertEquals(parts(2), "**Highest value — get first**")

  test("TASKS: a bold line that is NOT the whole line stays prose"):
    val rows = Queue.parse(Kind.Tasks, fixture("TASKS.md"))
    val drained = rows.find(_.text.startsWith("**Original ingestion queues")).get
    assertEquals(drained.status, Some(Note))
    // …while the trailing `:**` group heading IS a heading
    assert(rows.exists(_.topic.exists(_.endsWith("**Enrichment / compile follow-ups:**"))))

  test("TASKS: the currency-checkpoint table is kept verbatim as one note"):
    val rows = Queue.parse(Kind.Tasks, fixture("TASKS.md"))
    val table = rows.find(_.text.contains("| subtopic | checkpoint held | since |")).get
    assertEquals(table.status, Some(Note))
    assert(table.text.contains("| IE homeland (ling.+arch.) |"), "the whole table is one block")
    assert(table.text.contains("**Genre note:**"), "trailing prose stays in the same block")

  test("LOG: entries keep their heading as the topic and their date as createdAt"):
    val rows = Queue.parse(Kind.Log, fixture("LOG.md"))
    val bullets = rows.filterNot(_.status.contains(Note))
    assert(bullets.nonEmpty)
    assert(bullets.forall(_.createdAt.isDefined), "every bullet inherits its entry's date")
    // a date RANGE heading keeps its first day
    assert(rows.exists(_.createdAt.contains("2026-06-24T00:00:00Z")),
      rows.flatMap(_.createdAt).distinct.mkString(","))
    assert(rows.exists(_.topic.contains("2026-06-24/25 — Frontier passes + reviews-first strategy")))

  test("LOG: a bullet's indented continuation lines belong to it"):
    val rows = Queue.parse(Kind.Log, fixture("LOG.md"))
    val merged = rows.find(_.text.startsWith("Merged the scattered working files")).get
    assert(merged.text.contains("\n  acquisition queue)"), merged.text)

  // ── the renderer on its own ──────────────────────────────────────────────────────────────────

  test("an empty list renders a usable file, and that file re-imports to nothing but its preamble"):
    Kind.values.foreach { kind =>
      val out = Queue.render(kind, Nil)
      assert(out.startsWith("# "), out.take(40))
      val rows = Queue.parse(kind, out)
      assert(rows.forall(_.status.contains(Note)), rows.toString)
    }

  test("checkbox lists render [ ] / [x]; the log renders plain bullets"):
    def item(status: String) = QueueItem(1, "x", "a thing", Some("Topic"), status, None, None)
    assert(Queue.render(Kind.Acquire, List(item(Open))).contains("- [ ] a thing"))
    assert(Queue.render(Kind.Acquire, List(item(Done))).contains("- [x] a thing"))
    assert(Queue.render(Kind.Log, List(item(Open))).contains("- a thing"))
    assert(!Queue.render(Kind.Log, List(item(Open))).contains("[ ]"))

  test("a topic path renders ## / ### / bold, one level per segment; a heading hugs its content"):
    val out = Queue.render(Kind.Tasks,
      List(QueueItem(1, "tasks", "x", Some(s"A${Sep}B${Sep}**C**"), Open, None, None)))
    // heading → heading is separated; heading → item hugs
    assert(out.endsWith("\n## A\n\n### B\n\n**C**\n- [ ] x\n"), out)

  test("later items join an existing topic group instead of repeating its heading"):
    val out = Queue.render(Kind.Tasks, List(
      QueueItem(1, "tasks", "first", Some("A"), Open, None, None),
      QueueItem(2, "tasks", "other", Some("B"), Open, None, None),
      QueueItem(3, "tasks", "second", Some("A"), Open, None, None)))
    assertEquals(out.linesIterator.count(_ == "## A"), 1)
    assert(out.indexOf("- [ ] second") > out.indexOf("- [ ] first"))

  test("log entries are ordered newest first, so an added line lands on top"):
    val out = Queue.render(Kind.Log, List(
      QueueItem(1, "log", "old", Some("2026-01-01 — old"), Open, None, Some("2026-01-01T00:00:00Z"), None),
      QueueItem(2, "log", "new", Some("2026-08-25 — new"), Open, None, Some("2026-08-25T00:00:00Z"), None)))
    assert(out.indexOf("## 2026-08-25 — new") < out.indexOf("## 2026-01-01 — old"), out)

  // ── the terminal listing ─────────────────────────────────────────────────────────────────────

  test("the `queue <list>` listing shows ids and hides note blocks"):
    val items = List(
      QueueItem(7, "acquire", "buy a book", Some("Books"), Open, None, None),
      QueueItem(8, "acquire", "# preamble", None, Note, None, None))
    val md = Queue.markdown(Kind.Acquire, items)
    assert(md.contains("`#7`"), md)
    assert(md.contains("1 item(s)"), md)
    assert(!md.contains("preamble"), md)

  test("the listing strips a bold group's own ** so the heading is not doubled"):
    val md = Queue.markdown(Kind.Acquire,
      List(QueueItem(1, "acquire", "x", Some(s"🟡 Outstanding${Sep}**Highest value**"), Open, None, None)))
    assert(md.contains("**🟡 Outstanding > Highest value**"), md)
    assert(!md.contains("****"), md)

  test("an empty list says so rather than rendering an empty table"):
    assertEquals(Queue.markdown(Kind.Tasks, Nil), "_tasks: nothing queued._")

  test("Kind.parse accepts exactly the three list names"):
    assertEquals(Kind.all, List("acquire", "tasks", "log"))
    assertEquals(Kind.parse(" Acquire ").map(_.file), Some("ACQUIRE.md"))
    assertEquals(Kind.parse("notes"), None)

  // ── input validation (these used to reach the INSERT unchecked) ──────────────────────────────

  test("status is whitelisted: open | done | note"):
    assertEquals(Queue.statusOf(Some("done")), Right(Some("done")))
    assertEquals(Queue.statusOf(Some("  OPEN ")), Right(Some("open")))
    assertEquals(Queue.statusOf(None), Right(None))
    assertEquals(Queue.statusOf(Some("")), Right(None))
    val e = Queue.statusOf(Some("pending")).swap.toOption.get
    assert(e.contains("unknown status 'pending'"), e)
    assert(e.contains("open, done, note"), e)

  test("created_at must be an ISO instant — it is cast to timestamptz in SQL"):
    assertEquals(Queue.createdAtOf(Some("2026-08-25T00:00:00Z")), Right(Some("2026-08-25T00:00:00Z")))
    assertEquals(Queue.createdAtOf(None), Right(None))
    val e = Queue.createdAtOf(Some("yesterday")).swap.toOption.get
    assert(e.contains("not an ISO-8601 instant"), e)
    assert(Queue.createdAtOf(Some("2026-08-25")).isLeft, "a bare date is not an instant")
    assert(Queue.createdAtOf(Some("'); drop table queue_items --")).isLeft)

  test("a patch changes only what it carries; a blank topic or reason clears it"):
    val cur = QueueItem(7, "acquire", "a book", Some("Books"), Open, Some("for the review"), None, None)
    assertEquals(Queue.applyPatch(cur, QueuePatch()), cur)
    assertEquals(Queue.applyPatch(cur, QueuePatch(text = Some("a better book"))),
      cur.copy(text = "a better book"))
    assertEquals(Queue.applyPatch(cur, QueuePatch(topic = Some("  Papers "))).topic, Some("Papers"))
    assertEquals(Queue.applyPatch(cur, QueuePatch(topic = Some("  "))).topic, None)
    assertEquals(Queue.applyPatch(cur, QueuePatch(reason = Some(""))).reason, None)
    assertEquals(Queue.applyPatch(cur, QueuePatch(status = Some(" DONE "))).status, Done)
    // an item edited in the web UI keeps its other fields
    val edited = Queue.applyPatch(cur, QueuePatch(text = Some("x"), topic = Some("")))
    assertEquals((edited.status, edited.reason, edited.id), (Open, Some("for the review"), 7))

  test("a patched text may be absent but never blank"):
    assertEquals(Queue.textOf(None), Right(None))
    assertEquals(Queue.textOf(Some("a thing")), Right(Some("a thing")))
    assertEquals(Queue.textOf(Some(" \n ")), Left("queue item text is empty"))

  // ── fulfilling an acquisition item ─────────────────────────────────────────────────────────────

  private def item(id: Int, list: String = "acquire", status: String = Open, reason: Option[String] = None) =
    QueueItem(id, list, s"item $id", Some("linear_b"), status, reason)

  test("a fulfilled item is ticked off as ingested as [[citekey]], keeping why it was wanted"):
    assertEquals(Queue.fulfilledReason(None, "lambert2013_babylonian"), "ingested as [[lambert2013_babylonian]]")
    assertEquals(Queue.fulfilledReason(Some("  "), "a2020_b"), "ingested as [[a2020_b]]")
    assertEquals(Queue.fulfilledReason(Some("Nagy 2001 argues against it"), "finkelberg2000_cypria"),
      "Nagy 2001 argues against it · ingested as [[finkelberg2000_cypria]]")
    // recording the same source twice adds nothing
    assertEquals(Queue.fulfilledReason(Some("ingested as [[a2020_b]]"), "a2020_b"), "ingested as [[a2020_b]]")
    assertEquals(Queue.fulfil(item(567), "lambert2013_babylonian"),
      QueuePatch(status = Some(Done), reason = Some("ingested as [[lambert2013_babylonian]]")))

  test("an upload may name only an existing, open acquisition item"):
    assertEquals(Queue.fulfillable(7, Some(item(7))), Right(item(7)))
    assertEquals(Queue.fulfillable(7, None), Left("no acquisition item 7"))
    assertEquals(Queue.fulfillable(7, Some(item(7, list = "tasks"))),
      Left("item 7 is on the tasks list, not the acquisition list"))
    assertEquals(Queue.fulfillable(7, Some(item(7, status = Done))), Left("acquisition item 7 is already done"))
    assert(Queue.fulfillable(7, Some(item(7, status = Note))).isLeft)
