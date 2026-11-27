package com.histmersion
package server
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{External, Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.acquisition.OpenAlexDiscovery

import com.histmersion.config.AppConfig
import com.histmersion.domain.library.acquisition.*
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.HttpAnswer
import com.histmersion.config.AcquireConfig
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.pluginhost.upstreamSettings
import com.histmersion.pluginhost.HostLog
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{tolerated, LintRule}

/** OpenAlex pauses anonymous search under heavy load (HTTP 503, 2026-09-27): discovery must not go dark with it. The
  * search falls back to Crossref's keyless `works?query=`.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class DiscoverFallbackSuite extends munit.FunSuite:

  private def discovery(c: AcquireConfig, http: Http): OpenAlexDiscovery =
    OpenAlexDiscovery(
      OpenAlexCrossref(
        upstreamSettings(c),
        HttpPlugin(http),
        HostLog.scribe.plugin
      )
    )

  private val cfg = AppConfig.load().acquire
  private val crossrefPage =
    RawJson.parse(scala.io.Source.fromResource("acquire/crossref_search.json").mkString).toOption.get

  /** OpenAlex answers 503; Crossref answers the captured page. Records what was asked. */
  final private class OpenAlexDown extends Http:
    var urls: List[Http.Url] = Nil // a test double is the one place a var is the simplest thing
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Problem, Json] =
      urls = urls :+ url
      if url.value.startsWith(cfg.crossref.url.value) then Right(crossrefPage)
      else
        Left(
          Problem.Upstream.Http.here(
            Problem.Upstream.Name("discovery"),
            Problem.Upstream.Url(s"$url"),
            Problem.Upstream.Status(503),
            External.ReplyExcerpt("down")
          )
        )
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) = Left(
      Problem.Unclassified(Legacy.Text("unused"))
    )
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
      json(url, headers).map(j => HttpAnswer.Body(j.noSpaces.getBytes("UTF-8")))

  test("when OpenAlex search is down, Crossref answers with the same kinds of work, from the same year"):
    val http = OpenAlexDown()
    val r = discovery(cfg, http).search(
      Search.Query("Mycenaean religion"),
      Some(Year(2023)),
      None,
      DiscoveryClient.SearchLimit(5)
    )
    assertEquals(r.map(_.size), Right(3))
    val asked =
      http.urls.find(_.value.startsWith(cfg.crossref.url.value)).getOrElse(fail("Crossref was not asked")).value
    assert(asked.contains("from-pub-date%3A2023-01-01") || asked.contains("from-pub-date:2023-01-01"), asked)
    assert(asked.contains("query=Mycenaean"), asked)

  test("both down: the error names both upstreams"):
    val dead = new Http:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
      def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
        Left(Problem.Unclassified(Legacy.Text(s"$url: HTTP 503")))
      @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
      def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
        Left(Problem.Unclassified(Legacy.Text("unused")))
      def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
        json(url, headers).map(j => HttpAnswer.Body(j.noSpaces.getBytes("UTF-8")))
    val r = discovery(cfg, dead).search(Search.Query("x"), None, None, DiscoveryClient.SearchLimit(5))
    assert(
      r.left.exists(e => e.sentences.exists(_.contains("openalex")) && e.sentences.exists(_.contains("crossref"))),
      r.toString
    )

  // ── meta (#599): a gold-OA work read "no OA copy known" whenever OpenAlex's singleton lookup
  // failed and the record came from Crossref, which carries no access information at all ──
  private val crossrefWork =
    RawJson
      .parse("""{"status": "ok", "message": {"DOI": "10.1038/s41598-022-07284-3",
      "title": ["New insights on commemoration of the dead"], "type": "journal-article",
      "container-title": ["Scientific Reports"], "author": [{"given": "Eline", "family": "Schotsmans"}],
      "issued": {"date-parts": [[2022]]}}}""")
      .toOption
      .get
  private val unpaywallGold =
    RawJson
      .parse("""{"doi": "10.1038/s41598-022-07284-3", "is_oa": true, "oa_status": "gold",
      "best_oa_location": {"url": "https://www.nature.com/articles/s41598-022-07284-3",
        "url_for_pdf": "https://www.nature.com/articles/s41598-022-07284-3.pdf"}}""")
      .toOption
      .get

  /** OpenAlex answers 503 (rate-limited); Crossref and Unpaywall answer. */
  final private class OpenAlexBusy(c: AcquireConfig) extends Http:
    var urls: List[Http.Url] = Nil // a test double is the one place a var is the simplest thing
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Problem, Json] =
      urls = urls :+ url
      if url.value.startsWith(c.crossref.url.value) then Right(crossrefWork)
      else if url.value.startsWith(c.unpaywall.url.value) then Right(unpaywallGold)
      else
        Left(
          Problem.Upstream.Http.here(
            Problem.Upstream.Name("discovery"),
            Problem.Upstream.Url(s"$url"),
            Problem.Upstream.Status(503),
            External.ReplyExcerpt("down")
          )
        )
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) = Left(
      Problem.Unclassified(Legacy.Text("unused"))
    )
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
      json(url, headers).map(j => HttpAnswer.Body(j.noSpaces.getBytes("UTF-8")))

  test("meta through the Crossref fallback still knows a gold-OA work is open access"):
    val withContact = cfg.modify(_.contactEmail).setTo(AcquireConfig.ContactEmail("librarian@example.org"))
    val http = OpenAlexBusy(withContact)
    val w = discovery(withContact, http).meta(Doi("10.1038/s41598-022-07284-3")).fold(e => fail(e.sentence), identity)
    assertEquals(w.venue, Some("Scientific Reports"))
    assert(w.isOa.value, w.toString)
    assertEquals(w.oaStatus, Some("gold"))
    assertEquals(w.oaUrl, Some("https://www.nature.com/articles/s41598-022-07284-3.pdf"))
    assert(http.urls.exists(_.value.startsWith(withContact.unpaywall.url.value)), http.urls.toString)

  test("meta through the Crossref fallback without a contact leaves the access UNKNOWN, not closed"):
    val noContact = cfg.modify(_.contactEmail).setTo(AcquireConfig.ContactEmail(""))
    val http = OpenAlexBusy(noContact)
    val w = discovery(noContact, http).meta(Doi("10.1038/s41598-022-07284-3")).fold(e => fail(e.sentence), identity)
    assert(!w.isOa.value)
    assertEquals(w.oaStatus, None) // rendered as "OA status unknown", never "no OA copy known"
    assert(
      !http.urls.exists(_.value.startsWith(noContact.unpaywall.url.value)),
      "Unpaywall refuses a request without an e-mail"
    )
