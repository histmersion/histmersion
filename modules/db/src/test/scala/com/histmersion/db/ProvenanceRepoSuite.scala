package com.histmersion.db

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.library.{MetadataPatch, Provenance, Source}
import com.histmersion.util.database.given

/** A provenance is free text: every write path stores it as written. Against a real Postgres (HISTMERSION_TEST_DB_URL);
  * skipped otherwise.
  */
class ProvenanceRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
    Migrate.run(d); d

  private val Prefix = "zzprov"

  private def source(ck: String, provenance: Option[String]) = Source(
    citekey = com.histmersion.api.library.Citekey(ck),
    title = com.histmersion.api.library.Title(s"Title $ck"),
    authors = (List("A")).map(com.histmersion.api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
    venue = None,
    status = com.histmersion.api.library.Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = provenance,
    calibreId = None
  )

  private def stored(ck: String): Option[String] =
    connect(ds)(sql"SELECT source FROM sources WHERE citekey = $ck".query[Option[String]].run().head)

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds)(sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run())

  test("a put and a patch store a provenance as written, a value no code knows included"):
    val a = s"${Prefix}a2020_x"
    SourceFixtures.upsert(ds, source(a, Some("Unheard-Of")), cfg.limits.chunkChars)
    assertEquals(stored(a), Some("Unheard-Of"))
    SourceFixtures.upsert(ds, source(a, Some(Provenance.ExternalFile)), cfg.limits.chunkChars)
    assertEquals(SourceFixtures.load(ds, a).flatMap(_.provenance), Some(Provenance.ExternalFile))
    val patched = SourceFixtures.patch(
      ds,
      a,
      Source.Patch(metadata = Some(MetadataPatch(provenance = Some("another-one")))),
      cfg.limits.chunkChars
    )
    assert(patched.isRight, patched.toString)
    assertEquals(stored(a), Some("another-one"))
