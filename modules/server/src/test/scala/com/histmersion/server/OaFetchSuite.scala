package com.histmersion.server

import com.histmersion.config.AppConfig
import com.histmersion.server.acquire.agent
import com.histmersion.server.acquire.*
import io.circe.Json

/** The contact e-mail is a per-install setting: the repo's default is blank. Blank must mean
  * "anonymous" — no `(mailto:)` stub in the User-Agent, and no Unpaywall call, which Unpaywall
  * would refuse without an e-mail — while a configured one reaches every upstream as before. */
class OaFetchSuite extends munit.FunSuite:

  /** Answers nothing, records every URL asked for. */
  private final class RecordingHttp extends Http:
    var urls: List[String] = Nil // a test double is the one place a var is the simplest thing
    private def miss(url: String) = { urls = urls :+ url; Left(s"$url: offline") }
    def json(url: String, headers: Map[String, String]): Either[String, Json]          = miss(url)
    def text(url: String, headers: Map[String, String]): Either[String, String]        = miss(url)
    def bytes(url: String, headers: Map[String, String]): Either[String, Array[Byte]]  = miss(url)

  private def acquire(email: String) = AppConfig.load().acquire.copy(contactEmail = email)

  private def asked(email: String): List[String] =
    val cfg  = acquire(email)
    val http = RecordingHttp()
    val _    = OaFetch(cfg, http, OpenAlexCrossref(cfg, http)).fetch(Some("10.1038/nature14317"), None)
    http.urls

  test("the repository default has no contact e-mail"):
    assertEquals(AppConfig.load().acquire.contact, None)

  test("without a contact: no mailto stub, no Unpaywall, and the other sources are still tried"):
    val cfg = acquire("  ")
    assertEquals(cfg.contact, None)
    assert(!cfg.agent.contains("mailto"), cfg.agent)
    val urls = asked("")
    assert(!urls.exists(_.startsWith(cfg.unpaywall.url)), urls.mkString("\n"))
    assert(urls.nonEmpty, "the fetch must still try the keyless sources")
    assert(!urls.exists(_.contains("mailto=")), urls.mkString("\n"))

  test("with a contact: it is a parameter where the API defines one, never in the User-Agent (#1946)"):
    val cfg = acquire("someone@example.org")
    assertEquals(cfg.agent, cfg.userAgent)
    assert(!cfg.agent.contains("someone"), cfg.agent)
    val urls = asked("someone@example.org")
    assert(urls.exists(u => u.startsWith(cfg.unpaywall.url) && u.contains("email=someone@example.org")),
      urls.mkString("\n"))
