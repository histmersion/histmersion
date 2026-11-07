package com.histmersion.domain.automation.worklists

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.*

/** The contract the work lists' kernel keeps, run on [[QueueService.InMemory]] and on the database (a scratch database
  * per test, gated on HISTMERSION_TEST_DB_URL): a row survives unchanged — multi-line text, unicode headings, status
  * transitions and the insertion order the export depends on —, a done item keeps its first done time, and every scope
  * reads in id order.
  */
abstract class QueueServiceContract(name: String) extends com.histmersion.db.DbSuite:

  /** A new, empty kernel; None when this run has no such store. */
  protected def fresh(test: String): Option[QueueService]

  protected def qtest(title: String)(body: QueueService => Unit): Unit =
    test(title)(fresh(title).foreach(body))

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

  private def importFile(q: QueueService, kind: Kind, text: String): (Int, Int) =
    val c = q.replace(kind, Queue.parse(kind, text), ImportMode.Replace)
    (c.written, c.removed)

  private def exported(q: QueueService, kind: Kind) = Queue.render(kind, q.list(kind, QueueScope.All))
  private def open(q: QueueService, kind: Kind) = q.list(kind, QueueScope.Open)
  private def every(q: QueueService, kind: Kind) = q.list(kind, QueueScope.All)
  private def id(i: QueueItem) = QueueItemId(i.id)

  // ── CRUD ─────────────────────────────────────────────────────────────────────────────────────

  qtest("add → list → done → list: an item leaves the open list when it is closed") { q =>
    val item = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("enrich heggarty2023"), Some(com.histmersion.api.automation.Queue.QueueItem.Topic("Backlog"))))
    assertEquals(item.list, "tasks")
    assertEquals(item.status, Open)
    assertEquals(item.topic, Some("Backlog"))
    assert(item.createdAt.isDefined && item.doneAt.isEmpty)
    assertEquals(open(q, Kind.Tasks).map(_.id), List(item.id))

    val done = q.patch(id(item), QueuePatch(status = Some(Done))).toOption.get
    assertEquals(done.status, Done)
    assert(done.doneAt.isDefined, "closing an item stamps done_at")
    assertEquals(open(q, Kind.Tasks), Nil)
    assertEquals(every(q, Kind.Tasks).map(_.id), List(item.id))
  }

  qtest("an item marked done keeps its first done time when patched again") { q =>
    val t = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("read Cline 2014")))
    val done = q.patch(id(t), QueuePatch(status = Some(Done))).toOption.get
    val again = q.patch(id(t), QueuePatch(text = Some(com.histmersion.api.automation.Queue.QueueItem.Text("read Cline 2014, ch. 3")))).toOption.get
    assertEquals(again.doneAt, done.doneAt)
    assertEquals(open(q, Kind.Tasks), Nil)
  }

  qtest("reopening an item clears its completion date") { q =>
    val i = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("x")))
    val _ = q.patch(id(i), QueuePatch(status = Some(Done)))
    val reopened = q.patch(id(i), QueuePatch(status = Some(Open))).toOption.get
    assertEquals(reopened.status, Open)
    assertEquals(reopened.doneAt, None)
  }

  qtest("an item made done is stamped done at once") { q =>
    val i = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("already done"), status = Some(Done)))
    assert(i.doneAt.isDefined, i)
  }

  qtest("patch changes only what it carries") { q =>
    val i = q.add(Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("a book"), Some(com.histmersion.api.automation.Queue.QueueItem.Topic("Books"))))
    val p = q.patch(id(i), QueuePatch(text = Some(com.histmersion.api.automation.Queue.QueueItem.Text("a better book")))).toOption.get
    assertEquals(p.text, "a better book")
    assertEquals(p.topic, Some("Books"))
    assertEquals(p.status, Open)
  }

  qtest("a blank topic or reason in a patch clears it; the rest of the row stays") { q =>
    val i = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("tidy up"), Some(com.histmersion.api.automation.Queue.QueueItem.Topic("Chores")), reason = Some(com.histmersion.api.automation.Queue.QueueItem.Reason("why not"))))
    val p = q.patch(id(i), QueuePatch(topic = Some(com.histmersion.api.automation.Queue.QueueItem.Topic("")), reason = Some(com.histmersion.api.automation.Queue.QueueItem.Reason(" ")))).toOption.get
    assertEquals((p.topic, p.reason, p.text, p.status), (None, None, "tidy up", Open))
    val r = q.patch(id(i), QueuePatch(topic = Some(com.histmersion.api.automation.Queue.QueueItem.Topic("Chores > Later")))).toOption.get
    assertEquals(r.topic, Some("Chores > Later"))
  }

  qtest("delete removes the row and returns what was removed") { q =>
    val i = q.add(Kind.Log, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("did a thing")))
    assertEquals(q.delete(id(i)).map(_.text), Right("did a thing"))
    assertEquals(q.get(id(i)), None)
  }

  qtest("patching or deleting an unknown id is a not-found value, not an exception") { q =>
    val nope = QueueItemId(987654321)
    assertEquals(q.patch(nope, QueuePatch(status = Some(Done))).left.map(_.message), Left("No queue item #987654321"))
    assertEquals(q.delete(nope), Left(QueueError.NotFound(nope)))
  }

  qtest("the three lists are independent") { q =>
    Kind.values.foreach { k =>
      val _ = q.add(k, QueueAdd(s"item for ${k.label}"))
    }
    Kind.values.foreach(k => assertEquals(every(q, k).map(_.text), List(s"item for ${k.label}")))
  }

  qtest("byIds: any list and status, by id; an unknown id is left out") { q =>
    val a = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("a")))
    val b = q.add(Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("b"), status = Some(Done)))
    assertEquals(q.byIds(List(id(b), QueueItemId(987654321), id(a))).map(_.id), List(a.id, b.id))
    assertEquals(q.byIds(Nil), Nil)
  }

  qtest("naming: the items of a list whose text names the DOI, any status") { q =>
    val a = q.add(Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("Drake 2012 doi:10.1000/XYZ.1")))
    val _ = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("Drake 2012 doi:10.1000/xyz.1")))
    val _ = q.add(Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("Drake 2012 doi:10.1000/xyz.10")))
    assertEquals(q.naming(Kind.Acquire, com.histmersion.domain.library.Doi(" 10.1000/XYZ.1 ")).map(_.id), List(a.id))
    assertEquals(q.naming(Kind.Acquire, com.histmersion.domain.library.Doi("")), Nil)
  }

  // ── import / export ──────────────────────────────────────────────────────────────────────────

  qtest("import then export reproduces the file") { q =>
    val (rows, removed) = importFile(q, Kind.Acquire, acquireFile)
    assertEquals(removed, 0)
    assert(rows > 0)
    assertEquals(exported(q, Kind.Acquire), acquireFile)
  }

  qtest("a multi-line bullet and a date range survive the round trip") { q =>
    val _ = importFile(q, Kind.Log, logFile)
    assertEquals(exported(q, Kind.Log), logFile)
    val stored = every(q, Kind.Log)
    assert(stored.exists(_.text.contains("\n  **TASKS.md** (agent backlog)")), "continuation lines kept")
    assert(stored.exists(_.createdAt.exists(_.startsWith("2026-06-22"))), "the range's first day is stored")
  }

  qtest("importing twice replaces rather than doubling") { q =>
    val _ = importFile(q, Kind.Acquire, acquireFile)
    val before = every(q, Kind.Acquire).size
    val (_, removed) = importFile(q, Kind.Acquire, acquireFile)
    assertEquals(removed, before)
    assertEquals(every(q, Kind.Acquire).size, before)
    assertEquals(exported(q, Kind.Acquire), acquireFile)
  }

  qtest("an append import adds instead") { q =>
    val _ = importFile(q, Kind.Acquire, acquireFile)
    val before = every(q, Kind.Acquire).size
    val c = q.replace(Kind.Acquire, Queue.parse(Kind.Acquire, acquireFile), ImportMode.Append)
    assertEquals(c.removed, 0)
    assertEquals(every(q, Kind.Acquire).size, before * 2)
  }

  qtest("an item added after an import lands in its topic group and re-exports cleanly") { q =>
    val _ = importFile(q, Kind.Acquire, acquireFile)
    val topic = s"🟡 Outstanding${Sep}Historiography$Sep**Highest value**"
    val _ = q.add(Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("**J.Z. Smith**, *Drudgery Divine* (1990)"), Some(topic)))
    val out = exported(q, Kind.Acquire)
    assertEquals(out.linesIterator.count(_ == "**Highest value**"), 1, out)
    assert(out.contains("- [ ] **Ronald Hutton**, *The Triumph of the Moon* (1999)\n- [ ] **J.Z. Smith**"), out)
    val _ = importFile(q, Kind.Acquire, out)
    assertEquals(exported(q, Kind.Acquire), out)
  }

  // ── goals and jobs ───────────────────────────────────────────────────────────────────────────

  qtest(
    "an item keeps its goal and the run that wrote it; the job that ticks it off is recorded, reopening forgets it"
  ) { q =>
    val added = q.add(Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("Drake 2012"), goal = Some(com.histmersion.api.automation.Goal.Slug("zz-links-goal")), job = Some(com.histmersion.api.SpatialHierarchyResult.Job.Id("zz-run-1"))))
    assertEquals((added.goal, added.createdBy, added.closedBy), (Some("zz-links-goal"), Some("zz-run-1"), None))
    val done =
      q.patch(id(added), Queue.fulfil(added, com.histmersion.api.library.Citekey("drake2012"), Some(com.histmersion.api.automation.Queue.QueueItem.ClosedBy("zz-job-9")))).fold(e => fail(e.message), identity)
    assertEquals((done.status, done.closedBy, done.createdBy), (Done, Some("zz-job-9"), Some("zz-run-1")))
    assertEquals(q.patch(id(added), QueuePatch(status = Some(Open))).map(_.closedBy), Right(None))
  }

  qtest("a goal's items: its own, and those its links name; open only unless done ones are asked for") { q =>
    val own = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("own item"), goal = Some(com.histmersion.api.automation.Goal.Slug("zz-links-goal"))))
    val linked = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("linked item")))
    val other = q.add(Kind.Tasks, QueueAdd("someone else's"))
    val closed = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("closed item"), goal = Some("zz-links-goal"), status = Some(Done)))
    val _ = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("a note"), goal = Some("zz-links-goal"), status = Some(Note)))
    def forGoal(k: Kind, withDone: Boolean) =
      q.list(k, QueueScope.ForGoal(com.histmersion.domain.automation.worklists.QueueScope.ForGoal.Goal("zz-links-goal"), Set(linked.id.toString, com.histmersion.domain.automation.worklists.QueueScope.ForGoal.Linked("not-an-id")), withDone)).map(_.id)
    assertEquals(forGoal(Kind.Tasks, withDone = false), List(own.id, linked.id))
    assertEquals(forGoal(Kind.Tasks, withDone = true), List(own.id, linked.id, closed.id))
    assert(!forGoal(Kind.Tasks, withDone = true).contains(other.id))
    assertEquals(forGoal(Kind.Acquire, withDone = true), Nil)
  }

  qtest("a run's or job's items: those it wrote and those it ticked off") { q =>
    val wrote = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("wrote"), job = Some(com.histmersion.api.SpatialHierarchyResult.Job.Id("zz-run-7"))))
    val ticked = q.add(Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("ticked")))
    val _ = q.patch(id(ticked), QueuePatch(status = Some(Done), closedBy = Some("zz-run-7")))
    val _ = q.add(Kind.Tasks, QueueAdd("another run's", job = Some("zz-run-8")))
    assertEquals(q.list(Kind.Tasks, QueueScope.ForJob(com.histmersion.domain.automation.worklists.QueueScope.ForJob.Job("zz-run-7"))).map(_.id), List(wrote.id, ticked.id))
  }
