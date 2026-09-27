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
