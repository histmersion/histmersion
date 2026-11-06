package com.histmersion.config

import com.histmersion.config.testsupport.ComparedByValue.given

import scala.jdk.CollectionConverters.*

class ConfigNewtypesSuite extends munit.FunSuite:

  private def env(kv: (String, String)*): org.ekrich.config.Config =
    org.ekrich.config.ConfigFactory.parseMap(kv.toMap.asJava)

  test("a comma-separated setting's entries are trimmed, lower-cased and never empty"):
    assertEquals(CommaList(" Zenodo.org, ,FIGSHARE.com ,").entries, List("zenodo.org", "figshare.com"))
    assertEquals(CommaList("").entries, Nil)

  test("a typed setting reads the shipped value, and an environment override arrives as a string"):
    val shipped = AppConfig.load(env()).acquire.recheck
    val viaEnv = AppConfig
      .load(env("HISTMERSION_ACQUIRE_RECHECK_PER_PASS" -> "7", "HISTMERSION_ACQUIRE_RECHECK_SHARE_PERCENT" -> "15"))
      .acquire
      .recheck
    assertEquals(viaEnv.perPass.value: Int, 7)
    assertEquals(viaEnv.sharePercent.value: Int, 15)
    assertEquals(viaEnv.perDay.value: Int, shipped.perDay.value: Int)
