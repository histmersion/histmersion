package com.histmersion.util.database

import com.augustnagro.magnum.*
import com.histmersion.config.AppConfig
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.research.Slug
import com.histmersion.Year

import java.util.UUID
import com.histmersion.domain.library.Source
import com.histmersion.util.jobs.Job

class NewtypeColumnsSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds = testPool(AppConfig.load().db.copy(url = testDb.get))

  test("a newtype column reads and writes as its base type"):
    val uid = UUID.fromString("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b")
    val row = connect(ds)(
      sql"SELECT ${Slug("sea_peoples")}::text, ${Year(-3300)}::int, ${Source.Uid(uid)}::uuid, NULL::text"
        .query[(Slug, Year, Source.Uid, Option[Job.Id])]
        .run()
    )
    assertEquals(row, Vector((Slug("sea_peoples"), Year(-3300), Source.Uid(uid), None)))

  test("a stored value the constraint refuses fails the read, naming the column and the value"):
    val e = intercept[Exception](connect(ds)(sql"SELECT 'Sea Peoples'::text AS slug".query[Slug].run()))
    val messages = Iterator.iterate[Throwable](e)(_.getCause).takeWhile(_ != null).map(_.getMessage).mkString(" | ")
    assert(messages.contains("column slug: 'Sea Peoples' is not valid"), messages)
