package histdb.server

import histdb.core.*
import histdb.server.acquire.*

/** ONE live call to the real upstreams, gated on `HISTDB_LIVE_APIS=1`.
  *
  * The fixture suites prove we parse what the APIs returned when they were captured; this proves the
  * APIs still return that shape TODAY, and that the polite-pool headers and URL assembly are accepted.
  * It is opt-in because a test suite must not depend on the internet:
  *
  * {{{ HISTDB_LIVE_APIS=1 sbt "server/testOnly *AcquireLiveSuite" }}}
  */
class AcquireLiveSuite extends munit.FunSuite:

  override def munitIgnore: Boolean = sys.env.get("HISTDB_LIVE_APIS") != Some("1")
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private lazy val cfg      = AppConfig.load().acquire
  private lazy val http     = SttpHttp(cfg)
  private lazy val openalex = OpenAlexCrossref(cfg, http)

  private val doi = "10.1038/nature14317" // Haak et al. 2015, the corpus's own `haak2015_massive`

  test("OpenAlex resolves a known DOI to the metadata the corpus expects"):
    val w = openalex.meta(doi).fold(fail(_), identity)
    assertEquals(w.doi, Some(doi))
    assertEquals(w.year, Some(2015))
    assertEquals(w.authors.headOption, Some("Wolfgang Haak"))
    assertEquals(w.citekey, Some("haak2015_massive"))
    assert(w.citedByCount.exists(_ > 500), s"cited_by_count = ${w.citedByCount}")

  test("Crossref still deposits this paper's reference list"):
    val (backend, refs) = openalex.references(doi).fold(fail(_), identity)
    assertEquals(backend, "crossref")
    assert(refs.size > 10, s"only ${refs.size} references")
    assert(refs.exists(_.doi.isDefined))

  test("Crossref update notices are reachable (this paper has none)"):
    assertEquals(openalex.updates(doi), Right(Nil))

  test("a legal open-access PDF is still resolvable for it"):
    val f = OaFetch(cfg, http, openalex).fetch(Some(doi), None).fold(fail(_), identity)
    assert(f.bytes.length > 10000, s"${f.bytes.length} bytes from ${f.source}")
    assert(new String(f.bytes.take(8), "ISO-8859-1").contains("%PDF"))
    assert(Set("unpaywall", "core", "openalex", "arxiv").contains(f.source), f.source)

  test("Semantic Scholar returns citing papers with intents"):
    val work = openalex.meta(doi).fold(fail(_), identity)
    val (backend, rows) = SemanticScholar(cfg, http, openalex).citing(work, None, 10)
      .fold(fail(_), identity)
    assert(rows.nonEmpty, s"no citations from $backend")
    assert(rows.forall(r => Acquire.EdgeKinds.all.contains(
      Acquire.classify(r.intents, r.contexts, r.isInfluential, r.year, work.year))))

  test("the Retraction Watch CSV downloads and parses"):
    val csv  = RetractionWatch(cfg, http).csv().fold(fail(_), identity)
    val rows = Acquire.parseRetractionWatch(csv)
    assert(rows.size > 10000, s"only ${rows.size} rows — has the export moved?")
    assert(rows.forall(_.doi.startsWith("10.")))
