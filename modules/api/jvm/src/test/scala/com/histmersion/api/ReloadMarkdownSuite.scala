package com.histmersion.api

class ReloadMarkdownSuite extends munit.FunSuite:

  test("ReloadMarkdown: failed, unchanged and published reports"):
    val failed = ReloadMarkdown.report(ReloadReport(false, 3, errors = List("pool: connection refused")))
    assert(failed.contains("failed — generation 3 stays") && failed.contains("connection refused"), failed)
    assert(ReloadMarkdown.report(ReloadReport(false, 3)).contains("Nothing in the config differs"))
    val ok = ReloadMarkdown.report(ReloadReport(true, 4, applied = List("histmersion.db.url"),
      restartOnly = List("histmersion.server.port"), rebuilt = List("pool", "migrations"), plugins = List("pmc")))
    assert(ok.contains("generation 4") && ok.contains("- histmersion.db.url") && ok.contains("after a restart")
      && ok.contains("- migrations") && ok.contains("- pmc"), ok)
