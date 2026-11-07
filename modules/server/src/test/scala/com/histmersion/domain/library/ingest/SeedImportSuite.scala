package com.histmersion.domain.library.ingest

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.RowCount
import com.histmersion.api.ListPatch
import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.{CitekeyMillis, SourceFixtures, SourceService, SourceSettings}
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.db.TestDb
import com.histmersion.util.database.Migrate
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.server.SeedNoteFiles
import com.histmersion.util.database.Transactor

import java.nio.file.Files
import com.histmersion.api.library.Source

/** The seed over a folder of notes, against a real Postgres (HISTMERSION_TEST_DB_URL; skipped otherwise): a note older
  * than the stored source is skipped and `force` overwrites it, and an exported note imports back to the same note.
  */
class SeedImportSuite extends com.histmersion.db.DbSuite:
  private given com.histmersion.util.storage.TextFiles = new com.histmersion.util.storage.DiskTextFiles
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d); d
  private lazy val sources = SourceService(Transactor(ds), SourceReferrers(Nil), () => SourceSettings(SourceSettings.ChunkChars(4000), SourceSettings.ToleranceKm(25.0)))
  private lazy val seed = SeedImport(sources, (_, figures) => RowCount(figures.size))

  private def run(dir: java.nio.file.Path, force: SeedImport.RunForce = SeedImport.RunForce(false)) =
    seed.run(SeedNoteFiles(dir, None), force, SeedFolder.MaxBytes(1 << 20))

  test("seed skips a file older than the stored row, and force overwrites it"):
    val dir = Files.createTempDirectory("histmersion-seed")
    val ckS = com.histmersion.domain.library.Citekey("zz_seed_guard")
    val _ =
      Files.writeString(dir.resolve(s"$ckS.md"), s"---\ncitekey: $ckS\ntitle: From file\ntags: [fromfile]\n---\nbody\n")

    // 1st seed: nothing stored yet → imported
    val first = run(dir)
    assertEquals(first.imported, 1)
    assertEquals(first.skipped, Nil)

    // an agent enriches the row → updated_at now newer than the file's mtime
    assert(sources.patch(ckS, Source.Patch(tags = Some(ListPatch(add = List(ListPatch.Add("enriched"))))), Caller.Local).isRight)

    // 2nd seed: the file is older than the row → skipped, enrichment preserved
    val second = run(dir)
    assertEquals(second.imported, 0)
    assertEquals(second.skipped, List(ckS))
    assert(sources.load(ckS).get.tags.exists(_.value == "enriched"))

    // --force: the file wins and the enrichment is gone (the deliberate re-import)
    val forced = run(dir, force = SeedImport.RunForce(true))
    assertEquals(forced.imported, 1)
    assertEquals(forced.skipped, Nil)
    assertEquals(sources.load(ckS).get.tags, List("fromfile"))

  test("a note without frontmatter fails alone, by its file name"):
    val dir = Files.createTempDirectory("histmersion-seed-bad")
    val _ = Files.writeString(dir.resolve("zz_seed_bad.md"), "no frontmatter here\n")
    val _ = Files.writeString(dir.resolve("zz_seed_ok.md"), "---\ncitekey: zz_seed_ok\ntitle: Fine\n---\n")
    val r = run(dir, force = SeedImport.RunForce(true))
    assertEquals(r.imported, 1)
    assertEquals(r.failed, List("zz_seed_bad.md: no frontmatter"))

  test("isStale: only a STRICTLY newer row blocks the file"):
    assert(SeedImport.isStale(Map(com.histmersion.domain.library.Citekey.assume("a") -> CitekeyMillis.Millis(100L)), com.histmersion.domain.library.Citekey.assume("a"), com.histmersion.domain.library.ingest.SeedNote.ModifiedMillis(99L)))
    assert(!SeedImport.isStale(Map(com.histmersion.domain.library.Citekey.assume("a") -> CitekeyMillis.Millis(100L)), com.histmersion.domain.library.Citekey.assume("a"), com.histmersion.domain.library.ingest.SeedNote.ModifiedMillis(100L)))
    assert(!SeedImport.isStale(Map(com.histmersion.domain.library.Citekey.assume("a") -> CitekeyMillis.Millis(100L)), com.histmersion.domain.library.Citekey.assume("a"), com.histmersion.domain.library.ingest.SeedNote.ModifiedMillis(101L)))
    assert(!SeedImport.isStale(Map.empty, com.histmersion.domain.library.Citekey.assume("a"), com.histmersion.domain.library.ingest.SeedNote.ModifiedMillis(1L))) // unknown citekey: always import

  test("export then re-import (force) leaves identical frontmatter — the §1.9a round trip"):
    val ck = com.histmersion.domain.library.Citekey("zz_seed_roundtrip")
    val dir0 = Files.createTempDirectory("histmersion-seed-first")
    val _ = Files.writeString(
      dir0.resolve(s"$ck.md"),
      s"---\ncitekey: $ck\ntitle: Round trip\nauthors: [A, B]\nyear: 2020\ntopics: [t]\ntags: [x]\n---\nThe notes.\n"
    )
    val _ = run(dir0, force = SeedImport.RunForce(true))
    val one = SourceNote.note(SourceFixtures.load(ds, ck).get)
    val dir = Files.createTempDirectory("histmersion-export")
    val _ = Files.writeString(dir.resolve(s"$ck.md"), one.value)
    assertEquals(run(dir, force = SeedImport.RunForce(true)).imported, 1)
    val two = SourceNote.note(SourceFixtures.load(ds, ck).get)
    assertEquals(two, one)
