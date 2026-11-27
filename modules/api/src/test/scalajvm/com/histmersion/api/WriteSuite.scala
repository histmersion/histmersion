package com.histmersion
package api
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.{Problem, Problems}
import sttp.model.StatusCode
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Confidence
import com.histmersion.api.automation.Queue
import com.histmersion.api.catalogue.{EventPatch, Period}
import com.histmersion.api.library.{
  Edge,
  EdgesPatch,
  Filter,
  Finding,
  FindingsPatch,
  MetadataField,
  MetadataPatch,
  Source
}
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.research.Article
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.lint.{tolerated, LintRule}

/** Pure tests for the patch-merge (plan §1.9a). No database: `applyPatch` is a total function of (existing, patch),
  * which is exactly why the merge rules live in `core`.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class WriteSuite extends munit.FunSuite:

  private val ck = "haak2015_massive"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finding(n: Finding.Ordinal, claim: Finding.Claim) =
    Finding(
      Source.Write.findingId(Citekey(ck), n),
      Citekey(ck),
      claim,
      Some(Finding.Region("Anatolia")),
      Some(GeoPoint(Longitude(32.0), Latitude(39.0))),
      Some(Period(Year(-2000), Year(-1500))),
      Some(Finding.PeriodName("EBA")),
      List(Finding.Tags("steppe")),
      Some(Confidence.High)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val existing = Source(
    citekey = Citekey(ck),
    title = Title("Massive migration"),
    authors = (List("Haak", "Reich")).map(AuthorName.apply),
    doi = (Some("10.1038/x")).map(Doi.apply),
    year = (Some(2015)).map(Year.apply),
    venue = (Some("Nature")).map(Source.Venue.apply),
    status = Source.Status.Foundational,
    topics = List(Source.Topics("ancient_dna")),
    regions = List(Source.Regions("Eurasian Steppe")),
    tags = List(Source.Tags("adna"), Source.Tags("yamnaya")),
    period = Some(Period(Year(-8000), Year(-900))),
    summary = Some(Source.Summary("original summary")),
    citedByCount = Some(Source.CitedByCount(2038)),
    influentialCitations = None,
    provenance = Some(Source.Provenance("unpaywall")),
    calibreId = None,
    ocrMarkdown = Some(Source.OcrMarkdown("the whole paper")),
    findings =
      List(finding(Finding.Ordinal(0), Finding.Claim("first")), finding(Finding.Ordinal(1), Finding.Claim("second"))),
    edges = List(Edge(Citekey(ck), Citekey("anthony2007_horse"), Edge.Kind.References)),
    notes = Some(Source.Notes("# article")),
    updatedAt = Some(Source.UpdatedAt("2026-08-25T10:00:00.000000Z"))
  )

  // ── absent parts are untouched ───────────────────────────────────────────────────────────────
  test("a tags-only patch leaves findings, body, notes and everything else untouched"):
    val out =
      Source.Write
        .applyPatch(existing, Source.Patch(tags = Some(ListPatch(add = List(Write.ListItem("sea-peoples"))))))
        .toOption
        .get
    assertEquals(out.tags, List("adna", "yamnaya", "sea-peoples"))
    assertEquals(out.findings, existing.findings)
    assertEquals(out.ocrMarkdown, existing.ocrMarkdown)
    assertEquals(out.notes, existing.notes)
    assertEquals(out.summary, existing.summary)
    assertEquals(out.edges, existing.edges)
    assertEquals(out.modify(_.tags).setTo(existing.tags), existing) // nothing else moved at all

  test("an empty patch is the identity"):
    assertEquals(Source.Write.applyPatch(existing, Source.Patch()), Right(existing))

  // ── tags: add / remove / replace ─────────────────────────────────────────────────────────────
  test("tags: remove drops, add appends, both dedupe and keep order"):
    val p = ListPatch(
      add = List(Write.ListItem("sea-peoples"), Write.ListItem("adna")),
      remove = List(Write.ListItem("yamnaya"))
    )
    val out = Write.applyList(existing.tags.map(t => Write.ListItem(t.value)), p)
    assertEquals(out, List("adna", "sea-peoples"))

  test("tags: replace is the new baseline, then remove and add apply to it"):
    val p = ListPatch(
      add = List(Write.ListItem("c")),
      remove = List(Write.ListItem("b")),
      replace = Some(List(Write.ListItem("a"), Write.ListItem("b")))
    )
    assertEquals(Write.applyList(existing.tags.map(t => Write.ListItem(t.value)), p), List("a", "c"))

  test("tags: removing something that was also added removes it (remove wins)"):
    assertEquals(
      Write.applyList(
        List(Write.ListItem("x")),
        ListPatch(add = List(Write.ListItem("y")), remove = List(Write.ListItem("y")))
      ),
      List("x")
    )

  // ── findings: ids are stable and distinct ────────────────────────────────────────────────────
  test("findings: two successive adds get two distinct ids"):
    val add = FindingsPatch(add = List(Finding.Input(claim = Finding.Claim("third"))))
    val once = Source.Write.applyPatch(existing, Source.Patch(findings = Some(add))).toOption.get
    val twice = Source.Write
      .applyPatch(
        once,
        Source.Patch(findings = Some(FindingsPatch(add = List(Finding.Input(claim = Finding.Claim("fourth"))))))
      )
      .toOption
      .get
    assertEquals(once.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2"))
    assertEquals(twice.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2", s"$ck#3"))
    assertEquals(twice.findings.map(_.claim), List("first", "second", "third", "fourth"))

  test("findings: an add reuses the lowest FREE id after a removal, never a duplicate"):
    val gapped =
      Source.Write
        .applyPatch(
          existing,
          Source.Patch(findings = Some(FindingsPatch(remove = List(FindingsPatch.Remove(s"$ck#0")))))
        )
        .toOption
        .get
    assertEquals(gapped.findings.map(_.id), List(s"$ck#1"))
    val refilled = Source.Write
      .applyPatch(gapped, Source.Patch(findings = Some(FindingsPatch(add = List(Finding.Input(Finding.Claim("new")))))))
      .toOption
      .get
    assertEquals(refilled.findings.map(_.id), List(s"$ck#1", s"$ck#0"))
    assertEquals(refilled.findings.map(_.id).distinct.size, 2)

  test("findings: several adds in ONE patch still get distinct ids"):
    val p = FindingsPatch(add =
      List(Finding.Input(Finding.Claim("a")), Finding.Input(Finding.Claim("b")), Finding.Input(Finding.Claim("c")))
    )
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2", s"$ck#3", s"$ck#4"))

  test("findings: a confidence outside the vocabulary is refused, never dropped or read as high (#1798)"):
    val bad =
      FindingsPatch(add = List(Finding.Input(Finding.Claim("x"), confidence = Some(Confidence.Wire("certain")))))
    val r = Source.Write.applyPatch(existing, Source.Patch(findings = Some(bad)))
    assert(
      r.left.exists(_.message match
        case Problem.Invalid.Unknown(t, raw, known, _) =>
          t == Problem.TypeName.named[Confidence] && raw == Problem.Raw("certain") &&
          known == Vector(Problem.Raw("high"), Problem.Raw("medium"), Problem.Raw("low"), Problem.Raw("contested"))
        case _ => false),
      r.toString
    )
    val ok = Source.Write.applyPatch(
      existing,
      Source.Patch(findings =
        Some(
          FindingsPatch(add =
            List(
              Finding.Input(Finding.Claim("y"), confidence = Some(Confidence.Wire(" Medium "))),
              Finding.Input(Finding.Claim("z"), confidence = Some(Confidence.Wire("")))
            )
          )
        )
      )
    )
    assertEquals(ok.map(_.findings.takeRight(2).map(_.confidence)), Right(List(Some(Confidence.Medium), None)))

  test("findings: replace renumbers from 0 and drops the old ones"):
    val p = FindingsPatch(replace = Some(List(Finding.Input(Finding.Claim("only one")))))
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0"))
    assertEquals(out.findings.map(_.claim), List("only one"))

  test("findings: remove of an unknown id is a no-op, not an error"):
    val out = Source.Write.applyPatch(
      existing,
      Source.Patch(findings = Some(FindingsPatch(remove = List(FindingsPatch.Remove("nope#9")))))
    )
    assertEquals(out.map(_.findings), Right(existing.findings))

  test("findings: geo needs BOTH lon and lat; period needs both bounds"):
    val p = FindingsPatch(add =
      List(Finding.Input(Finding.Claim("half"), lat = Some(Latitude(39.0)), periodStart = None, periodEnd = None))
    )
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).toOption.get
    assertEquals(out.findings.last.geo, None)
    assertEquals(out.findings.last.period, None)

  test("findings: a half-specified period is rejected with a readable message"):
    val p = FindingsPatch(add = List(Finding.Input(Finding.Claim("bad"), periodStart = Some(Year(-1000)))))
    val e = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).swap.toOption.get
    assert(e.message.sentence.contains("period_start"), e)

  // ── metadata / scalars ───────────────────────────────────────────────────────────────────────
  test("metadata: present fields replace, absent fields are left alone"):
    val p = Source.Patch(
      metadata = Some(MetadataPatch(status = Some(Source.Status.Superseded), year = (Some(2016)).map(Year.apply))),
      edges = Some(
        EdgesPatch(add = List(Edge.Input(Citekey("newer2020_source"), Edge.Kind.SupersededBy)))
      )
    )
    val out = Source.Write.applyPatch(existing, p).toOption.get
    assertEquals(out.status, Source.Status.Superseded)
    assertEquals(out.year, Some(Year(2016)))
    assertEquals(out.title, existing.title)
    assertEquals(out.authors, existing.authors)
    assertEquals(out.doi, existing.doi)

  test("summary / body / notes / topics / regions each replace only themselves"):
    val out = Source.Write.applyPatch(existing, Source.Patch(notes = Some(Source.Notes("# rewritten")))).toOption.get
    assertEquals(out.notes, Some("# rewritten"))
    assertEquals(out.summary, existing.summary)
    assertEquals(out.ocrMarkdown, existing.ocrMarkdown)
    assertEquals(out.topics, existing.topics)

  test("period: replaced when present, and an inverted range is rejected"):
    val ok = Source.Write
      .applyPatch(existing, Source.Patch(period = Some(Period.Input(Year(-3300), Year(-2600)))))
      .toOption
      .get
    assertEquals(ok.period, Some(Period(Year(-3300), Year(-2600))))
    assert(Source.Write.applyPatch(existing, Source.Patch(period = Some(Period.Input(Year(-100), Year(-900))))).isLeft)

  // ── edges ────────────────────────────────────────────────────────────────────────────────────
  test("edges: add and remove, always rooted at this citekey, deduped"):
    val p = EdgesPatch(
      add = List(
        Edge.Input(Citekey("cline2014_1177"), Edge.Kind.Supersedes),
        Edge.Input(Citekey("anthony2007_horse"), Edge.Kind.References, Some(Citekey(ck)))
      ),
      remove = List(Edge.Input(Citekey("anthony2007_horse"), Edge.Kind.References))
    )
    val out = Source.Write.applyEdges(Citekey(ck), existing.edges, p).toOption.get
    assertEquals(out, List(Edge(Citekey(ck), Citekey("cline2014_1177"), Edge.Kind.Supersedes)))
    assert(out.forall(_.src == Citekey(ck)))

  // #698: a src naming ANOTHER source was rewritten to the patched one — a critic → target edge
  // sent to the target's patch became target → target
  test("edges: a src other than the patched citekey is refused, never rewritten into a self-edge"):
    val json = """{"edges":{"add":[{"src":"kassian2024_rebuttal","dst":"haak2015_massive","kind":"refutes"}]}}"""
    val p = RawJson.decode[Source.Patch](json).toOption.get
    val e = Source.Write.applyPatch(existing, p).swap.toOption.get
    assertEquals(
      e.message,
      Problem.Invalid.StoredOn(
        Problem.TypeName.named[Edge],
        Problem.Raw("'kassian2024_rebuttal' --refutes--> 'haak2015_massive'"),
        Problem.Raw("kassian2024_rebuttal"),
        Vector(Problem.Field("edges.add"))
      )
    )
    assert(e.message.sentence.contains("patch 'kassian2024_rebuttal' to record it"), e)
    val rm =
      EdgesPatch(remove = List(Edge.Input(Citekey("anthony2007_horse"), Edge.Kind.References, Some(Citekey("other")))))
    assert(Source.Write.applyEdges(Citekey(ck), existing.edges, rm).swap.toOption.get.sentence.contains("edges.remove"))

  test("edges: src is optional and defaults to the patched citekey; a self-edge is refused"):
    val p =
      RawJson.decode[Source.Patch]("""{"edges":{"add":[{"dst":"x2020_y","kind":"challenges"}]}}""").toOption.get
    assertEquals(
      Source.Write.applyPatch(existing, p).map(_.edges.last),
      Right(Edge(Citekey(ck), Citekey("x2020_y"), Edge.Kind.Challenges))
    )
    val self = EdgesPatch(add = List(Edge.Input(Citekey(ck), Edge.Kind.Challenges)))
    assert(Source.Write.applyEdges(Citekey(ck), existing.edges, self).isLeft)

  test("edges: an unknown kind is refused when the patch is decoded"):
    assert(RawJson.decode[Source.Patch]("""{"edges":{"add":[{"dst":"a","kind":"disagrees"}]}}""").isLeft)

  test("edges: a src naming the OLD citekey of a renamed source counts as the source itself"):
    val p = Source.Patch(edges =
      Some(
        EdgesPatch(add = List(Edge.Input(Citekey("b2020_x"), Edge.Kind.Refutes, Some(Citekey("old_key")))))
      )
    )
    val fixed = Source.Write.srcAliased(p, Citekey("old_key"), Citekey(ck))
    assertEquals(
      Source.Write.applyPatch(existing, fixed).map(_.edges.last),
      Right(Edge(Citekey(ck), Citekey("b2020_x"), Edge.Kind.Refutes))
    )
    assertEquals(Source.Write.srcAliased(p, Citekey(ck), Citekey(ck)), p)

  // ── ifUpdatedAt precondition ─────────────────────────────────────────────────────────────────
  test("ifUpdatedAt: matching token passes, stale token fails with an actionable message"):
    assertEquals(Source.Write.precondition(existing, Source.Patch(ifUpdatedAt = existing.updatedAt)), Right(()))
    val e =
      Source.Write
        .precondition(existing, Source.Patch(ifUpdatedAt = Some(Source.UpdatedAt("2026-01-01T00:00:00.000000Z"))))
        .swap
        .toOption
        .get
    assert(e.sentence.contains("has moved on"), e)
    assert(e.sentence.contains(ck), e)

  test("ifUpdatedAt: absent means last-write-wins, and a stale one blocks the whole patch"):
    assert(
      Source.Write.applyPatch(existing, Source.Patch(tags = Some(ListPatch(add = List(Write.ListItem("x")))))).isRight
    )
    val stale = Source.Patch(
      tags = Some(ListPatch(add = List(Write.ListItem("x")))),
      ifUpdatedAt = Some(Source.UpdatedAt("nope"))
    )
    assert(Source.Write.applyPatch(existing, stale).isLeft)

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put: numbers findings from 0, roots edges at the citekey, defaults status to unassessed"):
    val in = Source.Put(
      title = Title("T"),
      findings = List(Finding.Input(Finding.Claim("a")), Finding.Input(Finding.Claim("b"))),
      edges = List(
        Edge.Input(Citekey("x"), Edge.Kind.References),
        Edge.Input(Citekey("y"), Edge.Kind.Refutes, Some(Citekey(ck)))
      )
    )
    val out = Source.Write.applyPut(Citekey(ck), in).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0", s"$ck#1"))
    assertEquals(
      out.edges,
      List(Edge(Citekey(ck), Citekey("x"), Edge.Kind.References), Edge(Citekey(ck), Citekey("y"), Edge.Kind.Refutes))
    )
    assertEquals(out.status, Source.Status.Unassessed)

  // #766: put rewrote any edge src to the citekey, the pattern #698 fixed for patch only
  test("put: a src naming another source is refused, never rewritten"):
    val in = RawJson
      .decode[Source.Put]("""{"title":"T","edges":[{"src":"kassian2024_rebuttal","dst":"x2020_y","kind":"refutes"}]}""")
      .toOption
      .get
    val e = Source.Write.applyPut(Citekey(ck), in).swap.toOption.get
    assert(
      e match
        case Problem.Invalid.StoredOn(_, _, owner, _) => owner == Problem.Raw("kassian2024_rebuttal")
        case _                                        => false
      ,
      e
    )

  test("put: a self-edge is refused, including one to an old citekey of this source"):
    val self =
      Source.Put(title = Title("T"), edges = List(Edge.Input(Citekey(ck), Edge.Kind.Challenges)))
    assertEquals(
      Source.Write.applyPut(Citekey(ck), self).swap.toOption,
      Some(
        Problem.Invalid.Inconsistent(
          NonEmptyVector.of(Problem.Field("src"), Problem.Field("dst")),
          Problem.Invalid.Exclusion.Distinct,
          Vector(Problem.Field("edges"))
        )
      )
    )
    val viaAlias =
      Source.Put(title = Title("T"), edges = List(Edge.Input(Citekey("old_key"), Edge.Kind.Challenges)))
    assert(Source.Write.applyPut(Citekey(ck), viaAlias, (Set("old_key")).map(Citekey.apply)).isLeft)

  test("put: a src naming an old citekey of this source is this source"):
    val in = Source.Put(
      title = Title("T"),
      edges = List(Edge.Input(Citekey("b2020_x"), Edge.Kind.Refutes, Some(Citekey("old_key"))))
    )
    assertEquals(
      Source.Write.applyPut(Citekey(ck), in, (Set("old_key")).map(Citekey.apply)).map(_.edges),
      Right(List(Edge(Citekey(ck), Citekey("b2020_x"), Edge.Kind.Refutes)))
    )
    assert(Source.Write.applyPut(Citekey(ck), in).isLeft, "without the alias it is another source")

  test("put: SourcePut.of round-trips a source through applyPut"):
    val out = Source.Write.applyPut(Citekey(ck), Source.Put.of(existing)).toOption.get
    assertEquals(out, existing.modify(_.updatedAt).setTo(None))

  // ── period bounds must stay inside the representable window ──────────────────────────────────

  test("a period bound outside [Earliest, Latest] is a 400, not a stored sentinel"):
    val put = Source.Put(title = Title("T"), periodStart = Some(Year(Long.MinValue + 1)), periodEnd = Some(Year(-1200)))
    val e = Source.Write.applyPut(Citekey("zz_range"), put).swap.toOption.get
    assert(
      e match
        case Problem.Invalid.OutOfTime(_, _, _, at) => at.lastOption.contains(Problem.Field("period_start"))
        case _                                      => false
      ,
      e
    )
    assertEquals(
      e.sentence,
      "source.period_start: 9223372036854775807 BCE is outside the representable range " +
        "[1000000000 BCE, 1000000000 CE] — deep time is signed YEARS, BCE negative"
    )
    assert(
      Source.Write
        .applyPut(
          Citekey("zz_range"),
          Source.Put(title = Title("T"), periodStart = Some(Year(-1200)), periodEnd = Some(Year(Long.MaxValue)))
        )
        .isLeft
    )

  test("the sentinels themselves are the boundary, and are allowed"):
    assert(
      Source.Write
        .applyPut(
          Citekey("zz_range"),
          Source.Put(title = Title("T"), periodStart = Some(Filter.Earliest), periodEnd = Some(Filter.Latest))
        )
        .isRight
    )

  test("ordinary deep time still passes"):
    assertEquals(
      Source.Write
        .applyPut(
          Citekey("zz_range"),
          Source.Put(title = Title("T"), periodStart = Some(Year(-3300)), periodEnd = Some(Year(-1200)))
        )
        .map(_.period),
      Right(Some(Period(Year(-3300), Year(-1200))))
    )

  test("a finding's period is range-checked too"):
    val put = Source.Put(
      title = Title("T"),
      findings = List(
        Finding.Input(
          claim = Finding.Claim("c"),
          periodStart = Some(Year(-1200)),
          periodEnd = Some(Year(Long.MaxValue))
        )
      )
    )
    assert(Source.Write.applyPut(Citekey("zz_range"), put).isLeft)

  // #395: status was a free String — an unknown value silently became "unassessed"
  test("a status in a write must be one the store knows; the wire spelling round-trips"):
    import com.histmersion.util.json.RawJson.*
    assertEquals(
      RawJson.decode[Source.Patch]("""{"metadata":{"status":"superseded"}}""").map(_.metadata.flatMap(_.status)),
      Right(Some(Source.Status.Superseded))
    )
    assert(RawJson.decode[Source.Patch]("""{"metadata":{"status":"supersceded"}}""").isLeft, "a misspelling is refused")
    assert(RawJson.decode[Source.Put]("""{"title":"T","status":"bogus"}""").isLeft)
    assertEquals(
      Source
        .Put(Title("T"), status = Some(Source.Status.Contested))
        .asJson
        .noSpaces
        .contains("\"status\":\"contested\""),
      true
    )

  // a split left a volume record with doi = "" — neither a DOI nor "none"
  test("a blank DOI or venue is stored as none; in a patch, \"\" clears the field"):
    val put = Source.Write.applyPut(
      Citekey("zz_blank"),
      Source.Put(Title("T"), doi = (Some("  ")).map(Doi.apply), venue = (Some("")).map(Source.Venue.apply))
    )
    assertEquals(put.map(s => (s.doi, s.venue)), Right((None, None)))
    val existing = put.toOption.get.copy(doi = Some(Doi("10.1/x")), venue = Some(Source.Venue("V")))
    val cleared = RawJson.decode[Source.Patch]("""{"metadata":{"doi":"","venue":" "}}""").toOption.get
    assertEquals(Source.Write.applyPatch(existing, cleared).map(s => (s.doi, s.venue)), Right((None, None)))
    val kept = RawJson.decode[Source.Patch]("""{"metadata":{"title":"T2"}}""").toOption.get
    assertEquals(
      Source.Write.applyPatch(existing, kept).map(s => (s.doi, s.venue)),
      Right((Some(Doi("10.1/x")), Some(Source.Venue("V"))))
    )

  // #745: {"doi":null} decoded to "absent" and was silently ignored
  test("metadata: an explicit null clears a nullable field; absent leaves it alone"):
    val ex = existing.modify(_.calibreId).setTo(Some(Source.CalibreId("1805")))
    val p = RawJson
      .decode[Source.Patch](
        """{"metadata":{"doi":null,"venue":null,"year":null,"provenance":null,"calibreId":null}}"""
      )
      .toOption
      .get
    assertEquals(p.metadata.map(_.clear.toSet), Some(MetadataField.values.toSet))
    val out = Source.Write.applyPatch(ex, p).toOption.get
    assertEquals((out.doi, out.venue, out.year, out.provenance, out.calibreId), (None, None, None, None, None))
    val kept =
      Source.Write
        .applyPatch(ex, RawJson.decode[Source.Patch]("""{"metadata":{"title":"T2"}}""").toOption.get)
        .toOption
        .get
    assertEquals((kept.doi, kept.year, kept.calibreId), (ex.doi, ex.year, ex.calibreId))

  test("metadata: null on a field that cannot be cleared is refused, not ignored"):
    val e = RawJson.decode[Source.Patch]("""{"metadata":{"title":null}}""").swap.toOption.get
    assert(e.contains("metadata.title cannot be cleared"), e)

  test("metadata: a field both set and cleared is refused"):
    val p = Source.Patch(metadata =
      Some(MetadataPatch(doi = (Some("10.1/x")).map(Doi.apply), clear = List(MetadataField.Doi)))
    )
    assertEquals(
      Source.Write.applyPatch(existing, p).swap.toOption.map(_.message),
      Some(
        Problem.Invalid
          .Repeated(Problem.TypeName.named[MetadataField], Problem.Raw("doi"), Vector(Problem.Field("metadata")))
      )
    )

  test("metadata: the encoder omits absent fields, so a round trip never turns absent into a clear"):
    import com.histmersion.util.json.RawJson.*
    val m = MetadataPatch(title = (Some("T")).map(Title.apply), clear = List(MetadataField.Doi))
    val json = Source.Patch(metadata = Some(m)).asJson.noSpaces
    assert(!json.contains("\"venue\""), json)
    assertEquals(RawJson.decode[Source.Patch](json).map(_.metadata), Right(Some(m)))

  // #721: metadata.calibreId was not part of the patch at all
  test("metadata: calibreId re-links the source; it must be a Calibre entry number"):
    val p = Source.Patch(metadata = Some(MetadataPatch(calibreId = Some(Source.CalibreId("1806")))))
    assertEquals(Source.Write.applyPatch(existing, p).map(_.calibreId), Right(Some("1806")))
    val bad = Source.Patch(metadata = Some(MetadataPatch(calibreId = Some(Source.CalibreId("abc")))))
    assertEquals(
      Source.Write.applyPatch(existing, bad).swap.toOption.map(_.message),
      Some(
        Problem.Invalid.Unreadable(
          Problem.TypeName.of(Source.CalibreId),
          Problem.Raw("abc"),
          Vector(Problem.Raw("1805")),
          Vector(Problem.Field("metadata"), Problem.Field("calibreId"))
        )
      )
    )

  // ── #813: an unknown or misplaced key is refused, naming the key and the expected shape ─────────
  test("strict: an unknown top-level key in a patch is refused, with the keys that are accepted"):
    val e = RawJson.decode[Source.Patch]("""{"tagz":{"add":["x"]},"summary":"s"}""").swap.toOption.get
    assert(e.contains("unknown key 'tagz' (one of: metadata, summary, body, notes"), e)

  test("strict: a key that belongs in a nested part says where it goes"):
    val e = RawJson.decode[Source.Patch]("""{"status":"current","title":"T"}""").swap.toOption.get
    assert(e.contains("unknown key 'status' (one of: metadata, "), e)
    assert(e.contains("unknown key 'title' (one of: metadata, "), e)
    assert(e.contains("'status' belongs under 'metadata' (metadata.status)"), e)
    assert(e.contains("'title' belongs under 'metadata' (metadata.title)"), e)

  test("strict: nested parts are strict too, and the message carries the path"):
    val tags = RawJson.decode[Source.Patch]("""{"tags":{"items":["x"]}}""").swap.toOption.get
    assert(tags.contains("tags: unknown key 'items' (one of: add, remove, replace)"), tags)
    val f = RawJson.decode[Source.Patch]("""{"findings":{"add":[{"claim":"c","date":-1200}]}}""").swap.toOption.get
    assert(f.contains("unknown key 'date'"), f)
    assert(f.contains("findings.add"), f)
    val meta = RawJson.decode[Source.Patch]("""{"metadata":{"stauts":"current"}}""").swap.toOption.get
    assert(meta.contains("metadata: unknown key 'stauts'"), meta)

  test("strict: an array where an object is expected names the expected shape"):
    val e = RawJson.decode[Source.Patch]("""{"tags":["a","b"]}""").swap.toOption.get
    assert(e.contains("tags: expected an object with keys add, remove, replace; got an array"), e)

  test("strict: put, articles and the other write inputs refuse unknown keys"):
    assert(RawJson.decode[Source.Put]("""{"title":"T","citekey":"x"}""").isLeft)
    assert(RawJson.decode[Source.Put]("""{"title":"T","edges":[{"dst":"a","kind":"refutes","weight":1}]}""").isLeft)
    assert(RawJson.decode[Article.Patch]("""{"appendix":"x"}""").isLeft)
    assert(
      RawJson.decode[Article.Put]("""{"title":"T","kind":"concept","body":"b","period":{"start":1,"end":2}}""").isLeft
    )
    assert(RawJson.decode[Goal.Patch]("""{"state":"done"}""").isLeft)
    assert(RawJson.decode[EventPatch]("""{"name":"x"}""").isLeft)
    assert(RawJson.decode[Attestation.Patch]("""{"quoted":"x"}""").isLeft)
    assert(RawJson.decode[Queue.QueuePatch]("""{"done":true}""").isLeft)

  test("strict: what the encoders write decodes again (the CLI and the web UI never send a refused key)"):
    import com.histmersion.util.json.RawJson.*
    val p = Source.Patch(
      metadata = Some(MetadataPatch(title = (Some("T")).map(Title.apply), clear = List(MetadataField.Doi))),
      summary = Some(Source.Summary("s")),
      tags = Some(ListPatch(add = List(Write.ListItem("a")), replace = Some(List(Write.ListItem("b"))))),
      findings = Some(
        FindingsPatch(add =
          List(Finding.Input(Finding.Claim("c"), lat = Some(Latitude(1.0)), lon = Some(Longitude(2.0))))
        )
      ),
      edges = Some(EdgesPatch(add = List(Edge.Input(Citekey("d"), Edge.Kind.Refutes)))),
      period = Some(Period.Input(Year(-10), Year(10))),
      ifUpdatedAt = Some(Source.UpdatedAt("t"))
    )
    assertEquals(RawJson.decode[Source.Patch](p.asJson.noSpaces), Right(p))
    val put = Source.Put.of(existing)
    assertEquals(RawJson.decode[Source.Put](put.asJson.noSpaces), Right(put))
    val ap =
      Article.Patch(
        append = Some(Article.Patch.Append("x")),
        tags = Some(ListPatch(add = List(Write.ListItem("t")))),
        period = Some(Period.Input(Year(1), Year(2)))
      )
    assertEquals(RawJson.decode[Article.Patch](ap.asJson.noSpaces), Right(ap))

  test("strict: metadata null-clears still work alongside the key check"):
    assertEquals(
      RawJson.decode[Source.Patch]("""{"metadata":{"doi":null}}""").map(_.metadata.map(_.clear)),
      Right(Some(List(MetadataField.Doi)))
    )
    assert(
      RawJson.decode[Source.Patch]("""{"metadata":{"isbn":null}}""").swap.toOption.get.contains("unknown key 'isbn'")
    )

  test("an error answers with its first problem's status; a code a site still gives keeps its own until step 7"):
    val (key, stamp) = (Problem.Raw("x"), Problem.Conflict.Stamp("2026-11-01T10:00:00Z"))
    val title = Problem.Invalid.Missing.here(Problem.Field("title"))
    val missing = Problem.NotFound.Entity(Problem.TypeName.of(Citekey), key)
    val stale = Problem.Conflict.Stale(Problem.TypeName.of(Citekey), key, stamp, stamp)
    val (invalid, notFound) = (Some(Api.Error.Code(Write.CodeInvalid)), Some(Api.Error.Code(Write.CodeNotFound)))
    assertEquals(Api.Error(missing, invalid).status, StatusCode.NotFound)
    assertEquals(Api.Error(title).status, StatusCode.BadRequest)
    assertEquals(Api.Error(title, notFound).status, StatusCode.NotFound)
    assertEquals(Api.Error(title, invalid).status, StatusCode.BadRequest)
    assertEquals(Api.Error(Problems.of(stale, missing)).status, StatusCode.PreconditionFailed)
    val two = Api.Error(Problems.of(title, missing), None)
    assertEquals(two.problems.size, 2)
    val json = two.asJson.noSpaces
    assert(json.startsWith("""{"problems":[{"""), json)
    assertEquals(RawJson.decode[Api.Error](json), Right(two))
