package com.histmersion
package api

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import java.nio.file.{Files, Path}
import com.histmersion.api.catalogue.{
  HistoricalPeriod,
  Latitude,
  Longitude,
  Period,
  PeriodO,
  PeriodsMarkdown,
  PresetMenu,
  PresetRegion,
  SkippedPeriod,
  Year
}

/** The PeriodO reader (#719): pure, so every rule is pinned here — ISO years, outer bounds, English labels, regions,
  * unreadable spans, the parent rules and the parents-first order — plus the committed seed itself, which is what a
  * fresh install's timeline presets are made of.
  */
class PeriodsSuite extends munit.FunSuite:

  /** A two-authority dump in PeriodO's own shape (abridged from the real one). */
  private val dump = """{
    "authorities": {
      "p0aaa": {
        "id": "p0aaa", "type": "Authority",
        "source": {"title": "Archaeological periods of Somewhere", "yearPublished": 2022},
        "periods": {
          "p0aaa01": {"id": "p0aaa01", "label": "Bronze Age", "spatialCoverageDescription": "Greece",
                      "languageTag": "en", "localizedLabels": {"en": ["Age of Bronze", "Bronze Age"]},
                      "start": {"in": {"year": "-3199"}}, "stop": {"in": {"year": "-1049"}}},
          "p0aaa02": {"id": "p0aaa02", "label": "Late Minoan", "broader": "p0aaa01",
                      "spatialCoverageDescription": "Crete",
                      "start": {"in": {"earliestYear": "-1699", "latestYear": "-1674"}},
                      "stop": {"in": {"earliestYear": "-1074", "latestYear": "-1049"}}},
          "p0aaa03": {"id": "p0aaa03", "label": "Late Minoan IA", "broader": "p0aaa02",
                      "start": {"in": {"year": "-1699"}}, "stop": {"in": {"year": "-1624"}}},
          "p0aaa04": {"id": "p0aaa04", "label": "Broken", "start": {"in": {}}, "stop": {"in": {"year": "0100"}}},
          "p0aaa05": {"id": "p0aaa05", "label": "Backwards", "start": {"in": {"year": "0500"}},
                      "stop": {"in": {"year": "0100"}}}
        }
      },
      "p0bbb": {
        "id": "p0bbb", "type": "Authority",
        "source": {"partOf": {"title": "FASTI", "yearPublished": 2004}},
        "periods": {
          "p0bbb01": {"id": "p0bbb01", "label": "Доба ранньої бронзи", "languageTag": "uk",
                      "localizedLabels": {"uk": ["Доба ранньої бронзи"], "en": ["Early Bronze Age"]},
                      "spatialCoverage": [{"id": "http://www.wikidata.org/entity/Q212", "label": "Ukraine"}],
                      "start": {"in": {"year": "-3199"}}, "stop": {"in": {"year": 0}}}
        }
      }
    }
  }"""

  private def parsed = PeriodO.parse(dump).fold(e => fail(e.sentence), identity)
  private def byId(ps: List[HistoricalPeriod]) = ps.map(p => p.id -> p).toMap

  test("ISO 8601 years count a year 0; signed years do not"):
    assertEquals(PeriodO.isoYear("-2999"), Some(-3000L)) // 3000 BC
    assertEquals(PeriodO.isoYear("0000"), Some(-1L)) // 1 BC
    assertEquals(PeriodO.isoYear("0001"), Some(1L))
    assertEquals(PeriodO.isoYear("0640"), Some(640L))
    assertEquals(PeriodO.isoYear("-349999"), Some(-350000L))
    assertEquals(PeriodO.isoYear("about 3000 BC"), None)

  test("a definition's span is its outer bounds, in signed years"):
    val d = parsed.authorities.flatMap(_.periods).map(p => p.id -> p).toMap
    assertEquals(d(HistoricalPeriod.Id("p0aaa01")).period, Period(Year(-3200), Year(-1050)))
    assertEquals(
      d(HistoricalPeriod.Id("p0aaa02")).period,
      Period(Year(-1700), Year(-1050))
    ) // earliest start, latest stop
    assertEquals(d(HistoricalPeriod.Id("p0bbb01")).period, Period(Year(-3200), Year(-1))) // a numeric year is read too

  test("labels are the authority's own in English, else its English alternative"):
    val d = parsed.authorities.flatMap(_.periods).map(p => p.id -> p).toMap
    assertEquals(
      d(HistoricalPeriod.Id("p0aaa01")).label,
      "Bronze Age"
    ) // ROAD lists "Late Stone Age" first for "Neolithic"
    assertEquals(d(HistoricalPeriod.Id("p0bbb01")).label, "Early Bronze Age")

  test("the region is the coverage description, else the coverage labels"):
    val d = parsed.authorities.flatMap(_.periods).map(p => p.id -> p).toMap
    assertEquals(d(HistoricalPeriod.Id("p0bbb01")).region, Some("Ukraine"))
    assertEquals(d(HistoricalPeriod.Id("p0aaa02")).region, Some("Crete"))
    assertEquals(d(HistoricalPeriod.Id("p0aaa03")).region, None)

  test("a definition without a readable span is skipped and named, never guessed"):
    val ds = parsed
    assert(!ds.authorities.flatMap(_.periods).exists(p => Set("p0aaa04", "p0aaa05")(p.id.value)))
    assertEquals(ds.skipped.size, 2, ds.skipped)
    assert(ds.skipped.exists(s => s.label.value == "Backwards" && s.reason == Period.Skip.EndsBeforeStart), ds.skipped)
    assertEquals(ds.skipped.find(_.id == SkippedPeriod.Id("p0aaa04")).map(_.reason), Some(Period.Skip.NoSpan))

  test("the authority citation names the PeriodO id, the source and its year"):
    val rows = byId(PeriodO.toPeriods(parsed))
    assertEquals(
      rows(HistoricalPeriod.Id("p0aaa01")).authority,
      Some("PeriodO p0aaa01 · Archaeological periods of Somewhere (2022)")
    )
    assertEquals(rows(HistoricalPeriod.Id("p0bbb01")).authority, Some("PeriodO p0bbb01 · FASTI (2004)"))

  test("PeriodO `broader` links inside the batch are kept; everything else is a root"):
    val rows = byId(PeriodO.toPeriods(parsed))
    assertEquals(rows(HistoricalPeriod.Id("p0aaa02")).parentId, Some("p0aaa01"))
    assertEquals(rows(HistoricalPeriod.Id("p0aaa03")).parentId, Some("p0aaa02"))
    assertEquals(rows(HistoricalPeriod.Id("p0aaa01")).parentId, None)
    assertEquals(rows(HistoricalPeriod.Id("p0bbb01")).parentId, None)

  test("a broader period outside the batch is dropped, not left dangling"):
    val rows = byId(
      PeriodO.toPeriods(
        PeriodO
          .parse(dump)
          .toOption
          .get
          .copy(authorities =
            parsed.authorities
              .map(a => a.modify(_.periods).setTo(a.periods.filterNot(_.id == HistoricalPeriod.Id("p0aaa01"))))
          )
      )
    )
    assertEquals(rows(HistoricalPeriod.Id("p0aaa02")).parentId, None)

  test("the curated overlay wins over `broader`"):
    val rows = byId(
      PeriodO.toPeriods(
        parsed.copy(parents =
          Map(
            PeriodODataset.ParentsKey("p0aaa03") -> PeriodODataset.ParentsValue("p0aaa01"),
            PeriodODataset.ParentsKey("p0bbb01") -> PeriodODataset.ParentsValue("p0aaa01")
          )
        )
      )
    )
    assertEquals(rows(HistoricalPeriod.Id("p0aaa03")).parentId, Some("p0aaa01"))
    assertEquals(rows(HistoricalPeriod.Id("p0bbb01")).parentId, Some("p0aaa01"))

  test("with roots, a collection hangs under one node per authority spanning its periods"):
    val rows = PeriodO.toPeriods(parsed, roots = true)
    val m = byId(rows)
    assertEquals(m(HistoricalPeriod.Id("p0aaa")).parentId, None)
    assertEquals(m(HistoricalPeriod.Id("p0aaa")).period, Period(Year(-3200), Year(-1050)))
    assertEquals(m(HistoricalPeriod.Id("p0aaa")).label, "Archaeological periods of Somewhere")
    assertEquals(m(HistoricalPeriod.Id("p0aaa01")).parentId, Some("p0aaa"))
    assertEquals(m(HistoricalPeriod.Id("p0aaa02")).parentId, Some("p0aaa01")) // its own broader still wins
    assertEquals(m(HistoricalPeriod.Id("p0bbb01")).parentId, Some("p0bbb"))
    assertEquals(rows.count(_.parentId.isEmpty), 2)

  test("a period already in the store keeps its place — a preset is never buried by a fetch"):
    val existing: Map[HistoricalPeriod.Id, Option[HistoricalPeriod.Id]] = Map(
      HistoricalPeriod.Id("p0aaa01") -> None,
      HistoricalPeriod.Id("p0bbb01") -> Some(HistoricalPeriod.Id("p0aaa01")),
      HistoricalPeriod.Id("p0aaa03") -> Some(HistoricalPeriod.Id("p0aaa01"))
    )
    val m = byId(PeriodO.toPeriods(parsed, existing, roots = true))
    assertEquals(m(HistoricalPeriod.Id("p0aaa01")).parentId, None) // was a preset, stays one
    assertEquals(m(HistoricalPeriod.Id("p0bbb01")).parentId, Some("p0aaa01")) // placed by the seed, stays there
    assertEquals(m(HistoricalPeriod.Id("p0aaa03")).parentId, Some("p0aaa01")) // over its own broader, too
    assertEquals(m(HistoricalPeriod.Id("p0aaa02")).parentId, Some("p0aaa01")) // new: its broader

  test("rows come parents first, and a cycle is cut rather than looped"):
    val rows = PeriodO.toPeriods(parsed, roots = true)
    rows.zipWithIndex.foreach { (r, i) =>
      r.parentId.foreach(p => assert(rows.indexWhere(_.id.value == p.value) < i, s"${r.id} before its parent $p"))
    }
    val a = HistoricalPeriod(
      HistoricalPeriod.Id("a"),
      HistoricalPeriod.Label("A"),
      None,
      Period(Year(1), Year(2)),
      None,
      Some(HistoricalPeriod.ParentId("b"))
    )
    val b = HistoricalPeriod(
      HistoricalPeriod.Id("b"),
      HistoricalPeriod.Label("B"),
      None,
      Period(Year(1), Year(2)),
      None,
      Some(HistoricalPeriod.ParentId("a"))
    )
    val out = PeriodO.parentsFirst(List(a, b))
    assertEquals(out.map(_.id).toSet, Set("a", "b"))
    assertEquals(out.count(_.parentId.isEmpty), 1)

  test("only() keeps the named authorities; nothing named keeps them all"):
    assertEquals(parsed.only(Set(HistoricalPeriod.Id("p0bbb"))).authorities.map(_.id), List("p0bbb"))
    assertEquals(
      parsed.only(Set(HistoricalPeriod.Id("p0bbb"))).skipped,
      Nil
    ) // p0aaa's broken definitions are not p0bbb's
    assertEquals(parsed.only(Set(HistoricalPeriod.Id("p0aaa"))).skipped.size, 2)
    assertEquals(parsed.only(Set.empty).authorities.size, 2)

  // #771: EAMENA's "Old Kingdom" came in as -2701 → 2150 and was stored as it was
  private def authorityOf(periods: String) =
    s"""{"authorities": {"p0eam": {"id": "p0eam", "source": {"title": "EAMENA"}, "periods": {$periods}}}}"""
  private def defn(id: String, label: String, start: String, stop: String, extra: String = "") =
    s""""$id": {"id": "$id", "label": "$label", "spatialCoverageDescription": "Egypt",
       "start": {"in": {"year": "$start"}}, "stop": {"in": {"year": "$stop"}}$extra}"""

  test("a definition outside -3,000,000..2100 is skipped and reported, never clamped"):
    val ds = PeriodO
      .parse(
        authorityOf(
          List(
            defn("p0eam01", "Old Kingdom", "-2700", "2150"),
            defn("p0eam02", "Too early", "-3500000", "-3000000"),
            defn("p0eam03", "Middle Kingdom", "-2054", "-1649")
          ).mkString(",")
        )
      )
      .toOption
      .get
    assertEquals(ds.authorities.flatMap(_.periods).map(_.id), List("p0eam03"))
    assertEquals(
      ds.skipped.map(s => s.id -> s.reason),
      List("p0eam01" -> Period.Skip.OutOfRange, "p0eam02" -> Period.Skip.OutOfRange)
    )
    assert(ds.skipped.head.detail.value.contains("2150"), ds.skipped.head.detail)

  test("a definition far longer than every other its authority gives for the region is skipped"):
    val phases = (1 to 4).map(i => defn(s"p0eam1$i", s"Phase $i", s"${-3000 + i * 300}", s"${-2800 + i * 300}"))
    val ds =
      PeriodO.parse(authorityOf((phases :+ defn("p0eam20", "Old Kingdom", "-2700", "1950")).mkString(","))).toOption.get
    assertEquals(ds.skipped.map(s => s.id -> s.reason), List("p0eam20" -> Period.Skip.ImplausibleSpan))
    assertEquals(ds.authorities.flatMap(_.periods).size, 4)

  test("an umbrella (another definition's broader) and a sparse region are never judged implausible"):
    val phases = (1 to 4).map(i =>
      defn(s"p0eam1$i", s"Phase $i", s"${-3000 + i * 300}", s"${-2800 + i * 300}", """, "broader": "p0eam20"""")
    )
    val umbrella =
      PeriodO.parse(authorityOf((phases :+ defn("p0eam20", "Pharaonic", "-3100", "1950")).mkString(","))).toOption.get
    assertEquals(umbrella.skipped, Nil)
    val sparse = PeriodO
      .parse(
        authorityOf(
          List(defn("p0eam01", "Short", "-1000", "-900"), defn("p0eam02", "Long", "-3000", "1900")).mkString(",")
        )
      )
      .toOption
      .get
    assertEquals(sparse.skipped, Nil)

  test("an import result counts every skipped definition and names them"):
    val r = Period.ImportResult(
      Period.ImportResult.Periods(3),
      Period.ImportResult.Roots(0),
      Period.ImportResult.Presets(5),
      List(
        SkippedPeriod(
          SkippedPeriod.Id("p0x1"),
          SkippedPeriod.Label("Old Kingdom"),
          Period.Skip.OutOfRange,
          SkippedPeriod.Detail("-2701 → 2150 lies outside -3000000..2100")
        )
      ),
      skippedCount = Period.ImportResult.SkippedCount(2)
    )
    val md = PeriodsMarkdown.imported(r)
    assert(md.contains("skipped 2 definitions (not imported)"), md)
    assert(md.contains("`p0x1` Old Kingdom: -2701 → 2150 lies outside"), md)
    assert(md.contains("…and 1 more"), md)
    assert(
      PeriodsMarkdown
        .imported(Period.ImportResult(Period.ImportResult.Periods(1), presets = Period.ImportResult.Presets(1)))
        .startsWith("imported 1 period — 1 timeline preset")
    )

  test("what is not a PeriodO dataset is refused"):
    assert(PeriodO.parse("""{"type": "FeatureCollection"}""").isLeft)
    assert(PeriodO.parse("not json").isLeft)

  // ── the committed seed: the presets a fresh install gets ──────────────────────────────────────
  private lazy val seed =
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    PeriodO.parse(Files.readString(root.resolve("db/periods/periodo-seed.json"))).fold(e => fail(e.sentence), identity)

  test("the seed reads cleanly and every curated parent is in it"):
    // #771: the only definitions left out are EAMENA's two Palaeolithic periods that PeriodO dates
    // from 14,000,000 BC — a data error the import reports rather than "fixes"
    assertEquals(
      seed.skipped.map(s => s.id -> s.reason),
      List("p0m64tdgpvd" -> Period.Skip.OutOfRange, "p0m64tdp282" -> Period.Skip.OutOfRange)
    )
    val ids = seed.authorities.flatMap(_.periods).map(_.id.value).toSet
    assert(ids.size > 150, ids.size.toString)
    val left = seed.skipped.map(_.id.value).toSet
    seed.parents.filterNot((c, _) => left(c.value)).foreach((c, p) => assert(ids(c.value) && ids(p.value), s"$c → $p"))

  private lazy val seedRows = PeriodO.toPeriods(seed)
  private lazy val presets = seedRows.filter(_.preset.isDefined)
  private val ages = """(?i)\b(neolithic|chalcolithic|eneolithic|copper age|bronze age|iron age)\b""".r

  test("the seed is versioned and names its presets under known region headings"):
    assert(seed.version.exists(_.value >= 2), seed.version.toString)
    assert(presets.size >= 100, presets.size.toString)
    assertEquals(presets.size, seed.presets.size)
    // every heading but the continent-wide one says where on the map it is
    PresetRegion.values.filterNot(_ == PresetRegion.Broad).foreach(r => assert(r.box.isDefined, r.toString))

  test("every preset says WHERE it applies and WHO defines it (a PeriodO citation)"):
    presets.foreach { p =>
      assert(p.region.exists(_.value.trim.nonEmpty), s"${p.id} ${p.label}: no region")
      assert(p.authority.exists(_.value.startsWith(s"PeriodO ${p.id} · ")), s"${p.id}: ${p.authority}")
      assert(PresetMenu.source(p).exists(_.value.nonEmpty), p.id)
    }

  test("no age is offered as one authority's continent-wide umbrella span"):
    val continental = Set("eurasia", "europe", "asia", "africa, europe, and asia")
    val umbrellas = presets.filter(p =>
      ages.findFirstIn(p.label.value).isDefined &&
        p.region.exists(r => continental(r.value.trim.toLowerCase))
    )
    assertEquals(umbrellas.map(p => s"${p.id} ${p.label} (${p.region.fold("")(_.value)})"), Nil)
    // ROAD's Eurasian Neolithic, Chalcolithic, Bronze Age and Iron Age stay in the store, off the menu
    List("p0s2rwkkkxz", "p0s2rwktsx4", "p0s2rwkwbsz", "p0s2rwkrbhq").map(HistoricalPeriod.Id(_)).foreach { id =>
      assert(seedRows.exists(_.id == id), id)
      assert(!presets.exists(_.id == id), id)
    }
    // the continent-wide heading holds only what PeriodO defines for a continent
    presets
      .filter(_.preset.contains(PresetRegion.Broad))
      .foreach(p => assert(ages.findFirstIn(p.label.value).isEmpty, s"${p.label} under the continent-wide heading"))

  test("the big ages come in regional variants, each region dating them its own way"):
    def headings(age: String) = presets.filter(_.label.value.toLowerCase.contains(age)).flatMap(_.preset).toSet
    val bronze = headings("bronze")
    List(
      PresetRegion.Aegean,
      PresetRegion.Anatolia,
      PresetRegion.LevantMesopotamia,
      PresetRegion.Iran,
      PresetRegion.Cyprus,
      PresetRegion.SoutheastEurope,
      PresetRegion.EasternEurope,
      PresetRegion.CentralEurope,
      PresetRegion.Italy,
      PresetRegion.Iberia,
      PresetRegion.BritainIreland,
      PresetRegion.Scandinavia
    )
      .foreach(r => assert(bronze(r), s"no Bronze Age under $r"))
    assert(headings("iron age").size >= 10, headings("iron age").toString)
    assert(headings("neolithic").size >= 10, headings("neolithic").toString)
    assert((headings("chalcolithic") ++ headings("eneolithic") ++ headings("copper age")).size >= 8)
    // the point of it: the same age starts centuries apart
    val starts = presets.filter(_.label.value.toLowerCase == "bronze age").map(_.period.start.value)
    assert(starts.max - starts.min >= 1500, starts.sorted.toString)

  test("no heading offers the same definition twice (one authority per age and region)"):
    presets.groupBy(_.preset).foreach { (r, ps) =>
      val dup = ps.groupBy(p => (p.label.value.toLowerCase, p.region)).filter(_._2.size > 1).keys
      assert(dup.isEmpty, s"$r: $dup")
    }

  test("'part of' is always the authority's own: a curated parent is the same authority's and spans its child"):
    val byId = seedRows.map(p => p.id.value -> p).toMap
    seed.parents.foreach { (c, p) =>
      assertEquals(c.value.take(7), p.value.take(7), s"$c → $p crosses authorities")
      for child <- byId.get(c.value); parent <- byId.get(p.value) do
        assert(
          parent.period.start.value <= child.period.start.value && child.period.end.value <= parent.period.end.value,
          s"${child.label} ${child.period} outside ${parent.label} ${parent.period}"
        )
    }
    seedRows.foreach(r =>
      r.parentId.foreach(p => assertEquals(r.id.value.take(7), p.value.take(7), s"${r.id} under $p"))
    )

  test("the Aegean Bronze Age is divided Helladic / Cycladic / Minoan, down to the phases"):
    val m = byId(seedRows)
    def chain(id: HistoricalPeriod.Id): List[String] = m
      .get(id)
      .toList
      .flatMap(p => p.label.value :: p.parentId.toList.flatMap(pid => chain(HistoricalPeriod.Id(pid.value))))
    // Late Minoan → Bronze Age Minoan → Bronze Age (the BSA's, for Greece) — and nothing above it
    assertEquals(chain(HistoricalPeriod.Id("p0ds9qjh4dh")), List("Late Minoan", "Bronze Age Minoan", "Bronze Age"))
    assertEquals(
      m(HistoricalPeriod.Id("p0ds9qj9pc2")).parentId,
      Some("p0ds9qjq7b2")
    ) // Bronze Age Helladic under the BSA's Bronze Age
    assertEquals(m(HistoricalPeriod.Id("p0ds9qjq7b2")).parentId, None) // no longer under ROAD's Eurasia
    assert(
      m.values.exists(p =>
        p.label.value == "Early Helladic" && p.parentId.contains(HistoricalPeriod.ParentId("p0ds9qj9pc2"))
      )
    )
    assert(
      m.values.exists(p =>
        p.label.value == "Middle Minoan" && p.parentId.contains(HistoricalPeriod.ParentId("p0ds9qj9kdj"))
      )
    )
    assertEquals(m(HistoricalPeriod.Id("p0ds9qjq7b2")).region, Some("Greece"))
    assertEquals(m(HistoricalPeriod.Id("p0ds9qjq7b2")).preset, Some(PresetRegion.Aegean))
    // the BSA dates Late Minoan from the earliest start to the latest end it gives
    assertEquals(m(HistoricalPeriod.Id("p0ds9qjh4dh")).period, Period(Year(-1700), Year(-1050)))

  // ── the curated overlays ─────────────────────────────────────────────────────────────────────

  private def withOverlay(overlay: String) =
    dump.trim.stripSuffix("}") + s""", "histmersion": $overlay}"""

  test("a seed's presets and version are read; an unknown region heading is refused"):
    val d = PeriodO
      .parse(withOverlay("""{"version": 3, "presets": {"p0aaa01": "aegean", "p0bbb01": "eastern-europe"}}"""))
      .fold(e => fail(e.sentence), identity)
    assertEquals(d.version, Some(3))
    assertEquals(d.presets, Map("p0aaa01" -> PresetRegion.Aegean, "p0bbb01" -> PresetRegion.EasternEurope))
    assert(d.curated)
    val rows = byId(PeriodO.toPeriods(d))
    assertEquals(rows(HistoricalPeriod.Id("p0aaa01")).preset, Some(PresetRegion.Aegean))
    assertEquals(rows(HistoricalPeriod.Id("p0aaa02")).preset, None)
    val bad = PeriodO.parse(withOverlay("""{"presets": {"p0aaa01": "atlantis"}}"""))
    assert(bad.left.exists(_.sentence.contains("atlantis")), bad.toString)
    assert(!parsed.curated)

  test("a curated seed re-files its own rows; a raw dump keeps where the store has them"):
    // an older seed nested the BSA's Bronze Age under another authority's span
    val existing: Map[HistoricalPeriod.Id, Option[HistoricalPeriod.Id]] = Map(
      HistoricalPeriod.Id("p0aaa01") -> Some(HistoricalPeriod.Id("p0zzz01")),
      HistoricalPeriod.Id("p0aaa02") -> Some(HistoricalPeriod.Id("p0zzz01"))
    )
    val seeded = byId(
      PeriodO.toPeriods(
        parsed.copy(presets = Map(PeriodODataset.PresetsKey("p0aaa01") -> PresetRegion.Aegean)),
        existing
      )
    )
    assertEquals(seeded(HistoricalPeriod.Id("p0aaa01")).parentId, None) // the seed says: a root
    assertEquals(seeded(HistoricalPeriod.Id("p0aaa02")).parentId, Some("p0aaa01")) // its own broader
    val fetched = byId(PeriodO.toPeriods(parsed, existing))
    assertEquals(fetched(HistoricalPeriod.Id("p0aaa01")).parentId, Some("p0zzz01"))

  // ── the timeline menu ────────────────────────────────────────────────────────────────────────

  private def preset(
      id: HistoricalPeriod.Id,
      label: HistoricalPeriod.Label,
      s: Year,
      r: PresetRegion,
      where: HistoricalPeriod.Region = HistoricalPeriod.Region("Somewhere")
  ) =
    HistoricalPeriod(
      id,
      label,
      Some(HistoricalPeriod.Authority(s"PeriodO $id · An authority (2020)")),
      Period(s, Year(s.value + 500)),
      Some(where),
      None,
      Some(r)
    )

  test("the menu groups presets by region heading, oldest first, in the headings' order"):
    val ps = List(
      preset(
        HistoricalPeriod.Id("b"),
        HistoricalPeriod.Label("Bronze Age"),
        Year(-1700),
        PresetRegion.Scandinavia,
        HistoricalPeriod.Region("Denmark")
      ),
      preset(
        HistoricalPeriod.Id("n"),
        HistoricalPeriod.Label("Neolithic"),
        Year(-3900),
        PresetRegion.Scandinavia,
        HistoricalPeriod.Region("Denmark")
      ),
      preset(
        HistoricalPeriod.Id("a"),
        HistoricalPeriod.Label("Bronze Age"),
        Year(-3200),
        PresetRegion.Aegean,
        HistoricalPeriod.Region("Greece")
      ),
      HistoricalPeriod(HistoricalPeriod.Id("x"), HistoricalPeriod.Label("Not a preset"), None, Period(Year(1), Year(2)))
    )
    val gs = PresetMenu.groups(ps)
    assertEquals(gs.map(_.region), List(PresetRegion.Aegean, PresetRegion.Scandinavia))
    assertEquals(gs(1).presets.map(_.id), List("n", "b"))
    assert(gs.forall(!_.inView))

  test("the region the map mostly shows comes first; a world view reorders nothing"):
    val ps = List(
      preset(HistoricalPeriod.Id("a"), HistoricalPeriod.Label("Bronze Age"), Year(-3200), PresetRegion.Aegean),
      preset(HistoricalPeriod.Id("b"), HistoricalPeriod.Label("Bronze Age"), Year(-2200), PresetRegion.BritainIreland)
    )
    val greece = BBox(Longitude(21.0), Latitude(36.0), Longitude(26.0), Latitude(40.0))
    assertEquals(
      PresetMenu.groups(ps, Some(greece)).map(g => (g.region, g.inView)),
      List(PresetRegion.Aegean -> true, PresetRegion.BritainIreland -> false)
    )
    val britain = BBox(Longitude(-6.0), Latitude(50.5), Longitude(1.0), Latitude(55.0))
    assertEquals(
      PresetMenu.groups(ps, Some(britain)).map(_.region),
      List(PresetRegion.BritainIreland, PresetRegion.Aegean)
    )
    val world = BBox(Longitude(-180), Latitude(-85), Longitude(180), Latitude(85))
    assertEquals(
      PresetMenu.groups(ps, Some(world)).map(g => (g.region, g.inView)),
      List(PresetRegion.Aegean -> false, PresetRegion.BritainIreland -> false)
    )

  test("share: the part of a view inside a box"):
    val box = BBox(Longitude(0), Latitude(0), Longitude(10), Latitude(10))
    assertEqualsDouble(
      PresetMenu.share(BBox(Longitude(0), Latitude(0), Longitude(10), Latitude(10)), box).value,
      1.0,
      1e-9
    )
    assertEqualsDouble(
      PresetMenu.share(BBox(Longitude(5), Latitude(0), Longitude(15), Latitude(10)), box).value,
      0.5,
      1e-9
    )
    assertEqualsDouble(
      PresetMenu.share(BBox(Longitude(20), Latitude(20), Longitude(30), Latitude(30)), box).value,
      0.0,
      1e-9
    )
    assertEqualsDouble(
      PresetMenu.share(BBox(Longitude(170), Latitude(0), Longitude(-170), Latitude(10)), box).value,
      0.0,
      1e-9
    ) // across the antimeridian
    assertEqualsDouble(
      PresetMenu.share(BBox(Longitude(1), Latitude(1), Longitude(1), Latitude(1)), box).value,
      0.0,
      1e-9
    ) // no area

  test("a row names its authority without the PeriodO id, and the id it cites"):
    val p =
      preset(HistoricalPeriod.Id("p0ds9qjq7b2"), HistoricalPeriod.Label("Bronze Age"), Year(-3200), PresetRegion.Aegean)
    assertEquals(PresetMenu.source(p), Some("An authority (2020)"))
    assertEquals(PresetMenu.periodoId(p), Some("p0ds9qjq7b2"))
    assertEquals(
      PresetMenu.source(p.modify(_.authority).setTo(Some(HistoricalPeriod.Authority("Bryce 2005")))),
      Some("Bryce 2005")
    )
    assertEquals(
      PresetMenu.periodoId(p.modify(_.authority).setTo(Some(HistoricalPeriod.Authority("Bryce 2005")))),
      None
    )
