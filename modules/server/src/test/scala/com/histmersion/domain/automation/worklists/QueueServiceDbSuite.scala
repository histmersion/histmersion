package com.histmersion
package domain.automation.worklists

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.util.database.Transactor
import com.histmersion.api.automation.Queue

import com.augustnagro.magnum.*
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.Provenance

class QueueServiceDbSuite extends QueueServiceContract("db"):
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val scratch =
    ScratchDatabases("histmersion_s7q", AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
  private val made = java.util.concurrent.atomic.AtomicInteger(0)
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  protected def fresh(test: String): Option[QueueService] =
    testDb.map(_ => QueueService(Transactor(scratch(made.incrementAndGet().toString))))

  test("G94: writing over or removing a row that is gone answers false, never a success over nothing"):
    val ds = scratch("gone")
    val item = QueueRows.add(ds, Queue.Kind.Tasks, Queue.QueueAdd(Queue.QueueItem.Text("x")))
    assert(QueueRows.removeRow(ds, item.id), "the row was there")
    assert(!QueueRows.removeRow(ds, item.id), "the row is gone")
    assert(!QueueRows.writeOver(ds, item), "an update of the gone row writes nothing")

  test("the CHECK constraint refuses a list name that is not one of the three"):
    val ds = scratch("check")
    intercept[Exception]:
      connect(ds)(
        sql"""INSERT INTO queue_items (list, text, creator, actor)
              VALUES ('notes', 'x', '{"kind":"unknown"}', 'person')""".update.run()
      )

  test("V113: a row needs a creator; an open task needs an actor, and an actor is person or agents"):
    val ds = scratch("creator")
    def insert(list: String, status: String, creator: Option[String], actor: Option[String]): Unit =
      connect(ds)(
        sql"""INSERT INTO queue_items (list, text, status, creator, actor)
              VALUES ($list, 'x', $status, $creator::jsonb, $actor)""".update.run()
      ): Unit
    intercept[Exception](insert("log", "open", None, Some("person")))
    intercept[Exception](insert("tasks", "open", Some("""{"kind":"unknown"}"""), None))
    intercept[Exception](insert("tasks", "open", Some("""{"kind":"unknown"}"""), Some("robot")))
    insert("tasks", "done", Some("""{"kind":"unknown"}"""), None)
    insert("log", "open", Some("""{"kind":"unknown"}"""), None)
    insert("tasks", "open", Some("""{"kind":"unknown"}"""), Some("agents"))
    val actors = connect(ds)(sql"SELECT coalesce(actor, '-') FROM queue_items ORDER BY id".query[String].run())
    assertEquals(actors.toList, List("-", "-", "agents"))

  test("a stored creator this build cannot read is shown as it is, never as a default"):
    val ds = scratch("unreadable")
    val item = QueueRows.add(ds, Queue.Kind.Tasks, Queue.QueueAdd(Queue.QueueItem.Text("x")))
    connect(ds)(
      sql"""UPDATE queue_items SET creator = '{"kind": 7, "plugin": ["x"]}' WHERE id = ${item.id}""".update.run()
    ): Unit
    assertEquals(QueueRows.get(ds, item.id).flatMap(_.creator), Some(Provenance(Provenance.Unreadable)))
    assertEquals(
      connect(ds)(sql"SELECT creator::text FROM queue_items WHERE id = ${item.id}".query[String].run().toList),
      List("""{"kind": 7, "plugin": ["x"]}"""),
      "and the row keeps what it holds"
    )
