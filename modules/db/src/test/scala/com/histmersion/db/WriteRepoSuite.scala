package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Files
import com.histmersion.values.{Confidence, SourceStatus}

/** The write paths against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. Covers the acceptance
  * lines of plan §1.9a that need a database: put→get, patch semantics, id allocation, the `ifUpdatedAt` precondition,
  * the seed guard, and the export round trip.
  */
class WriteRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val ck = "zz_write_source"

  private def seedSource(citekey: String = ck) = Source(
    citekey = citekey,
    title = "Writes",
    authors = List("A", "B"),
    doi = Some("10.0/x"),
    year = Some(2020),
    venue = Some("Nature"),
    status = SourceStatus.Current,
    topics = List("t"),
    regions = List("Anatolia"),
    tags = List("adna", "yamnaya"),
    period = Some(Period(-2000, -1500)),
    summary = Some("sum"),
    citedByCount = Some(1),
    influentialCitations = None,
    provenance = Some("unpaywall"),
    calibreId = None,
    ocrMarkdown = Some("body text"),
    findings = List(
      Finding(
        s"$citekey#0",
        citekey,
        "claim one",
        Some("Anatolia"),
        Some(GeoPoint(32.0, 39.0)),
        Some(Period(-1800, -1700)),
        Some("MBA"),
        List("f"),
        Some(Confidence.High)
      )
    ),
    edges = List(Edge(citekey, "zz_other", "references")),
    notes = Some("# article\n\nagent-written prose")
  )

  private def fresh(citekey: String = ck): String =
    SourceRepo.put(ds, seedSource(citekey), 4000)

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put → get round-trip, including notes and the updatedAt token"):
    val token = fresh()
    val (d, fs) = SourceRepo.get(ds, ck).get
    assertEquals(d.title, "Writes")
    assertEquals(d.notes, Some("# article\n\nagent-written prose"))
    assertEquals(d.updatedAt, Some(token))
    assert(token.endsWith("Z"), token)
    assertEquals(fs.map(_.claim).toList, List("claim one"))

  test("put is idempotent and replaces everything under the citekey"):
    val _ = fresh()
    val s2 = seedSource().copy(tags = List("only"), findings = Nil, summary = None)
    val _ = SourceRepo.put(ds, s2, 4000)
    val (d, fs) = SourceRepo.get(ds, ck).get
    assertEquals(d.tags, List("only"))
    assertEquals(fs.size, 0)
    assertEquals(d.summary, None)

  test("load reads the whole domain Source back (findings + edges + notes)"):
    val _ = fresh()
    val s = SourceRepo.load(ds, ck).get
    assertEquals(s.tags, List("adna", "yamnaya"))
    assertEquals(s.findings.map(_.id), List(s"$ck#0"))
    assertEquals(s.findings.head.geo, Some(GeoPoint(32.0, 39.0)))
    assertEquals(s.edges, List(Edge(ck, "zz_other", "references")))
    assertEquals(s.period, Some(Period(-2000, -1500)))
    assert(s.updatedAt.isDefined)

  // ── patch: the §1.9a acceptance lines ────────────────────────────────────────────────────────
  test("patch {tags:{add:[sea-peoples]}} leaves findings and body untouched"):
    val _ = fresh()
    val before = SourceRepo.load(ds, ck).get
    val r = SourceRepo.patch(ds, ck, SourcePatch(tags = Some(ListPatch(add = List("sea-peoples")))), 4000)
    assert(r.isRight, r.toString)
    val after = SourceRepo.load(ds, ck).get
    assertEquals(after.tags, List("adna", "yamnaya", "sea-peoples"))
    assertEquals(after.findings, before.findings)
    assertEquals(after.ocrMarkdown, before.ocrMarkdown)
    assertEquals(after.notes, before.notes)
    assertEquals(after.summary, before.summary)

  test("patch {findings:{add}} twice adds two findings with distinct ids"):
    val _ = fresh()
    val add = (claim: String) =>
      SourcePatch(findings =
        Some(FindingsPatch(add = List(FindingInput(claim = claim, lon = Some(1.0), lat = Some(2.0)))))
      )
    assert(SourceRepo.patch(ds, ck, add("second"), 4000).isRight)
    assert(SourceRepo.patch(ds, ck, add("third"), 4000).isRight)
    val fs = SourceRepo.load(ds, ck).get.findings
    assertEquals(fs.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2"))
    assertEquals(fs.map(_.id).distinct.size, 3)
    assertEquals(fs.map(_.claim), List("claim one", "second", "third"))

  test("patch findings remove drops exactly one, by id"):
    val _ = fresh()
    assert(
      SourceRepo
        .patch(
          ds,
          ck,
          SourcePatch(findings = Some(FindingsPatch(add = List(FindingInput("second"), FindingInput("third"))))),
          4000
        )
        .isRight
    )
    assert(SourceRepo.patch(ds, ck, SourcePatch(findings = Some(FindingsPatch(remove = List(s"$ck#1")))), 4000).isRight)
    assertEquals(SourceRepo.load(ds, ck).get.findings.map(_.claim), List("claim one", "third"))

  test("patch bumps updatedAt; an unrelated source is untouched"):
    val t1 = fresh()
    val _ = fresh("zz_write_other")
    val t2 = SourceRepo.patch(ds, ck, SourcePatch(summary = Some("new")), 4000).toOption.get
    assertNotEquals(t1, t2)
    assertEquals(SourceRepo.load(ds, "zz_write_other").get.summary, Some("sum"))

  test("patch of a missing citekey is not_found, not a crash"):
    val e = SourceRepo.patch(ds, "zz_no_such_source", SourcePatch(summary = Some("x")), 4000).swap.toOption.get
    assertEquals(e.code, Some(Write.CodeNotFound))

  test("ifUpdatedAt: current token succeeds, stale token is refused and changes nothing"):
    val t1 = fresh()
    assert(SourceRepo.patch(ds, ck, SourcePatch(summary = Some("one"), ifUpdatedAt = Some(t1)), 4000).isRight)
    val e = SourceRepo.patch(ds, ck, SourcePatch(summary = Some("two"), ifUpdatedAt = Some(t1)), 4000).swap.toOption.get
    assertEquals(e.code, Some(Write.CodePrecondition))
    assertEquals(SourceRepo.load(ds, ck).get.summary, Some("one"))

  test("an invalid patch is rejected as invalid and writes nothing"):
    val _ = fresh()
    val e = SourceRepo.patch(ds, ck, SourcePatch(period = Some(PeriodInput(-100, -900))), 4000).swap.toOption.get
    assertEquals(e.code, Some(Write.CodeInvalid))
    assertEquals(SourceRepo.load(ds, ck).get.period, Some(Period(-2000, -1500)))

  // ── seed guard ───────────────────────────────────────────────────────────────────────────────
  test("seed skips a file older than the stored row, and force overwrites it"):
    val dir = Files.createTempDirectory("histmersion-seed")
    val ckS = "zz_seed_guard"
    val file = dir.resolve(s"$ckS.md")
    val _ = Files.writeString(file, s"---\ncitekey: $ckS\ntitle: From file\ntags: [fromfile]\n---\nbody\n")

    // 1st seed: nothing stored yet → imported
    val first = Importer.importDir(ds, dir, 4000)
    assertEquals(first.imported, 1)
    assertEquals(first.skipped, Nil)

    // an agent enriches the row → updated_at now newer than the file's mtime
    assert(SourceRepo.patch(ds, ckS, SourcePatch(tags = Some(ListPatch(add = List("enriched")))), 4000).isRight)

    // 2nd seed: the file is older than the row → skipped, enrichment preserved
    val second = Importer.importDir(ds, dir, 4000)
    assertEquals(second.imported, 0)
    assertEquals(second.skipped, List(ckS))
    assert(SourceRepo.load(ds, ckS).get.tags.contains("enriched"))

    // --force: the file wins and the enrichment is gone (the deliberate re-import)
    val forced = Importer.importDir(ds, dir, 4000, force = true)
    assertEquals(forced.imported, 1)
    assertEquals(forced.skipped, Nil)
    assertEquals(SourceRepo.load(ds, ckS).get.tags, List("fromfile"))

  test("isStale: only a STRICTLY newer row blocks the file"):
    assert(Importer.isStale(Map("a" -> 100L), "a", 99L))
    assert(!Importer.isStale(Map("a" -> 100L), "a", 100L))
    assert(!Importer.isStale(Map("a" -> 100L), "a", 101L))
    assert(!Importer.isStale(Map.empty, "a", 1L)) // unknown citekey: always import

  // ── export ───────────────────────────────────────────────────────────────────────────────────
  test("export → parseNote round trip: frontmatter and notes survive the database"):
    val _ = fresh()
    val stored = SourceRepo.load(ds, ck).get
    val exported = Export.note(stored)
    val back = Frontmatter.parseNote(exported, "fallback").get
    assertEquals(back.citekey, stored.citekey)
    assertEquals(back.title, stored.title)
    assertEquals(back.authors, stored.authors)
    assertEquals(back.tags, stored.tags)
    assertEquals(back.topics, stored.topics)
    assertEquals(back.regions, stored.regions)
    assertEquals(back.period, stored.period)
    assertEquals(back.summary, stored.summary)
    assertEquals(back.findings, stored.findings)
    assertEquals(back.notes, stored.notes)

  test("export then re-import (force) leaves identical frontmatter — the §1.9a round trip"):
    val _ = fresh()
    val one = Export.note(SourceRepo.load(ds, ck).get)
    val dir = Files.createTempDirectory("histmersion-export")
    val _ = Files.writeString(dir.resolve(s"$ck.md"), one)
    assertEquals(Importer.importDir(ds, dir, 4000, force = true).imported, 1)
    val two = Export.note(SourceRepo.load(ds, ck).get)
    assertEquals(two, one)

  test("citekeys lists what was written, sorted"):
    val _ = fresh()
    val ks = SourceRepo.citekeys(ds)
    assert(ks.contains(ck))
    assertEquals(ks.toList, ks.toList.sorted)
