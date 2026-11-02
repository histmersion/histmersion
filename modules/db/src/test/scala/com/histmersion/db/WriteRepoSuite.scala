package com.histmersion.db

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.{GeoPoint, ListPatch, Write}
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Files
import com.histmersion.domain.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Edge, Finding, FindingsPatch, Source}

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
    status = com.histmersion.domain.library.Source.Status.Current,
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
    SourceFixtures.put(ds, seedSource(citekey), 4000)

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put → get round-trip, including notes and the updatedAt token"):
    val token = fresh()
    val (d, fs) = SourceFixtures.get(ds, ck).get
    assertEquals(d.title, "Writes")
    assertEquals(d.notes, Some("# article\n\nagent-written prose"))
    assertEquals(d.updatedAt, Some(token))
    assert(token.endsWith("Z"), token)
    assertEquals(fs.map(_.claim).toList, List("claim one"))

  test("put is idempotent and replaces everything under the citekey"):
    val _ = fresh()
    val s2 = seedSource().copy(tags = List("only"), findings = Nil, summary = None)
    val _ = SourceFixtures.put(ds, s2, 4000)
    val (d, fs) = SourceFixtures.get(ds, ck).get
    assertEquals(d.tags, List("only"))
    assertEquals(fs.size, 0)
    assertEquals(d.summary, None)

  test("load reads the whole domain Source back (findings + edges + notes)"):
    val _ = fresh()
    val s = SourceFixtures.load(ds, ck).get
    assertEquals(s.tags, List("adna", "yamnaya"))
    assertEquals(s.findings.map(_.id), List(s"$ck#0"))
    assertEquals(s.findings.head.geo, Some(GeoPoint(32.0, 39.0)))
    assertEquals(s.edges, List(Edge(ck, "zz_other", "references")))
    assertEquals(s.period, Some(Period(-2000, -1500)))
    assert(s.updatedAt.isDefined)

  // ── patch: the §1.9a acceptance lines ────────────────────────────────────────────────────────
  test("patch {tags:{add:[sea-peoples]}} leaves findings and body untouched"):
    val _ = fresh()
    val before = SourceFixtures.load(ds, ck).get
    val r = SourceFixtures.patch(ds, ck, Source.Patch(tags = Some(ListPatch(add = List("sea-peoples")))), 4000)
    assert(r.isRight, r.toString)
    val after = SourceFixtures.load(ds, ck).get
    assertEquals(after.tags, List("adna", "yamnaya", "sea-peoples"))
    assertEquals(after.findings, before.findings)
    assertEquals(after.ocrMarkdown, before.ocrMarkdown)
    assertEquals(after.notes, before.notes)
    assertEquals(after.summary, before.summary)

  test("patch {findings:{add}} twice adds two findings with distinct ids"):
    val _ = fresh()
    val add = (claim: String) =>
      Source.Patch(findings =
        Some(FindingsPatch(add = List(Finding.Input(claim = claim, lon = Some(1.0), lat = Some(2.0)))))
      )
    assert(SourceFixtures.patch(ds, ck, add("second"), 4000).isRight)
    assert(SourceFixtures.patch(ds, ck, add("third"), 4000).isRight)
    val fs = SourceFixtures.load(ds, ck).get.findings
    assertEquals(fs.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2"))
    assertEquals(fs.map(_.id).distinct.size, 3)
    assertEquals(fs.map(_.claim), List("claim one", "second", "third"))

  test("patch findings remove drops exactly one, by id"):
    val _ = fresh()
    assert(
      SourceFixtures
        .patch(
          ds,
          ck,
          Source.Patch(findings = Some(FindingsPatch(add = List(Finding.Input("second"), Finding.Input("third"))))),
          4000
        )
        .isRight
    )
    assert(
      SourceFixtures.patch(ds, ck, Source.Patch(findings = Some(FindingsPatch(remove = List(s"$ck#1")))), 4000).isRight
    )
    assertEquals(SourceFixtures.load(ds, ck).get.findings.map(_.claim), List("claim one", "third"))

  test("patch bumps updatedAt; an unrelated source is untouched"):
    val t1 = fresh()
    val _ = fresh("zz_write_other")
    val t2 = SourceFixtures.patch(ds, ck, Source.Patch(summary = Some("new")), 4000).toOption.get
    assertNotEquals(t1, t2)
    assertEquals(SourceFixtures.load(ds, "zz_write_other").get.summary, Some("sum"))

  test("patch of a missing citekey is not_found, not a crash"):
    val e = SourceFixtures.patch(ds, "zz_no_such_source", Source.Patch(summary = Some("x")), 4000).swap.toOption.get
    assertEquals(e.code, Some(Write.CodeNotFound))

  test("ifUpdatedAt: current token succeeds, stale token is refused and changes nothing"):
    val t1 = fresh()
    assert(SourceFixtures.patch(ds, ck, Source.Patch(summary = Some("one"), ifUpdatedAt = Some(t1)), 4000).isRight)
    val e =
      SourceFixtures.patch(ds, ck, Source.Patch(summary = Some("two"), ifUpdatedAt = Some(t1)), 4000).swap.toOption.get
    assertEquals(e.code, Some(Write.CodePrecondition))
    assertEquals(SourceFixtures.load(ds, ck).get.summary, Some("one"))

  test("an invalid patch is rejected as invalid and writes nothing"):
    val _ = fresh()
    val e = SourceFixtures.patch(ds, ck, Source.Patch(period = Some(Period.Input(-100, -900))), 4000).swap.toOption.get
    assertEquals(e.code, Some(Write.CodeInvalid))
    assertEquals(SourceFixtures.load(ds, ck).get.period, Some(Period(-2000, -1500)))

  // ── export ───────────────────────────────────────────────────────────────────────────────────
  test("export → parseNote round trip: frontmatter and notes survive the database"):
    val _ = fresh()
    val stored = SourceFixtures.load(ds, ck).get
    val exported = SourceNote.note(stored)
    val back = SourceNote.parseNote(exported, "fallback").get
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

  test("citekeys lists what was written, sorted"):
    val _ = fresh()
    val ks = SourceFixtures.citekeys(ds)
    assert(ks.contains(ck))
    assertEquals(ks.toList, ks.toList.sorted)
