package com.histmersion
package domain.automation.worklists

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.*
import com.histmersion.db.DbSuite
import com.histmersion.domain.library.Doi
import com.histmersion.api.automation.Goal
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.library.Citekey

/** The contract the work lists' kernel keeps, run on [[QueueService.InMemory]] and on the database (a scratch database
  * per test, gated on HISTMERSION_TEST_DB_URL): a row survives unchanged — multi-line text, unicode headings, status
  * transitions and the insertion order the export depends on —, a done item keeps its first done time, and every scope
  * reads in id order.
  */
abstract class QueueServiceContract(name: String) extends DbSuite:

  /** A new, empty kernel; None when this run has no such store. */
  protected def fresh(test: String): Option[QueueService]

  protected def qtest(title: String)(body: QueueService => Unit): Unit =
    test(title)(fresh(title).foreach(body))

  /** Small, but it exercises every shape the real files use. */
  private val acquireFile = Queue.FileText(
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
  )

  private val logFile = Queue.FileText(
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
  )

  private def importFile(q: QueueService, kind: Kind, text: Queue.FileText): (Int, Int) =
    val c = q.replace(kind, Queue.parse(kind, text), ImportMode.Replace)
    (c.written.value, c.removed.value)

  private def exported(q: QueueService, kind: Kind) = Queue.render(kind, q.list(kind, QueueScope.All))
  private def open(q: QueueService, kind: Kind) = q.list(kind, QueueScope.Open)
  private def every(q: QueueService, kind: Kind) = q.list(kind, QueueScope.All)
  private def id(i: QueueItem) = QueueItemId(i.id.value)
  private def status(raw: String) = QueueItem.Status(raw)

  // ── CRUD ─────────────────────────────────────────────────────────────────────────────────────

  qtest("add → list → done → list: an item leaves the open list when it is closed") { q =>
    val item = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("enrich heggarty2023"), Some(QueueItem.Topic("Backlog"))))
    assertEquals(item.list, "tasks")
    assertEquals(item.status, Open)
    assertEquals(item.topic, Some("Backlog"))
    assert(item.createdAt.isDefined && item.doneAt.isEmpty)
    assertEquals(open(q, Kind.Tasks).map(_.id), List(item.id))

    val done = q.patch(id(item), QueuePatch(status = Some(status(Done)))).toOption.get
    assertEquals(done.status, Done)
    assert(done.doneAt.isDefined, "closing an item stamps done_at")
    assertEquals(open(q, Kind.Tasks), Nil)
    assertEquals(every(q, Kind.Tasks).map(_.id), List(item.id))
  }

  qtest("an item marked done keeps its first done time when patched again") { q =>
    val t = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("read Cline 2014")))
    val done = q.patch(id(t), QueuePatch(status = Some(status(Done)))).toOption.get
    val again = q.patch(id(t), QueuePatch(text = Some(QueueItem.Text("read Cline 2014, ch. 3")))).toOption.get
    assertEquals(again.doneAt, done.doneAt)
    assertEquals(open(q, Kind.Tasks), Nil)
  }

  qtest("reopening an item clears its completion date") { q =>
    val i = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("x")))
    val _ = q.patch(id(i), QueuePatch(status = Some(status(Done))))
    val reopened = q.patch(id(i), QueuePatch(status = Some(status(Open)))).toOption.get
    assertEquals(reopened.status, Open)
    assertEquals(reopened.doneAt, None)
  }

  qtest("an item made done is stamped done at once") { q =>
    val i = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("already done"), status = Some(status(Done))))
    assert(i.doneAt.isDefined, i)
  }

  qtest("patch changes only what it carries") { q =>
    val i = q.add(Kind.Acquire, QueueAdd(QueueItem.Text("a book"), Some(QueueItem.Topic("Books"))))
    val p = q.patch(id(i), QueuePatch(text = Some(QueueItem.Text("a better book")))).toOption.get
    assertEquals(p.text, "a better book")
    assertEquals(p.topic, Some("Books"))
    assertEquals(p.status, Open)
  }

  qtest("a blank topic or reason in a patch clears it; the rest of the row stays") { q =>
    val i = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("tidy up"), Some(QueueItem.Topic("Chores")), reason = Some(QueueItem.Reason("why not"))))
    val p = q.patch(id(i), QueuePatch(topic = Some(QueueItem.Topic("")), reason = Some(QueueItem.Reason(" ")))).toOption.get
    assertEquals((p.topic, p.reason, p.text, p.status), (None, None, "tidy up", Open))
    val r = q.patch(id(i), QueuePatch(topic = Some(QueueItem.Topic("Chores > Later")))).toOption.get
    assertEquals(r.topic, Some("Chores > Later"))
  }

  qtest("delete removes the row and returns what was removed") { q =>
    val i = q.add(Kind.Log, QueueAdd(QueueItem.Text("did a thing")))
    assertEquals(q.delete(id(i)).map(_.text), Right("did a thing"))
    assertEquals(q.get(id(i)), None)
  }

  qtest("patching or deleting an unknown id is a not-found value, not an exception") { q =>
    val nope = QueueItemId(987654321)
    assertEquals(q.patch(nope, QueuePatch(status = Some(status(Done)))).left.map(_.message), Left("No queue item #987654321"))
    assertEquals(q.delete(nope), Left(QueueError.NotFound(nope)))
  }

  qtest("the three lists are independent") { q =>
    Kind.values.foreach { k =>
      val _ = q.add(k, QueueAdd(QueueItem.Text(s"item for ${k.label}")))
    }
    Kind.values.foreach(k => assertEquals(every(q, k).map(_.text), List(s"item for ${k.label}")))
  }

  qtest("byIds: any list and status, by id; an unknown id is left out") { q =>
    val a = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("a")))
    val b = q.add(Kind.Acquire, QueueAdd(QueueItem.Text("b"), status = Some(status(Done))))
    assertEquals(q.byIds(List(id(b), QueueItemId(987654321), id(a))).map(_.id), List(a.id, b.id))
    assertEquals(q.byIds(Nil), Nil)
  }

  qtest("naming: the items of a list whose text names the DOI, any status") { q =>
    val a = q.add(Kind.Acquire, QueueAdd(QueueItem.Text("Drake 2012 doi:10.1000/XYZ.1")))
    val _ = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("Drake 2012 doi:10.1000/xyz.1")))
    val _ = q.add(Kind.Acquire, QueueAdd(QueueItem.Text("Drake 2012 doi:10.1000/xyz.10")))
    assertEquals(q.naming(Kind.Acquire, Doi.assume(" 10.1000/XYZ.1 ")).map(_.id), List(a.id))
    assertEquals(q.naming(Kind.Acquire, Doi.assume("")), Nil)
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
    assert(stored.exists(_.text.value.contains("\n  **TASKS.md** (agent backlog)")), "continuation lines kept")
    assert(stored.exists(_.createdAt.exists(_.value.startsWith("2026-06-22"))), "the range's first day is stored")
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
    val topic = QueueItem.Topic(s"🟡 Outstanding${Sep}Historiography$Sep**Highest value**")
    val _ = q.add(Kind.Acquire, QueueAdd(QueueItem.Text("**J.Z. Smith**, *Drudgery Divine* (1990)"), Some(topic)))
    val out = exported(q, Kind.Acquire)
    assertEquals(out.value.linesIterator.count(_ == "**Highest value**"), 1, out)
    assert(out.value.contains("- [ ] **Ronald Hutton**, *The Triumph of the Moon* (1999)\n- [ ] **J.Z. Smith**"), out)
    val _ = importFile(q, Kind.Acquire, Queue.FileText(out.value))
    assertEquals(exported(q, Kind.Acquire), out)
  }

  // ── goals and jobs ───────────────────────────────────────────────────────────────────────────

  qtest(
    "an item keeps its goal and the run that wrote it; the job that ticks it off is recorded, reopening forgets it"
  ) { q =>
    val added = q.add(Kind.Acquire, QueueAdd(QueueItem.Text("Drake 2012"), goal = Some(Goal.Slug("zz-links-goal")), job = Some(SpatialHierarchyResult.Job.Id("zz-run-1"))))
    assertEquals((added.goal, added.createdBy, added.closedBy), (Some("zz-links-goal"), Some("zz-run-1"), None))
    val done =
      q.patch(id(added), Queue.fulfil(added, Citekey("drake2012"), Some(QueueItem.ClosedBy("zz-job-9")))).fold(e => fail(e.message), identity)
    assertEquals((done.status, done.closedBy, done.createdBy), (Done, Some("zz-job-9"), Some("zz-run-1")))
    assertEquals(q.patch(id(added), QueuePatch(status = Some(status(Open)))).map(_.closedBy), Right(None))
  }

  qtest("a goal's items: its own, and those its links name; open only unless done ones are asked for") { q =>
    val own = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("own item"), goal = Some(Goal.Slug("zz-links-goal"))))
    val linked = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("linked item")))
    val other = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("someone else's")))
    val closed = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("closed item"), goal = Some(Goal.Slug("zz-links-goal")), status = Some(status(Done))))
    val _ = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("a note"), goal = Some(Goal.Slug("zz-links-goal")), status = Some(status(Note))))
    def forGoal(k: Kind, withDone: QueueScope.ForGoal.WithDone) =
      q.list(k, QueueScope.ForGoal(QueueScope.ForGoal.Goal("zz-links-goal"), Set(QueueScope.ForGoal.Linked(linked.id.toString), QueueScope.ForGoal.Linked("not-an-id")), withDone)).map(_.id)
    assertEquals(forGoal(Kind.Tasks, withDone = QueueScope.ForGoal.WithDone(false)), List(own.id, linked.id))
    assertEquals(forGoal(Kind.Tasks, withDone = QueueScope.ForGoal.WithDone(true)), List(own.id, linked.id, closed.id))
    assert(!forGoal(Kind.Tasks, withDone = QueueScope.ForGoal.WithDone(true)).contains(other.id))
    assertEquals(forGoal(Kind.Acquire, withDone = QueueScope.ForGoal.WithDone(true)), Nil)
  }

  qtest("a run's or job's items: those it wrote and those it ticked off") { q =>
    val wrote = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("wrote"), job = Some(SpatialHierarchyResult.Job.Id("zz-run-7"))))
    val ticked = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("ticked")))
    val _ = q.patch(id(ticked), QueuePatch(status = Some(status(Done)), closedBy = Some(QueuePatch.ClosedBy("zz-run-7"))))
    val _ = q.add(Kind.Tasks, QueueAdd(QueueItem.Text("another run's"), job = Some(SpatialHierarchyResult.Job.Id("zz-run-8"))))
    assertEquals(q.list(Kind.Tasks, QueueScope.ForJob(QueueScope.ForJob.Job("zz-run-7"))).map(_.id), List(wrote.id, ticked.id))
  }
