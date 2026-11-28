package com.histmersion
package server

import com.histmersion.domain.corequota.Purpose
import com.histmersion.domain.corequota.Refusal

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.library.acquisition.CoreBudget
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.database.Db
import com.histmersion.util.database.Migrate
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The CORE budget through a running server: kept in the database it opens (a restart reads it back), and shown by
  * `/api/about` once CORE was asked. Skipped without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CoreQuotaHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String): String =
    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body

  test("the budget is kept in the server's database, read back by the next server, and shown by about"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    import com.augustnagro.magnum.*
    val clean = Db.pool(cfg.db)
    try {
      val _ = Migrate.run(clean);
      val _ = connect(clean)(sql"DELETE FROM upstream_quota WHERE upstream = 'core'".update.run())
    } finally clean.close()
    val now = AtomicReference(Instant.now())
    val limits = CoreBudget.limits(cfg.acquire.core)
    val first = CoreBudget(() => now.get, _ => ())
    supervised {
      val s = Composition
        .server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), OfflineConverter(), core = Some(first))
        .start()
      try
        val url = s"http://127.0.0.1:${s.port}"
        assert(!get(s"$url/api/about").contains("\"upstreams\":[{"), "nothing shown before CORE was asked")
        val until = now.get.plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        assert(first.take(limits, Purpose.Search).isRight)
        first.observe(
          limits,
          CoreQuota.StatusCode(429),
          Map(
            CoreQuota.HeaderName("x-ratelimit-limit") -> CoreQuota.HeaderValue("25"),
            CoreQuota.HeaderName("x-ratelimit-remaining") -> CoreQuota.HeaderValue("0"),
            CoreQuota.HeaderName("x-ratelimit-retry-after") -> CoreQuota.HeaderValue(
              until.toString.replace("Z", "+0000")
            )
          )
        )
        val about = get(s"$url/api/about")
        assert(about.contains("\"upstream\":\"core\"") && about.contains("\"searchTokens\":1"), about)
        assert(about.contains("\"blockedWhy\":\"HTTP 429\""), about)
      finally s.stop()
    }
    // a restart: a fresh budget, the same database
    val second = CoreBudget(() => now.get, _ => ())
    supervised {
      val s = Composition
        .server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), OfflineConverter(), core = Some(second))
        .start()
      try
        assertEquals(second.state.searchTokens, 1)
        assert(second.unavailable(limits, Purpose.Lookup).exists(_.isInstanceOf[Refusal.Blocked]))
      finally s.stop()
    }
