package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.{Api, GeoPoint, ListPatch, Write}
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Files
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Edge, Finding, FindingsPatch, Source}
import com.histmersion.util.database.given
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude

/** The write paths against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. Covers the acceptance
  * lines of plan §1.9a that need a database: put→get, patch semantics, id allocation, the `ifUpdatedAt` precondition,
  * the seed guard, and the export round trip.
  */
class WriteRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val ck = Citekey("zz_write_source")
  private val chunkChars = Limits.ChunkChars(4000)

  private def seedSource(citekey: Citekey = ck) = Source(
    citekey = api.library.Citekey(citekey.value),
    title = api.library.Title("Writes"),
    authors = (List("A", "B")).map(api.library.AuthorName.apply),
    doi = (Some("10.0/x")).map(Doi.apply),
    year = (Some(2020)).map(Year.apply),
    venue = (Some("Nature")).map(Source.Venue.apply),
    status = Source.Status.Current,
    topics = List(Source.Topics("t")),
    regions = List(Source.Regions("Anatolia")),
    tags = List(Source.Tags("adna"), Source.Tags("yamnaya")),
    period = Some(Period(Year(-2000), Year(-1500))),
    summary = Some(Source.Summary("sum")),
    citedByCount = Some(Source.CitedByCount(1)),
    influentialCitations = None,
    provenance = Some(Source.Provenance("unpaywall")),
    calibreId = None,
    ocrMarkdown = Some(Source.OcrMarkdown("body text")),
    findings = List(
      Finding(
        Finding.Id(s"$citekey#0"),
        api.library.Citekey(citekey.value),
        Finding.Claim("claim one"),
        Some(Finding.Region("Anatolia")),
        Some(GeoPoint(Longitude(32.0), Latitude(39.0))),
        Some(Period(Year(-1800), Year(-1700))),
        Some(Finding.PeriodName("MBA")),
        List(Finding.Tags("f")),
        Some(Confidence.High)
      )
    ),
    edges = List(
      Edge(
        api.library.Citekey(citekey.value),
        api.library.Citekey("zz_other"),
        Edge.Kind.References
      )
    ),
    notes = Some(Source.Notes("# article\n\nagent-written prose"))
  )

  private def fresh(citekey: Citekey = ck): Source.UpdatedAt =
    SourceFixtures.put(ds, seedSource(citekey), chunkChars)

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put → get round-trip, including notes and the updatedAt token"):
    val token = fresh()
    val (d, fs) = SourceFixtures.get(ds, ck).get
    assertEquals(d.title, "Writes")
    assertEquals(d.notes, Some("# article\n\nagent-written prose"))
    assertEquals(d.updatedAt, Some(token))
    assert(token.value.endsWith("Z"), token)
    assertEquals(fs.map(_.claim).toList, List("claim one"))

  test("put is idempotent and replaces everything under the citekey"):
    val _ = fresh()
    val s2 = seedSource()
      .modify(_.tags)
      .setTo(List(Source.Tags("only")))
      .modify(_.findings)
      .setTo(Nil)
      .modify(_.summary)
      .setTo(None)
    val _ = SourceFixtures.put(ds, s2, chunkChars)
    val (d, fs) = SourceFixtures.get(ds, ck).get
    assertEquals(d.tags, List("only"))
    assertEquals(fs.size, 0)
    assertEquals(d.summary, None)

  test("load reads the whole domain Source back (findings + edges + notes)"):
    val _ = fresh()
    val s = SourceFixtures.load(ds, ck).get
    assertEquals(s.tags, List("adna", "yamnaya"))
    assertEquals(s.findings.map(_.id), List(s"$ck#0"))
    assertEquals(s.findings.head.geo, Some(GeoPoint(Longitude(32.0), Latitude(39.0))))
    assertEquals(
      s.edges,
      List(
        Edge(
          api.library.Citekey(ck.value),
          api.library.Citekey("zz_other"),
          Edge.Kind.References
        )
      )
    )
    assertEquals(s.period, Some(Period(Year(-2000), Year(-1500))))
    assert(s.updatedAt.isDefined)

  // ── patch: the §1.9a acceptance lines ────────────────────────────────────────────────────────
  test("patch {tags:{add:[sea-peoples]}} leaves findings and body untouched"):
    val _ = fresh()
    val before = SourceFixtures.load(ds, ck).get
    val r = SourceFixtures.patch(
      ds,
      ck,
      Source.Patch(tags = Some(ListPatch(add = List(Write.ListItem("sea-peoples"))))),
      chunkChars
    )
    assert(r.isRight, r.toString)
    val after = SourceFixtures.load(ds, ck).get
    assertEquals(after.tags, List("adna", "yamnaya", "sea-peoples"))
    assertEquals(after.findings, before.findings)
    assertEquals(after.ocrMarkdown, before.ocrMarkdown)
    assertEquals(after.notes, before.notes)
    assertEquals(after.summary, before.summary)

  test("patch {findings:{add}} twice adds two findings with distinct ids"):
    val _ = fresh()
    val add = (claim: Finding.Claim) =>
      Source.Patch(findings =
        Some(
          FindingsPatch(
            add = List(
              Finding.Input(
                claim = claim,
                lon = Some(Longitude(1.0)),
                lat = Some(Latitude(2.0))
              )
            )
          )
        )
      )
    assert(SourceFixtures.patch(ds, ck, add(Finding.Claim("second")), chunkChars).isRight)
    assert(SourceFixtures.patch(ds, ck, add(Finding.Claim("third")), chunkChars).isRight)
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
          Source.Patch(findings =
            Some(
              FindingsPatch(add = List(Finding.Input(Finding.Claim("second")), Finding.Input(Finding.Claim("third"))))
            )
          ),
          chunkChars
        )
        .isRight
    )
    assert(
      SourceFixtures
        .patch(
          ds,
          ck,
          Source.Patch(findings = Some(FindingsPatch(remove = List(FindingsPatch.Remove(s"$ck#1"))))),
          chunkChars
        )
        .isRight
    )
    assertEquals(SourceFixtures.load(ds, ck).get.findings.map(_.claim), List("claim one", "third"))

  test("patch bumps updatedAt; an unrelated source is untouched"):
    val t1 = fresh()
    val _ = fresh(Citekey("zz_write_other"))
    val t2 = SourceFixtures.patch(ds, ck, Source.Patch(summary = Some(Source.Summary("new"))), chunkChars).toOption.get
    assertNotEquals(t1, t2)
    assertEquals(SourceFixtures.load(ds, Citekey("zz_write_other")).get.summary, Some("sum"))

  test("patch of a missing citekey is not_found, not a crash"):
    val e = SourceFixtures
      .patch(ds, Citekey("zz_no_such_source"), Source.Patch(summary = Some(Source.Summary("x"))), chunkChars)
      .swap
      .toOption
      .get
    assertEquals(e.code, Some(Write.CodeNotFound))

  test("ifUpdatedAt: current token succeeds, stale token is refused and changes nothing"):
    val t1 = fresh()
    assert(
      SourceFixtures
        .patch(ds, ck, Source.Patch(summary = Some(Source.Summary("one")), ifUpdatedAt = Some(t1)), chunkChars)
        .isRight
    )
    val e =
      SourceFixtures
        .patch(ds, ck, Source.Patch(summary = Some(Source.Summary("two")), ifUpdatedAt = Some(t1)), chunkChars)
        .swap
        .toOption
        .get
    assertEquals(e.code, Some(Write.CodePrecondition))
    assertEquals(SourceFixtures.load(ds, ck).get.summary, Some("one"))

  test("an invalid patch is rejected as invalid and writes nothing"):
    val _ = fresh()
    val e = SourceFixtures
      .patch(ds, ck, Source.Patch(period = Some(Period.Input(Year(-100), Year(-900)))), chunkChars)
      .swap
      .toOption
      .get
    assertEquals(e.code, Some(Api.Error.Field.Period.code))
    assertEquals(SourceFixtures.load(ds, ck).get.period, Some(Period(Year(-2000), Year(-1500))))

  // ── export ───────────────────────────────────────────────────────────────────────────────────
  test("export → parseNote round trip: frontmatter and notes survive the database"):
    val _ = fresh()
    val stored = SourceFixtures.load(ds, ck).get
    val exported = SourceNote.note(stored)
    val back = SourceNote.parseNote(exported, api.library.Citekey("fallback")).get
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
