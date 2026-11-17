package com.histmersion
package server.agents
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.PlanQuota.*

import java.nio.file.Files
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.config.CodexConfig
import com.histmersion.config.AgentsConfig

/** Reading the plan pools without spending anything: Claude's `rate_limit_event`, a mod's report, and Codex's
  * `account/rateLimits/read` — the last through a STUB `codex app-server` answering the response captured on 2026-10-05
  * (docs/research-agent-runtimes.md §1.2), which also proves that nothing but initialize / initialized / the read is
  * ever sent (never a reset-credit consume).
  */
class PlanPoolsSuite extends munit.FunSuite:

  /** The read captured on 2026-10-05: the weekly Codex window spent, no 5-hour window on this plan, a second bucket for
    * a fallback model, ordinary usage not allowed, a reset credit on offer.
    */
  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  val Captured: String =
    """{"ordinaryUsageAllowed":false,
      | "rateLimits":{"limitId":"codex","limitName":null,"normalModelSlug":null,
      |   "primary":{"usedPercent":100,"windowDurationMins":10080,"resetsAt":1791625500},"secondary":null,
      |   "credits":null,"planType":"prolite","rateLimitReachedType":"rate_limit_reached"},
      | "rateLimitsByLimitId":{
      |   "codex":{"limitId":"codex","limitName":null,"normalModelSlug":null,
      |     "primary":{"usedPercent":100,"windowDurationMins":10080,"resetsAt":1791625500},"secondary":null,
      |     "credits":null,"planType":"prolite","rateLimitReachedType":"rate_limit_reached"},
      |   "base_model_inference":{"limitId":"gpt-reserve","limitName":"base_model_inference","normalModelSlug":"gpt-5.6-luna",
      |     "primary":{"usedPercent":0,"windowDurationMins":10080,"resetsAt":1791826560},"secondary":null,
      |     "credits":null,"planType":"prolite","rateLimitReachedType":null}},
      | "rateLimitResetCredits":{"availableCount":1,"credits":null},
      | "accountId":null,
      | "rateLimitUpsell":{"cta":"reset_usage"}}""".stripMargin

  test("codex: windows keyed by LENGTH and bucket; a reached limit refused; ordinaryUsageAllowed false is a refusal"):
    val (rs, refusal, plan) = PlanPools.codexReadings(RawJson.parse(Captured).toOption.get, Now(1000L))
    val got = rs.map(r => s"${r.limit} ${r.kind} ${r.usedPct} ${r.rejected} ${r.model.getOrElse("-")}").toSet
    assertEquals(got, Set("codex 10080m 100.0 true -", "gpt-reserve 10080m 0.0 false gpt-5.6-luna"))
    assertEquals(rs.find(_.limit.value == "codex").flatMap(_.resetsAt), Some(1791625500000L))
    assert(refusal.exists(_._1.sentence.contains("ordinaryUsageAllowed")))
    assertEquals(plan, Some("prolite"))

  test("codex: a plan whose primary is the 5-hour window and secondary the week"):
    val j = """{"rateLimits":{"limitId":"codex","primary":{"usedPercent":2,"windowDurationMins":300,"resetsAt":1790552145},
              |"secondary":{"usedPercent":0,"windowDurationMins":10080,"resetsAt":1791138940},"planType":"plus","rateLimitReachedType":null}}""".stripMargin
    val (rs, refusal, _) = PlanPools.codexReadings(RawJson.parse(j).toOption.get, Now(0L))
    assertEquals(rs.map(_.kind).toSet, Set("300m", "10080m"))
    assertEquals(refusal, None)

  test("the stub app-server: initialize, initialized, the read — and nothing else is ever sent"):
    val dir = Files.createTempDirectory("hm-appserver-")
    val log = dir.resolve("received")
    val resp = dir.resolve("resp.json")
    Files.writeString(resp, s"""{"jsonrpc":"2.0","id":2,"result":${Captured.replace("\n", "")}}""")
    val script = dir.resolve("codex")
    Files.writeString(
      script,
      s"""#!/bin/bash
         |[ "$$1" = "app-server" ] || exit 9
         |echo "CODEX_HOME=$$CODEX_HOME" > '$dir/env'
         |while IFS= read -r line; do
         |  echo "$$line" >> '$log'
         |  case "$$line" in
         |    *'"id":1'*) echo '{"jsonrpc":"2.0","id":1,"result":{"userAgent":"stub"}}' ;;
         |    *'account/rateLimits/read'*) cat '$resp'; echo ;;
         |  esac
         |done
         |""".stripMargin
    )
    script.toFile.setExecutable(true)
    val home = Files.createDirectories(dir.resolve("home"))
    val r = PlanPools.readCodex(CodexConfig.Command(script.toString), home, AgentsConfig.DoctorTimeoutSeconds(10))
    assert(r.isRight, r)
    assertEquals(r.toOption.get.cursor.downField("ordinaryUsageAllowed").as[Boolean], Right(false))
    val sent = Files.readString(log)
    assertEquals(sent.linesIterator.size, 3, sent)
    assert(!sent.contains("Credit") && !sent.contains("consume"), sent)
    assertEquals(Files.readString(dir.resolve("env")).trim, s"CODEX_HOME=$home", "the runs' own login is read")

  test("the stub app-server that never answers is a timeout, not a hang"):
    val dir = Files.createTempDirectory("hm-appserver2-")
    val script = dir.resolve("codex")
    Files.writeString(script, "#!/bin/bash\nsleep 30\n")
    script.toFile.setExecutable(true)
    val t0 = System.nanoTime()
    val r = PlanPools.readCodex(CodexConfig.Command(script.toString), dir, AgentsConfig.DoctorTimeoutSeconds(2))
    // the timeout names the process and how long it was given
    assertEquals(r.left.map(_.sentence), Left("codex app-server did not finish within 2000 ms"))
    assert((System.nanoTime() - t0) / 1000000000L < 15)

  test("a mod's report: percentUsed and the ISO reset"):
    val rs = PlanPools.modReadings(
      List(
        ModWindow(
          ModWindow.Kind("five_hour"),
          ModWindow.PercentUsed(12),
          Some(ModWindow.ResetsAt("2026-10-05T21:40:00.000Z"))
        ),
        ModWindow(ModWindow.Kind("seven_day"), ModWindow.PercentUsed(42), None)
      ),
      Source.Mod,
      Now(5L)
    )
    assertEquals(
      rs.map(r => (r.kind, r.usedPct, r.resetsAt, r.source)),
      List(
        ("five_hour", 12.0, Some(java.time.Instant.parse("2026-10-05T21:40:00Z").toEpochMilli), Source.Mod),
        ("seven_day", 42.0, None, Source.Mod)
      )
    )
