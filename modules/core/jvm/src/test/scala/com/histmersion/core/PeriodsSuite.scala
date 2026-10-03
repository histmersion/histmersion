package com.histmersion.core

import java.nio.file.{Files, Path}

/** The PeriodO reader (#719): pure, so every rule is pinned here — ISO years, outer bounds, English
  * labels, regions, unreadable spans, the parent rules and the parents-first order — plus the
  * committed seed itself, which is what a fresh install's timeline presets are made of. */
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

  private def parsed = PeriodO.parse(dump).fold(e => fail(e), identity)
  private def byId(ps: List[HistoricalPeriod]) = ps.map(p => p.id -> p).toMap

  test("ISO 8601 years count a year 0; signed years do not"):
    assertEquals(PeriodO.isoYear("-2999"), Some(-3000L)) // 3000 BC
    assertEquals(PeriodO.isoYear("0000"), Some(-1L))     // 1 BC
    assertEquals(PeriodO.isoYear("0001"), Some(1L))
    assertEquals(PeriodO.isoYear("0640"), Some(640L))
    assertEquals(PeriodO.isoYear("-349999"), Some(-350000L))
    assertEquals(PeriodO.isoYear("about 3000 BC"), None)

  test("a definition's span is its outer bounds, in signed years"):
    val d = parsed.authorities.flatMap(_.periods).map(p => p.id -> p).toMap
    assertEquals(d("p0aaa01").period, Period(-3200, -1050))
    assertEquals(d("p0aaa02").period, Period(-1700, -1050)) // earliest start, latest stop
    assertEquals(d("p0bbb01").period, Period(-3200, -1))    // a numeric year is read too

  test("labels are the authority's own in English, else its English alternative"):
    val d = parsed.authorities.flatMap(_.periods).map(p => p.id -> p).toMap
    assertEquals(d("p0aaa01").label, "Bronze Age") // ROAD lists "Late Stone Age" first for "Neolithic"
    assertEquals(d("p0bbb01").label, "Early Bronze Age")

  test("the region is the coverage description, else the coverage labels"):
    val d = parsed.authorities.flatMap(_.periods).map(p => p.id -> p).toMap
    assertEquals(d("p0bbb01").region, Some("Ukraine"))
    assertEquals(d("p0aaa02").region, Some("Crete"))
    assertEquals(d("p0aaa03").region, None)

  test("a definition without a readable span is skipped and named, never guessed"):
    val ds = parsed
    assert(!ds.authorities.flatMap(_.periods).exists(p => Set("p0aaa04", "p0aaa05")(p.id)))
    assertEquals(ds.skipped.size, 2, ds.skipped)
    assert(ds.skipped.exists(s => s.label == "Backwards" && s.reason == PeriodSkip.EndsBeforeStart), ds.skipped)
    assertEquals(ds.skipped.find(_.id == "p0aaa04").map(_.reason), Some(PeriodSkip.NoSpan))

  test("the authority citation names the PeriodO id, the source and its year"):
    val rows = byId(PeriodO.toPeriods(parsed))
    assertEquals(rows("p0aaa01").authority, Some("PeriodO p0aaa01 · Archaeological periods of Somewhere (2022)"))
    assertEquals(rows("p0bbb01").authority, Some("PeriodO p0bbb01 · FASTI (2004)"))

  test("PeriodO `broader` links inside the batch are kept; everything else is a root"):
    val rows = byId(PeriodO.toPeriods(parsed))
    assertEquals(rows("p0aaa02").parentId, Some("p0aaa01"))
    assertEquals(rows("p0aaa03").parentId, Some("p0aaa02"))
    assertEquals(rows("p0aaa01").parentId, None)
    assertEquals(rows("p0bbb01").parentId, None)

  test("a broader period outside the batch is dropped, not left dangling"):
    val rows = byId(PeriodO.toPeriods(PeriodO.parse(dump).toOption.get.copy(
      authorities = parsed.authorities.map(a => a.copy(periods = a.periods.filterNot(_.id == "p0aaa01"))))))
    assertEquals(rows("p0aaa02").parentId, None)

  test("the curated overlay wins over `broader`"):
    val rows = byId(PeriodO.toPeriods(parsed.copy(parents = Map("p0aaa03" -> "p0aaa01", "p0bbb01" -> "p0aaa01"))))
    assertEquals(rows("p0aaa03").parentId, Some("p0aaa01"))
    assertEquals(rows("p0bbb01").parentId, Some("p0aaa01"))

  test("with roots, a collection hangs under one node per authority spanning its periods"):
    val rows = PeriodO.toPeriods(parsed, roots = true)
    val m    = byId(rows)
    assertEquals(m("p0aaa").parentId, None)
    assertEquals(m("p0aaa").period, Period(-3200, -1050))
    assertEquals(m("p0aaa").label, "Archaeological periods of Somewhere")
    assertEquals(m("p0aaa01").parentId, Some("p0aaa"))
    assertEquals(m("p0aaa02").parentId, Some("p0aaa01")) // its own broader still wins
    assertEquals(m("p0bbb01").parentId, Some("p0bbb"))
    assertEquals(rows.count(_.parentId.isEmpty), 2)

  test("a period already in the store keeps its place — a preset is never buried by a fetch"):
    val existing = Map("p0aaa01" -> None, "p0bbb01" -> Some("p0aaa01"), "p0aaa03" -> Some("p0aaa01"))
    val m = byId(PeriodO.toPeriods(parsed, existing, roots = true))
    assertEquals(m("p0aaa01").parentId, None)            // was a preset, stays one
    assertEquals(m("p0bbb01").parentId, Some("p0aaa01")) // placed by the seed, stays there
    assertEquals(m("p0aaa03").parentId, Some("p0aaa01")) // over its own broader, too
    assertEquals(m("p0aaa02").parentId, Some("p0aaa01")) // new: its broader

  test("rows come parents first, and a cycle is cut rather than looped"):
    val rows = PeriodO.toPeriods(parsed, roots = true)
    rows.zipWithIndex.foreach { (r, i) =>
      r.parentId.foreach(p => assert(rows.indexWhere(_.id == p) < i, s"${r.id} before its parent $p"))
    }
    val a = HistoricalPeriod("a", "A", None, Period(1, 2), None, Some("b"))
    val b = HistoricalPeriod("b", "B", None, Period(1, 2), None, Some("a"))
    val out = PeriodO.parentsFirst(List(a, b))
    assertEquals(out.map(_.id).toSet, Set("a", "b"))
    assertEquals(out.count(_.parentId.isEmpty), 1)

  test("only() keeps the named authorities; nothing named keeps them all"):
    assertEquals(parsed.only(Set("p0bbb")).authorities.map(_.id), List("p0bbb"))
    assertEquals(parsed.only(Set("p0bbb")).skipped, Nil) // p0aaa's broken definitions are not p0bbb's
    assertEquals(parsed.only(Set("p0aaa")).skipped.size, 2)
    assertEquals(parsed.only(Set.empty).authorities.size, 2)

  // #771: EAMENA's "Old Kingdom" came in as -2701 → 2150 and was stored as it was
  private def authorityOf(periods: String) =
    s"""{"authorities": {"p0eam": {"id": "p0eam", "source": {"title": "EAMENA"}, "periods": {$periods}}}}"""
  private def defn(id: String, label: String, start: String, stop: String, extra: String = "") =
    s""""$id": {"id": "$id", "label": "$label", "spatialCoverageDescription": "Egypt",
       "start": {"in": {"year": "$start"}}, "stop": {"in": {"year": "$stop"}}$extra}"""

  test("a definition outside -3,000,000..2100 is skipped and reported, never clamped"):
    val ds = PeriodO.parse(authorityOf(List(
      defn("p0eam01", "Old Kingdom", "-2700", "2150"),
      defn("p0eam02", "Too early", "-3500000", "-3000000"),
      defn("p0eam03", "Middle Kingdom", "-2054", "-1649")).mkString(","))).toOption.get
    assertEquals(ds.authorities.flatMap(_.periods).map(_.id), List("p0eam03"))
    assertEquals(ds.skipped.map(s => s.id -> s.reason),
      List("p0eam01" -> PeriodSkip.OutOfRange, "p0eam02" -> PeriodSkip.OutOfRange))
    assert(ds.skipped.head.detail.contains("2150"), ds.skipped.head.detail)

  test("a definition far longer than every other its authority gives for the region is skipped"):
    val phases = (1 to 4).map(i => defn(s"p0eam1$i", s"Phase $i", s"${-3000 + i * 300}", s"${-2800 + i * 300}"))
    val ds = PeriodO.parse(authorityOf((phases :+ defn("p0eam20", "Old Kingdom", "-2700", "1950")).mkString(",")))
      .toOption.get
    assertEquals(ds.skipped.map(s => s.id -> s.reason), List("p0eam20" -> PeriodSkip.ImplausibleSpan))
    assertEquals(ds.authorities.flatMap(_.periods).size, 4)

  test("an umbrella (another definition's broader) and a sparse region are never judged implausible"):
    val phases = (1 to 4).map(i => defn(s"p0eam1$i", s"Phase $i", s"${-3000 + i * 300}", s"${-2800 + i * 300}",
      """, "broader": "p0eam20""""))
    val umbrella = PeriodO.parse(authorityOf((phases :+ defn("p0eam20", "Pharaonic", "-3100", "1950")).mkString(",")))
      .toOption.get
    assertEquals(umbrella.skipped, Nil)
    val sparse = PeriodO.parse(authorityOf(List(defn("p0eam01", "Short", "-1000", "-900"),
      defn("p0eam02", "Long", "-3000", "1900")).mkString(","))).toOption.get
    assertEquals(sparse.skipped, Nil)

  test("an import result counts every skipped definition and names them"):
    val r = PeriodImportResult(3, 0, 5, List(SkippedPeriod("p0x1", "Old Kingdom", PeriodSkip.OutOfRange,
      "-2701 → 2150 lies outside -3000000..2100")), skippedCount = 2)
    val md = PeriodsMarkdown.imported(r)
    assert(md.contains("skipped 2 definitions (not imported)"), md)
    assert(md.contains("`p0x1` Old Kingdom: -2701 → 2150 lies outside"), md)
    assert(md.contains("…and 1 more"), md)
    assert(PeriodsMarkdown.imported(PeriodImportResult(1, presets = 1)).startsWith("imported 1 period — 1 timeline preset"))

  test("what is not a PeriodO dataset is refused"):
    assert(PeriodO.parse("""{"type": "FeatureCollection"}""").isLeft)
    assert(PeriodO.parse("not json").isLeft)

  // ── the committed seed: the presets a fresh install gets ──────────────────────────────────────
  private lazy val seed =
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    PeriodO.parse(Files.readString(root.resolve("db/periods/periodo-seed.json"))).fold(e => fail(e), identity)

  test("the seed reads cleanly and every curated parent is in it"):
    // #771: the only definitions left out are EAMENA's two Palaeolithic periods that PeriodO dates
    // from 14,000,000 BC — a data error the import reports rather than "fixes"
    assertEquals(seed.skipped.map(s => s.id -> s.reason),
      List("p0m64tdgpvd" -> PeriodSkip.OutOfRange, "p0m64tdp282" -> PeriodSkip.OutOfRange))
    val ids = seed.authorities.flatMap(_.periods).map(_.id).toSet
    assert(ids.size > 150, ids.size.toString)
    val left = seed.skipped.map(_.id).toSet
    seed.parents.filterNot((c, _) => left(c)).foreach((c, p) => assert(ids(c) && ids(p), s"$c → $p"))

  test("the seed's presets are a few broad periods, Palaeolithic to the Middle Ages"):
    val rows    = PeriodO.toPeriods(seed)
    val presets = rows.filter(_.parentId.isEmpty).map(_.label)
    assert(presets.size <= 15, presets.toString)
    List("Paleolithic", "Mesolithic", "Neolithic", "Chalcolithic", "Bronze Age", "Iron Age", "Dynastic Egypt",
      "Antiquity", "Vedic Period").foreach(l =>
      assert(presets.contains(l), presets.toString))
    assert(presets.exists(_.startsWith("Middle Ages")), presets.toString)

  test("the Aegean Bronze Age is divided Helladic / Cycladic / Minoan, down to the phases"):
    val m = byId(PeriodO.toPeriods(seed))
    def label(id: String) = m(id).label
    def chain(id: String): List[String] = m.get(id).toList.flatMap(p => p.label :: p.parentId.toList.flatMap(chain))
    // Late Minoan IB → Late Minoan I → Late Minoan → Bronze Age Minoan → Bronze Age (Greece) → Bronze Age
    assertEquals(chain("p0ds9qjh4dh").take(4), List("Late Minoan", "Bronze Age Minoan", "Bronze Age", "Bronze Age"))
    assertEquals(m("p0ds9qj9pc2").parentId, Some("p0ds9qjq7b2")) // Bronze Age Helladic under the BSA's Bronze Age
    assert(m.values.exists(p => p.label == "Early Helladic" && p.parentId.contains("p0ds9qj9pc2")))
    assert(m.values.exists(p => p.label == "Middle Minoan" && p.parentId.contains("p0ds9qj9kdj")))
    assertEquals(label("p0ds9qjq7b2"), "Bronze Age")
    assertEquals(m("p0ds9qjq7b2").region, Some("Greece"))
    // the BSA dates Late Minoan from the earliest start to the latest end it gives
    assertEquals(m("p0ds9qjh4dh").period, Period(-1700, -1050))
