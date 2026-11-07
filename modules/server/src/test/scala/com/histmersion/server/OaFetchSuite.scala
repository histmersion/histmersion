package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.{AcquireConfig, AppConfig}
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.HttpAnswer
import com.histmersion.domain.library.acquisition.agent
import com.histmersion.domain.library.acquisition.*
import io.circe.Json

/** The contact e-mail is a per-install setting: the repo's default is blank. Blank must mean "anonymous" — no
  * `(mailto:)` stub in the User-Agent, and no Unpaywall call, which Unpaywall would refuse without an e-mail — while a
  * configured one reaches every upstream as before.
  */
class OaFetchSuite extends munit.FunSuite:

  /** Answers nothing, records every URL asked for. */
  final private class RecordingHttp extends Http:
    var urls: List[Http.Url] = Nil // a test double is the one place a var is the simplest thing
    private def miss(url: Http.Url) = { urls = urls :+ url; Left(Http.Problem(s"$url: offline")) }
    def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Json] = miss(url)
    def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, Http.Text] = miss(url)
    def bytes(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Http.Problem, HttpAnswer.Body] = miss(url)

  private def acquire(email: AcquireConfig.ContactEmail) = AppConfig.load().acquire.copy(contactEmail = email)

  private def loaded(text: String): com.histmersion.config.LoadedConfig =
    AppConfig.loadWithUserText(org.ekrich.config.ConfigFactory.empty, text).fold(e => fail(e.value), identity)

  private def asked(email: AcquireConfig.ContactEmail): List[Http.Url] =
    val cfg = acquire(email)
    val http = RecordingHttp()
    val _ =
      OaFetch(loaded(s"histmersion.acquire.contact-email = \"$email\""), http, core = com.histmersion.domain.library.acquisition.CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC()))).fetch(Some(com.histmersion.domain.library.Doi("10.1038/nature14317")), None)
    http.urls

  test("the repository default has no contact e-mail"):
    assertEquals(AppConfig.load().acquire.contact, None)

  test("without a contact: no mailto stub, no Unpaywall, and the other sources are still tried"):
    val cfg = acquire(AcquireConfig.ContactEmail("  "))
    assertEquals(cfg.contact, None)
    assert(!cfg.agent.contains("mailto"), cfg.agent)
    val urls = asked(AcquireConfig.ContactEmail(""))
    assert(!urls.exists(_.value.startsWith(cfg.unpaywall.url.value)), urls.mkString("\n"))
    assert(urls.nonEmpty, "the fetch must still try the keyless sources")
    assert(!urls.exists(_.value.contains("mailto=")), urls.mkString("\n"))

  test("with a contact: it is a parameter where the API defines one, never in the User-Agent (#1946)"):
    val cfg = acquire(AcquireConfig.ContactEmail("someone@example.org"))
    assertEquals(cfg.agent, cfg.userAgent)
    assert(!cfg.agent.contains("someone"), cfg.agent)
    val urls = asked(AcquireConfig.ContactEmail("someone@example.org"))
    assert(
      urls.exists(u => u.value.startsWith(cfg.unpaywall.url.value) && u.value.contains("email=someone@example.org")),
      urls.mkString("\n")
    )
