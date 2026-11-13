package com.histmersion
package server

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.library.acquisition.OpenAlexDiscovery

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.acquisition.*
import com.histmersion.api.library.Acquire
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.pluginhost.upstreamSettings
import com.histmersion.pluginhost.HostLog
import com.histmersion.domain.library.Doi
import com.histmersion.upstreams.jats.Jats

/** ONE live call to the real upstreams, gated on `HISTMERSION_LIVE_APIS=1`.
  *
  * The fixture suites prove we parse what the APIs returned when they were captured; this proves the APIs still return
  * that shape TODAY, and that the polite-pool headers and URL assembly are accepted. It is opt-in because a test suite
  * must not depend on the internet:
  *
  * {{{HISTMERSION_LIVE_APIS=1 sbt "server/testOnly *AcquireLiveSuite"}}}
  */
class AcquireLiveSuite extends munit.FunSuite:

  override def munitIgnore: Boolean = sys.env.get("HISTMERSION_LIVE_APIS") != Some("1")
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private lazy val cfg = AppConfig.load().acquire

  private def loaded(text: String): LoadedConfig =
    AppConfig.loadWithUserText(org.ekrich.config.ConfigFactory.empty, text).fold(e => fail(e.sentence), identity)

  private lazy val http = SttpHttp(cfg, clock = () => java.time.Instant.now(java.time.Clock.systemUTC()))
  private lazy val openalex =
    OpenAlexDiscovery(
      OpenAlexCrossref(
        upstreamSettings(cfg),
        HttpPlugin(http),
        HostLog.scribe.plugin
      )
    )

  private val doi = Doi("10.1038/nature14317") // Haak et al. 2015, the corpus's own `haak2015_massive`

  test("OpenAlex resolves a known DOI to the metadata the corpus expects"):
    val w = openalex.meta(doi).fold(e => fail(e.sentence), identity)
    assertEquals(w.doi.map(_.value: String), Some(doi))
    assertEquals(w.year, Some(2015L))
    assertEquals(w.authors.headOption, Some("Wolfgang Haak"))
    assertEquals(w.citekey, Some("haak2015_massive"))
    assert(w.citedByCount.exists(_.value > 500), s"cited_by_count = ${w.citedByCount}")

  test("Crossref still deposits this paper's reference list"):
    val (backend, refs) = openalex.references(doi).fold(e => fail(e.sentence), identity)
    assertEquals(backend, "crossref")
    assert(refs.size > 10, s"only ${refs.size} references")
    assert(refs.exists(_.doi.isDefined))

  test("Crossref update notices are reachable (this paper has none)"):
    assertEquals(openalex.updates(doi), Right(Nil))

  test("a legal open-access PDF is still resolvable for it"):
    val f = OaFetch(loaded(""), http, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
      .fetch(Some(doi), None)
      .fold(e => fail(e.sentence), identity)
    assert(f.bytes.value.length > 10000, s"${f.bytes.value.length} bytes from ${f.source}")
    assert(new String(f.bytes.value.take(8), "ISO-8859-1").contains("%PDF"))
    assert(Set("unpaywall", "core", "openalex", "europepmc", "pmc", "arxiv").contains(f.source), f.source)

  // #1523: OpenAlex knows no PDF for these; PMC has one (the OA subset) or only the JATS text
  test("PMC: the dataset PDF of an open-access author manuscript (Mathieson 2018)"):
    val f = OaFetch(loaded(""), http, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
      .fetch(Some(Doi("10.1038/nature25778")), None)
      .fold(e => fail(e.sentence), identity)
    assertEquals((f.source, f.extension), ("pmc", "pdf"))
    assert(f.url.exists(_.value.contains("PMC6091220")), f.url.toString)

  // europepmc.org renders this manuscript's PDF when its bot protection lets the request through
  // (it did on 2026-10-11, and challenged curl an hour earlier); else the dataset's JATS is the copy
  test("PMC: Europe PMC's rendered PDF, or the JATS full text, of a manuscript with no dataset PDF (Damgaard 2018)"):
    val f = OaFetch(loaded(""), http, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
      .fetch(Some(Doi("10.1126/science.aar7711")), None)
      .fold(e => fail(e.sentence), identity)
    (f.source.value, f.extension.value) match
      case ("europepmc", "pdf") => assert(new String(f.bytes.value.take(8), "ISO-8859-1").contains("%PDF"))
      case ("pmc", "xml")       =>
        val md = Jats.toMarkdown(Jats.Xml(new String(f.bytes.value, "UTF-8")))
        assert(md.value.startsWith("# The First Horse Herders"), md.value.take(200))
      case other => fail(s"unexpected copy $other")

  test("Semantic Scholar returns citing papers with intents"):
    val work = openalex.meta(doi).fold(e => fail(e.sentence), identity)
    val (backend, rows) = SemanticScholar(cfg, http)
      .citing(work, None, CitationsClient.CitingLimit(10))
      .fold(e => fail(e.sentence), identity)
    assert(rows.nonEmpty, s"no citations from $backend")
    assert(
      rows.forall(r =>
        Acquire.EdgeKinds.all.contains(
          Acquire.classify(
            r.intents.map(i => Acquire.Intent.Wire(i.value)),
            r.contexts.map(c => Acquire.CitationContext(c.value)),
            r.isInfluential.value,
            r.year,
            work.year
          )
        )
      )
    )

  test("the Retraction Watch CSV downloads and parses"):
    val csv = RetractionWatch(cfg, http).csv().fold(e => fail(e.sentence), identity)
    val rows = Acquire.parseRetractionWatch(csv.value)
    assert(rows.size > 10000, s"only ${rows.size} rows — has the export moved?")
    assert(rows.forall(_.doi.value.startsWith("10.")))
