package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.{Jsonb, Migrate}
import com.histmersion.api.library.{Finding, Source}
import com.histmersion.util.database.given

/** The vocabulary codecs (#1798 item 11): a stored value the vocabulary does not know reads as its UNKNOWN — a
  * confidence as "not stated", a status as unassessed, a place's dating as unknown — never as a confident member (an
  * unknown confidence used to read as `high`).
  */
class VocabCodecSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
    Migrate.run(d); d

  private val ck = "zzvocab_src"

  test("an unknown confidence in the store reads as not stated, never as high"):
    SourceFixtures.put(
      ds,
      Source(
        citekey = com.histmersion.api.library.Citekey(ck),
        title = com.histmersion.api.library.Title("Vocab"),
        authors = (Nil).map(com.histmersion.api.library.AuthorName.apply),
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
        provenance = None,
        calibreId = None,
        ocrMarkdown = None,
        findings = List(
          Finding(s"$ck#0", com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Finding.Claim("stated low"), None, None, None, None, Nil, Some(Confidence.Low)),
          Finding(s"$ck#1", com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Finding.Claim("garbled"), None, None, None, None, Nil, Some(Confidence.Medium)),
          Finding(s"$ck#2", com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Finding.Claim("not stated"), None, None, None, None, Nil, None)
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )
    connect(ds):
      sql"UPDATE findings SET confidence = 'Certain' WHERE id = ${s"$ck#1"}".update.run()
      sql"UPDATE sources SET status = 'bogus' WHERE citekey = $ck".update.run()
    val (s, fs) = SourceFixtures.get(ds, ck).get
    assertEquals(
      fs.map(f => f.claim -> f.confidence).toMap,
      Map("stated low" -> Some(Confidence.Low), "garbled" -> None, "not stated" -> None)
    )
    assertEquals(s.status, com.histmersion.api.library.Source.Status.Unassessed)
    connect(ds)(sql"DELETE FROM sources WHERE citekey = $ck".update.run())

/** [[Jsonb]] (#1946): one rule for a jsonb column — what does not decode reads as the fallback or as absent, never as
  * an exception; nulls are dropped only when asked.
  */
class JsonbCodecSuite extends com.histmersion.db.DbSuite:
  import hearth.kindlings.circederivation.KindlingsCodecAsObject

  final case class Doc(a: Int = 1, b: Option[String] = None) derives KindlingsCodecAsObject

  test("write: compact, nulls kept unless dropNulls"):
    assertEquals(Jsonb.write(Doc(2, None)), """{"a":2,"b":null}""")
    assertEquals(Jsonb.write(Doc(2, None), dropNulls = com.histmersion.util.database.Jsonb.DropNulls(true)), """{"a":2}""")

  test("read and decode: a document this build cannot read is absent, or says why"):
    assertEquals(Jsonb.read[Doc]("""{"a":3}"""), Some(Doc(3)))
    assertEquals(Jsonb.read[Doc]("""{"a":"x"}"""), None)
    assertEquals(Jsonb.read[Doc](com.histmersion.util.database.Jsonb.JsonText("not json")), None)
    assert(Jsonb.decode[Doc]("""{"a":"x"}""").isLeft)
    assertEquals(Jsonb.decode[List[Int]](com.histmersion.util.database.Jsonb.JsonText("[1,2]")), Right(List(1, 2)))
