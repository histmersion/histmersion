package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Migrate
import com.histmersion.api.library.{MetadataPatch, Provenance, Source}
import com.histmersion.util.database.given
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

/** A provenance is free text: every write path stores it as written. Against a real Postgres (HISTMERSION_TEST_DB_URL);
  * skipped otherwise.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ProvenanceRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val Prefix = "zzprov"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def source(ck: Citekey, provenance: Option[Source.Provenance]) = Source(
    citekey = api.library.Citekey(ck.value),
    title = api.library.Title(s"Title $ck"),
    authors = (List("A")).map(api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
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

  private def stored(ck: Citekey): Option[String] =
    connect(ds)(sql"SELECT source FROM sources WHERE citekey = $ck".query[Option[String]].run().head)

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds)(sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run())

  test("a put and a patch store a provenance as written, a value no code knows included"):
    val a = Citekey.applyUnsafe(s"${Prefix}a2020_x")
    SourceFixtures.upsert(ds, source(a, Some(Source.Provenance("Unheard-Of"))), cfg.limits.chunkChars)
    assertEquals(stored(a), Some("Unheard-Of"))
    SourceFixtures.upsert(ds, source(a, Some(Source.Provenance(Provenance.ExternalFile))), cfg.limits.chunkChars)
    assertEquals(SourceFixtures.load(ds, a).flatMap(_.provenance), Some(Provenance.ExternalFile))
    val patched = SourceFixtures.patch(
      ds,
      a,
      Source.Patch(metadata = Some(MetadataPatch(provenance = Some(Source.Provenance("another-one"))))),
      cfg.limits.chunkChars
    )
    assert(patched.isRight, patched.toString)
    assertEquals(stored(a), Some("another-one"))
