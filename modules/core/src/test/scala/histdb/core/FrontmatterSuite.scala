package histdb.core

class FrontmatterSuite extends munit.FunSuite:
  val note =
    """---
      |citekey: heggarty2023_language
      |title: Language trees
      |authors: [Paul Heggarty, Cormac Anderson]
      |year: 2023
      |venue: null
      |status: current
      |topics: [indo-european, refutes_anthony2007]
      |regions: [Anatolia]
      |tags: [phylogeny]
      |period_start: -6000
      |period_end: -3000
      |superseded_by: null
      |findings:
      |  - claim: Hybrid origin
      |    region: Caucasus
      |    lat: 42.5
      |    lon: 44.0
      |    period_start: -6000
      |    period_end: -5000
      |    tags: [origin]
      |    confidence: medium
      |  - claim: undated one
      |    lat: null
      |---
      |Body citing [[anthony2007_horse|Anthony]] and [[bouckaert2012_mapping]].
      |""".stripMargin

  test("parses frontmatter with null values safely"):
    val s = Frontmatter.parseNote(note, "fallback").get
    assertEquals(s.citekey, "heggarty2023_language")
    assertEquals(s.venue, None)
    assertEquals(s.year, Some(2023))
    assertEquals(s.period, Some(Period(-6000, -3000)))
    assertEquals(s.status, SourceStatus.Current)

  test("findings: geo + period, and null geo → None"):
    val fs = Frontmatter.parseNote(note, "x").get.findings
    assertEquals(fs.map(_.id), List("heggarty2023_language#0", "heggarty2023_language#1"))
    assertEquals(fs.head.geo, Some(GeoPoint(44.0, 42.5)))
    assertEquals(fs(1).geo, None)
    assertEquals(fs(1).period, None)

  test("edges: relation tags and wikilinks (aliased or not), never self"):
    val es = Frontmatter.parseNote(note, "x").get.edges.toSet
    assertEquals(es, Set(
      Edge("heggarty2023_language", "anthony2007", "refutes"),
      Edge("heggarty2023_language", "anthony2007_horse", "references"),
      Edge("heggarty2023_language", "bouckaert2012_mapping", "references")))

  test("no frontmatter → None; fallback citekey from filename"):
    assertEquals(Frontmatter.parseNote("just text", "x"), None)
    assertEquals(Frontmatter.parseNote("---\ntitle: T\n---\n", "fromfile").get.citekey, "fromfile")
