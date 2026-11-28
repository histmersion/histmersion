class BuildLedgerSitesSuite extends munit.FunSuite:

  test("the command runs scalafix-cli for its Scala 2.13 over every module, with each SemanticDB root"):
    val cmd = LedgerSitesPlan.command(Seq("/r/a/meta", "/r/b/test-meta"), "3.10.1", "2.13.18", "0.14.9")
    assertEquals(cmd.take(3), Seq("scala-cli", "run", "--server=false"))
    assert(cmd.containsSlice(Seq("-S", "2.13.18")))
    assert(cmd.containsSlice(Seq("--dep", "ch.epfl.scala:scalafix-cli_2.13.18:0.14.9")))
    assert(cmd.containsSlice(Seq("--config", LedgerSitesPlan.Config, "--scala-version", "3.10.1")))
    assert(
      cmd.containsSlice(Seq("--semanticdb-targetroots", "/r/a/meta", "--semanticdb-targetroots", "/r/b/test-meta"))
    )
    assertEquals(cmd.takeRight(2), Seq("--files", "modules"))

  test("the key changes with the sources' digest and with the versions, and only with them"):
    val k = LedgerSitesPlan.key("abc", "scala=3.10.1\n")
    assertEquals(k, LedgerSitesPlan.key("abc", "scala=3.10.1\n"))
    assertNotEquals(k, LedgerSitesPlan.key("abd", "scala=3.10.1\n"))
    assertNotEquals(k, LedgerSitesPlan.key("abc", "scala=3.10.2\n"))
    assert(k.matches("[0-9a-f]{64}"))

  test("the timeout is the environment's positive number of seconds, else 15 minutes"):
    assertEquals(LedgerSitesPlan.timeoutSeconds(Some(" 120 ")), 120L)
    assertEquals(LedgerSitesPlan.timeoutSeconds(None), 900L)
    assertEquals(LedgerSitesPlan.timeoutSeconds(Some("")), 900L)
    assertEquals(LedgerSitesPlan.timeoutSeconds(Some("0")), 900L)
    assertEquals(LedgerSitesPlan.timeoutSeconds(Some("-5")), 900L)
    assertEquals(LedgerSitesPlan.timeoutSeconds(Some("soon")), 900L)
