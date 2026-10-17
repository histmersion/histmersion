package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*

/** The vocabulary codecs (#1798 item 11): a stored value the vocabulary does not know reads as its
  * UNKNOWN — a confidence as "not stated", a status as unassessed, a place's dating as unknown —
  * never as a confident member (an unknown confidence used to read as `high`). */
class VocabCodecSuite extends munit.FunSuite:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private val ck = "zzvocab_src"

  test("an unknown confidence in the store reads as not stated, never as high"):
    SourceRepo.put(ds, Source(citekey = ck, title = "Vocab", authors = Nil, doi = None, year = Some(2020), venue = None,
      status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil, period = None, summary = None,
      citedByCount = None, influentialCitations = None, provenance = None, calibreId = None, ocrMarkdown = None,
      findings = List(
        Finding(s"$ck#0", ck, "stated low", None, None, None, None, Nil, Some(Confidence.Low)),
        Finding(s"$ck#1", ck, "garbled", None, None, None, None, Nil, Some(Confidence.Medium)),
        Finding(s"$ck#2", ck, "not stated", None, None, None, None, Nil, None))),
      cfg.limits.chunkChars, cfg.places.pointToleranceKm)
    connect(ds):
      sql"UPDATE findings SET confidence = 'Certain' WHERE id = ${s"$ck#1"}".update.run()
      sql"UPDATE sources SET status = 'bogus' WHERE citekey = $ck".update.run()
    val (s, fs) = SourceRepo.get(ds, ck).get
    assertEquals(fs.map(f => f.claim -> f.confidence).toMap, Map("stated low" -> Some(Confidence.Low), "garbled" -> None, "not stated" -> None))
    assertEquals(s.status, SourceStatus.Unassessed)
    connect(ds)(sql"DELETE FROM sources WHERE citekey = $ck".update.run())
