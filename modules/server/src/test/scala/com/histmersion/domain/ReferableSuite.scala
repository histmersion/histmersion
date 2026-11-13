package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.goals.GoalReferable
import com.histmersion.domain.catalogue.CatalogueReferable
import com.histmersion.domain.library.sources.SourceReferable
import com.histmersion.domain.media.assets.PictureReferable
import com.histmersion.domain.research.articles.ArticleReferable
import com.histmersion.util.database.Transactor
import com.histmersion.domain.Title

import com.augustnagro.magnum.*

import java.time.Instant
import java.util.UUID
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig

/** A reference resolves and describes the same way in memory and in the database (a scratch database of its own, gated
  * on HISTMERSION_TEST_DB_URL): the source a reference names now, after a rename, a merge and a deletion.
  */
class ReferableSuite extends DbSuite:

  private val source = SourceReferable.Kind
  private def key(k: String) = ItemKey(k)

  /** Sources, and how the collection changes under them, over one store. */
  final private case class World(
      referable: Referable,
      add: String => Unit,
      rename: (String, String) => Unit,
      merge: (String, String) => Unit,
      delete: String => Unit
  )

  private def inMemory: World =
    val r = Referable.InMemory(source)
    World(
      r,
      ck => r.put(key(ck), Title(s"Title of $ck"), Some(ItemUid(UUID.randomUUID()))),
      (a, b) => r.rename(key(a), key(b)),
      (a, b) => r.merge(key(a), key(b)),
      ck => r.delete(key(ck), Instant.parse("2026-10-31T10:00:00Z"))
    )

  private val testDb = TestDb.url
  private lazy val scratch = ScratchDatabases("referable", AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def onDatabase(name: String): (World, Transactor) =
    val db = Transactor(scratch(name))
    val world = World(
      SourceReferable(db),
      ck => db.transact(sql"INSERT INTO sources (citekey, title) VALUES ($ck, ${s"Title of $ck"})".update.run(): Unit),
      (a, b) =>
        db.transact {
          sql"UPDATE sources SET citekey = $b WHERE citekey = $a".update.run(): Unit
          sql"INSERT INTO source_aliases (alias, citekey) VALUES ($a, $b)".update.run(): Unit
        },
      (a, b) =>
        db.transact {
          sql"""INSERT INTO uid_aliases (alias_uid, uid, kind)
                SELECT f.uid, i.uid, 'source' FROM sources f, sources i WHERE f.citekey = $a AND i.citekey = $b""".update
            .run(): Unit
          sql"DELETE FROM sources WHERE citekey = $a".update.run(): Unit
        },
      ck => db.transact(sql"DELETE FROM sources WHERE citekey = $ck".update.run(): Unit)
    )
    (world, db)

  private def both(name: String)(body: World => Unit): Unit =
    test(s"$name (in memory)")(body(inMemory))
    test(s"$name (database)") {
      assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL is not set")
      body(onDatabase(name.toLowerCase.replaceAll("[^a-z]+", "_").take(30))._1)
    }

  private def ref(w: World, ck: String): ItemRef =
    w.referable.resolve(source, key(ck)).fold(e => fail(e.sentence), identity)

  both("a reference follows its record through a rename, a merge and a deletion") { w =>
    List("zz_a", "zz_b", "zz_c", "zz_d").foreach(w.add)
    val (a, b, c, d) = (ref(w, "zz_a"), ref(w, "zz_b"), ref(w, "zz_c"), ref(w, "zz_d"))
    assert(a.uid.isDefined)
    w.rename("zz_a", "zz_a2")
    w.merge("zz_b", "zz_c")
    w.delete("zz_d")
    val unknown = ItemRef(source, key("zz_never"), None)
    val got = w.referable.describe(Set(a, b, c, d, unknown))
    assertEquals(got(a).key, key("zz_a2"))
    assertEquals(got(a).state, RecordState.Renamed(key("zz_a2")))
    assertEquals(got(a).title.map(_.value.toString), Some("Title of zz_a"))
    assertEquals(got(b).key, key("zz_c"))
    assertEquals(got(b).state, RecordState.MergedInto(c))
    assertEquals(got(c).state, RecordState.Live)
    assertEquals(got(d).key, key("zz_d"))
    assertEquals(got(d).title, None)
    assert(got(d).state.isInstanceOf[RecordState.Deleted])
    assertEquals(got.get(unknown), None)
  }

  both("a key resolves to its record's uid, an old key through its alias; an unknown key does not resolve") { w =>
    w.add("zz_k")
    val before = ref(w, "zz_k")
    w.rename("zz_k", "zz_k2")
    assertEquals(ref(w, "zz_k").uid, before.uid)
    assertEquals(ref(w, "zz_k2").uid, before.uid)
    assert(w.referable.resolve(source, key("zz_nope")).isLeft)
    val keyed = ItemRef(source, key("zz_k"), None)
    assertEquals(w.referable.describe(Set(keyed)).get(keyed).map(_.state), Some(RecordState.Renamed(key("zz_k2"))))
  }

  test("every owner's tables answer: resolve and describe of records none of them holds") {
    assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL is not set")
    val db = onDatabase("every_owner")._2
    val all = Referables(
      List(SourceReferable(db), ArticleReferable(db), CatalogueReferable(db), PictureReferable(db), GoalReferable(db))
    )
    assertEquals(all.kinds.map(_.value.toString), Set("source", "article", "event", "place", "picture", "goal"))
    val refs = all.kinds.flatMap(k =>
      Set(ItemRef(k, key("zz_none"), None), ItemRef(k, key("zz_none"), Some(ItemUid(UUID.randomUUID()))))
    )
    assertEquals(all.describe(refs), Map.empty)
    all.kinds.foreach(k => assert(all.resolve(k, key("zz_none")).isLeft, k.value))
    assert(all.resolve(ItemKind("nothing"), key("x")).isLeft)
  }
