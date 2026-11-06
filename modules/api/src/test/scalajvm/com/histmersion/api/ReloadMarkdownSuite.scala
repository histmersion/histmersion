package com.histmersion.api

import com.histmersion.api.settings.{ReloadMarkdown, ReloadReport}
class ReloadMarkdownSuite extends munit.FunSuite:

  test("ReloadMarkdown: failed, unchanged and published reports"):
    val failed = ReloadMarkdown.report(ReloadReport(com.histmersion.api.settings.ReloadReport.Published(false), com.histmersion.api.settings.ReloadReport.Generation(3), errors = List("pool: connection refused")))
    assert(failed.contains("failed — generation 3 stays") && failed.contains("connection refused"), failed)
    assert(ReloadMarkdown.report(ReloadReport(com.histmersion.api.settings.ReloadReport.Published(false), com.histmersion.api.settings.ReloadReport.Generation(3))).contains("Nothing in the config differs"))
    val ok = ReloadMarkdown.report(
      ReloadReport(
        com.histmersion.api.settings.ReloadReport.Published(true),
        com.histmersion.api.settings.ReloadReport.Generation(4),
        applied = List(com.histmersion.api.settings.ReloadReport.Applied("histmersion.db.url")),
        restartOnly = List(com.histmersion.api.settings.ReloadReport.RestartOnly("histmersion.server.port")),
        rebuilt = List(com.histmersion.api.settings.ReloadReport.Rebuilt("pool"), com.histmersion.api.settings.ReloadReport.Rebuilt("migrations")),
        plugins = List(com.histmersion.api.settings.ReloadReport.Plugins("pmc"))
      )
    )
    assert(
      ok.contains("generation 4") && ok.contains("- histmersion.db.url") && ok.contains("after a restart")
        && ok.contains("- migrations") && ok.contains("- pmc"),
      ok
    )
