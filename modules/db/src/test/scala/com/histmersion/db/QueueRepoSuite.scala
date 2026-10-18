package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.Queue.*

import com.augustnagro.magnum.*

/** The bookkeeping queues against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise.
  *
  * The parser and renderer are pure and covered by `core/QueueSuite` on the REAL `meta` files; what is
  * tested here is that a row survives the database unchanged — multi-line text, unicode headings,
  * status transitions and the insertion order the export depends on. */
class QueueRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d)
    d


  /** `queue_items` is a small, GLOBAL table and this suite rewrites whole lists, while the server
    * module's HTTP suite does the same against the SAME test database — and sbt runs the two module
    * test tasks in parallel. A Postgres advisory lock (held on its own connection for the life of the
    * suite) serialises them; without it one suite's `DELETE` pulls the row out from under the other.
    * `var` because the connection must outlive `beforeAll` and be closed in `afterAll`. */
  private var lock: java.sql.Connection = null
  private val LockKey = 2808 // stream 2.8

  override def beforeAll(): Unit =
    if testDb.isDefined then
      lock = ds.getConnection
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_lock($LockKey)") } finally st.close()

  override def afterSuite(): Unit =
    if lock != null then
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_unlock($LockKey)") } finally st.close()
      lock.close()
      lock = null

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds)(sql"DELETE FROM queue_items".update.run())

  /** Small, but it exercises every shape the real files use. */
  private val acquireFile =
    """# 📥 ACQUIRE — the acquisition queue
      |
      |Grouped by topic.
      |
      |---
      |
      |## 🟡 Outstanding
      |
      |### Historiography
      |*Read each theory next to its refutation.*
      |
      |**Highest value**
      |- [ ] **Cynthia Eller**, *The Myth of Matriarchal Prehistory* (2000)
      |- [ ] **Ronald Hutton**, *The Triumph of the Moon* (1999)
      |
      |## ✅ Completed
      |- [x] 24 foundational books
      |""".stripMargin

  private val logFile =
    """# 📓 LOG — project worklog
      |
      |Newest first.
      |
      |---
      |
      |## 2026-06-25 — Bookkeeping consolidation
      |- Merged the scattered working files into `meta/`: **ACQUIRE.md** (user queue),
      |  **TASKS.md** (agent backlog), **LOG.md** (this).
      |- Recorded the convention in `CLAUDE.md`.
      |
      |## 2026-06-22/24 — Corpus build
      |- Discovery → fetch → convert → enrich, across four topics.
      |""".stripMargin

  private def importFile(kind: Kind, text: String) =
    QueueRepo.replaceAll(ds, kind, Queue.parse(kind, text), replace = true)

  private def exported(kind: Kind) = Queue.render(kind, QueueRepo.list(ds, kind, all = true).toList)

  // ── CRUD ─────────────────────────────────────────────────────────────────────────────────────

  test("add → list → done → list: an item leaves the open list when it is closed"):
    val item = QueueRepo.add(ds, Kind.Tasks, QueueAdd("enrich heggarty2023", Some("Backlog")))
    assertEquals(item.list, "tasks")
    assertEquals(item.status, Open)
    assertEquals(item.topic, Some("Backlog"))
    assert(item.createdAt.isDefined && item.doneAt.isEmpty)
    assertEquals(QueueRepo.list(ds, Kind.Tasks, all = false).map(_.id), Vector(item.id))

    val done = QueueRepo.patch(ds, item.id, QueuePatch(status = Some(Done))).toOption.get
    assertEquals(done.status, Done)
    assert(done.doneAt.isDefined, "closing an item stamps done_at")
    assertEquals(QueueRepo.list(ds, Kind.Tasks, all = false), Vector.empty)
    assertEquals(QueueRepo.list(ds, Kind.Tasks, all = true).map(_.id), Vector(item.id))

  test("reopening an item clears its completion date"):
    val i = QueueRepo.add(ds, Kind.Tasks, QueueAdd("x"))
    val _ = QueueRepo.patch(ds, i.id, QueuePatch(status = Some(Done)))
    val reopened = QueueRepo.patch(ds, i.id, QueuePatch(status = Some(Open))).toOption.get
    assertEquals(reopened.status, Open)
    assertEquals(reopened.doneAt, None)

  test("patch changes only what it carries"):
    val i = QueueRepo.add(ds, Kind.Acquire, QueueAdd("a book", Some("Books")))
    val p = QueueRepo.patch(ds, i.id, QueuePatch(text = Some("a better book"))).toOption.get
    assertEquals(p.text, "a better book")
    assertEquals(p.topic, Some("Books"))
    assertEquals(p.status, Open)

  test("a blank topic or reason in a patch clears it; the rest of the row stays"):
    val i = QueueRepo.add(ds, Kind.Tasks, QueueAdd("tidy up", Some("Chores"), reason = Some("why not")))
    val p = QueueRepo.patch(ds, i.id, QueuePatch(topic = Some(""), reason = Some(" "))).toOption.get
    assertEquals((p.topic, p.reason, p.text, p.status), (None, None, "tidy up", Open))
    val q = QueueRepo.patch(ds, i.id, QueuePatch(topic = Some("Chores > Later"))).toOption.get
    assertEquals(q.topic, Some("Chores > Later"))

  test("delete removes the row and returns what was removed"):
    val i = QueueRepo.add(ds, Kind.Log, QueueAdd("did a thing"))
    assertEquals(QueueRepo.delete(ds, i.id).map(_.text), Right("did a thing"))
    assertEquals(QueueRepo.get(ds, i.id), None)

  test("patching or deleting an unknown id is a not_found value, not an exception"):
    assertEquals(QueueRepo.patch(ds, 987654321, QueuePatch(status = Some(Done))).left.map(_.code),
      Left(Some(Write.CodeNotFound)))
    assertEquals(QueueRepo.delete(ds, 987654321).left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("the three lists are independent"):
    Kind.values.foreach(k => { val _ = QueueRepo.add(ds, k, QueueAdd(s"item for ${k.label}")) })
    Kind.values.foreach(k => assertEquals(QueueRepo.list(ds, k, all = true).map(_.text),
      Vector(s"item for ${k.label}")))

  test("the CHECK constraint refuses a list name that is not one of the three"):
    intercept[Exception]:
      connect(ds)(sql"INSERT INTO queue_items (list, text) VALUES ('notes', 'x')".update.run())

  // ── import / export ──────────────────────────────────────────────────────────────────────────

  test("import then export reproduces the file, through the database"):
    val (rows, removed) = importFile(Kind.Acquire, acquireFile)
    assertEquals(removed, 0)
    assert(rows > 0)
    assertEquals(exported(Kind.Acquire), acquireFile)

  test("a multi-line bullet and a date range survive the round trip"):
    val _ = importFile(Kind.Log, logFile)
    assertEquals(exported(Kind.Log), logFile)
    val stored = QueueRepo.list(ds, Kind.Log, all = true)
    assert(stored.exists(_.text.contains("\n  **TASKS.md** (agent backlog)")), "continuation lines kept")
    assert(stored.exists(_.createdAt.exists(_.startsWith("2026-06-22"))), "the range's first day is stored")

  test("importing twice replaces rather than doubling"):
    val _              = importFile(Kind.Acquire, acquireFile)
    val before         = QueueRepo.list(ds, Kind.Acquire, all = true).size
    val (_, removed)   = importFile(Kind.Acquire, acquireFile)
    assertEquals(removed, before)
    assertEquals(QueueRepo.list(ds, Kind.Acquire, all = true).size, before)
    assertEquals(exported(Kind.Acquire), acquireFile)

  test("replace = false appends instead"):
    val _            = importFile(Kind.Acquire, acquireFile)
    val before       = QueueRepo.list(ds, Kind.Acquire, all = true).size
    val (_, removed) = QueueRepo.replaceAll(ds, Kind.Acquire, Queue.parse(Kind.Acquire, acquireFile), replace = false)
    assertEquals(removed, 0)
    assertEquals(QueueRepo.list(ds, Kind.Acquire, all = true).size, before * 2)

  test("an item added after an import lands in its topic group and re-exports cleanly"):
    val _ = importFile(Kind.Acquire, acquireFile)
    val topic = s"🟡 Outstanding${Sep}Historiography${Sep}**Highest value**"
    val _ = QueueRepo.add(ds, Kind.Acquire, QueueAdd("**J.Z. Smith**, *Drudgery Divine* (1990)", Some(topic)))
    val out = exported(Kind.Acquire)
    assertEquals(out.linesIterator.count(_ == "**Highest value**"), 1, out)
    assert(out.contains("- [ ] **Ronald Hutton**, *The Triumph of the Moon* (1999)\n- [ ] **J.Z. Smith**"), out)
    // and the result is a fixpoint
    val _ = importFile(Kind.Acquire, out)
    assertEquals(exported(Kind.Acquire), out)

  test("a log line added with no topic joins today's entry"):
    val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString
    val _ = QueueRepo.add(ds, Kind.Log, QueueAdd("ran the lint", Some(today)))
    assert(exported(Kind.Log).contains(s"## $today\n- ran the lint"), exported(Kind.Log))
