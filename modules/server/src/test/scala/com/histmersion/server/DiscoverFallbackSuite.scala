package com.histmersion.server

import com.histmersion.core.AppConfig
import com.histmersion.server.acquire.*
import io.circe.Json

/** OpenAlex pauses anonymous search under heavy load (HTTP 503, 2026-09-27): discovery must not go
  * dark with it. The search falls back to Crossref's keyless `works?query=`. */
class DiscoverFallbackSuite extends munit.FunSuite:

  private val cfg = AppConfig.load().acquire
  private val crossrefPage =
    io.circe.parser.parse(scala.io.Source.fromResource("acquire/crossref_search.json").mkString).toOption.get

  /** OpenAlex answers 503; Crossref answers the captured page. Records what was asked. */
  private final class OpenAlexDown extends Http:
    var urls: List[String] = Nil // a test double is the one place a var is the simplest thing
    def json(url: String, headers: Map[String, String]): Either[String, Json] =
      urls = urls :+ url
      if url.startsWith(cfg.crossref.url) then Right(crossrefPage) else Left(s"$url failed after 4 attempts: HTTP 503")
    def text(url: String, headers: Map[String, String]) = Left("unused")
    def bytes(url: String, headers: Map[String, String]) = Left("unused")

  test("when OpenAlex search is down, Crossref answers with the same kinds of work, from the same year"):
    val http = OpenAlexDown()
    val r    = OpenAlexCrossref(cfg, http).search("Mycenaean religion", Some(2023), None, 5)
    assertEquals(r.map(_.size), Right(3))
    val asked = http.urls.find(_.startsWith(cfg.crossref.url)).getOrElse(fail("Crossref was not asked"))
    assert(asked.contains("from-pub-date%3A2023-01-01") || asked.contains("from-pub-date:2023-01-01"), asked)
    assert(asked.contains("query=Mycenaean"), asked)

  test("both down: the error names both upstreams"):
    val dead = new Http:
      def json(url: String, headers: Map[String, String]) = Left(s"$url: HTTP 503")
      def text(url: String, headers: Map[String, String]) = Left("unused")
      def bytes(url: String, headers: Map[String, String]) = Left("unused")
    val r = OpenAlexCrossref(cfg, dead).search("x", None, None, 5)
    assert(r.left.exists(e => e.contains("openalex") && e.contains("Crossref")), r.toString)

  // ── meta (#599): a gold-OA work read "no OA copy known" whenever OpenAlex's singleton lookup
  // failed and the record came from Crossref, which carries no access information at all ──
  private val crossrefWork =
    io.circe.parser.parse("""{"status": "ok", "message": {"DOI": "10.1038/s41598-022-07284-3",
      "title": ["New insights on commemoration of the dead"], "type": "journal-article",
      "container-title": ["Scientific Reports"], "author": [{"given": "Eline", "family": "Schotsmans"}],
      "issued": {"date-parts": [[2022]]}}}""").toOption.get
  private val unpaywallGold =
    io.circe.parser.parse("""{"doi": "10.1038/s41598-022-07284-3", "is_oa": true, "oa_status": "gold",
      "best_oa_location": {"url": "https://www.nature.com/articles/s41598-022-07284-3",
        "url_for_pdf": "https://www.nature.com/articles/s41598-022-07284-3.pdf"}}""").toOption.get

  /** OpenAlex answers 503 (rate-limited); Crossref and Unpaywall answer. */
  private final class OpenAlexBusy(c: com.histmersion.core.AcquireConfig) extends Http:
    var urls: List[String] = Nil // a test double is the one place a var is the simplest thing
    def json(url: String, headers: Map[String, String]): Either[String, Json] =
      urls = urls :+ url
      if url.startsWith(c.crossref.url) then Right(crossrefWork)
      else if url.startsWith(c.unpaywall.url) then Right(unpaywallGold)
      else Left(s"$url failed after 4 attempts: HTTP 503")
    def text(url: String, headers: Map[String, String]) = Left("unused")
    def bytes(url: String, headers: Map[String, String]) = Left("unused")

  test("meta through the Crossref fallback still knows a gold-OA work is open access"):
    val withContact = cfg.copy(contactEmail = "librarian@example.org")
    val http        = OpenAlexBusy(withContact)
    val w = OpenAlexCrossref(withContact, http).meta("10.1038/s41598-022-07284-3").fold(e => fail(e), identity)
    assertEquals(w.venue, Some("Scientific Reports"))
    assert(w.isOa, w.toString)
    assertEquals(w.oaStatus, Some("gold"))
    assertEquals(w.oaUrl, Some("https://www.nature.com/articles/s41598-022-07284-3.pdf"))
    assert(http.urls.exists(_.startsWith(withContact.unpaywall.url)), http.urls.toString)

  test("meta through the Crossref fallback without a contact leaves the access UNKNOWN, not closed"):
    val noContact = cfg.copy(contactEmail = "")
    val http      = OpenAlexBusy(noContact)
    val w = OpenAlexCrossref(noContact, http).meta("10.1038/s41598-022-07284-3").fold(e => fail(e), identity)
    assert(!w.isOa)
    assertEquals(w.oaStatus, None) // rendered as "OA status unknown", never "no OA copy known"
    assert(!http.urls.exists(_.startsWith(noContact.unpaywall.url)), "Unpaywall refuses a request without an e-mail")
