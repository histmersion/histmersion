package com.histmersion.server

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.server.acquire.CoreBudget
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** The CORE budget through a running server: kept in the database it opens (a restart reads it back), and shown by
  * `/api/about` once CORE was asked. Skipped without HISTMERSION_TEST_DB_URL.
  */
class CoreQuotaHttpSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String): String =
    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body

  test("the budget is kept in the server's database, read back by the next server, and shown by about"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    import com.augustnagro.magnum.*
    val clean = com.histmersion.db.Db.pool(cfg.db)
    try {
      val _ = com.histmersion.db.Migrate.run(clean);
      val _ = connect(clean)(sql"DELETE FROM upstream_quota WHERE upstream = 'core'".update.run())
    } finally clean.close()
    val now = AtomicReference(Instant.now())
    val limits = CoreBudget.limits(cfg.acquire.core)
    val first = CoreBudget(() => now.get, _ => ())
    supervised {
      val s = Composition.server(cfg, "127.0.0.1", 0, OfflineConverter(), core = first).start()
      try
        val url = s"http://127.0.0.1:${s.port}"
        assert(!get(s"$url/api/about").contains("\"upstreams\":[{"), "nothing shown before CORE was asked")
        val until = now.get.plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        assert(first.take(limits, CoreQuota.Purpose.Search).isRight)
        first.observe(
          limits,
          429,
          Map(
            "x-ratelimit-limit" -> "25",
            "x-ratelimit-remaining" -> "0",
            "x-ratelimit-retry-after" -> until.toString.replace("Z", "+0000")
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
      val s = Composition.server(cfg, "127.0.0.1", 0, OfflineConverter(), core = second).start()
      try
        assertEquals(second.state.searchTokens, 1)
        assert(second.unavailable(limits, CoreQuota.Purpose.Lookup).exists(_.isInstanceOf[CoreQuota.Refusal.Blocked]))
      finally s.stop()
    }
