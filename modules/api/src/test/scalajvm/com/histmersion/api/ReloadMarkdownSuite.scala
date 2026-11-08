package com.histmersion
package api

import com.histmersion.api.settings.{ReloadMarkdown, ReloadReport}
class ReloadMarkdownSuite extends munit.FunSuite:

  test("ReloadMarkdown: failed, unchanged and published reports"):
    val failed = ReloadMarkdown.report(ReloadReport(ReloadReport.Published(false), ReloadReport.Generation(3), errors = List(ReloadReport.Errors("pool: connection refused"))))
    assert(failed.contains("failed — generation 3 stays") && failed.contains("connection refused"), failed)
    assert(ReloadMarkdown.report(ReloadReport(ReloadReport.Published(false), ReloadReport.Generation(3))).contains("Nothing in the config differs"))
    val ok = ReloadMarkdown.report(
      ReloadReport(
        ReloadReport.Published(true),
        ReloadReport.Generation(4),
        applied = List(ReloadReport.Applied("histmersion.db.url")),
        restartOnly = List(ReloadReport.RestartOnly("histmersion.server.port")),
        rebuilt = List(ReloadReport.Rebuilt("pool"), ReloadReport.Rebuilt("migrations")),
        plugins = List(ReloadReport.Plugins("pmc"))
      )
    )
    assert(
      ok.contains("generation 4") && ok.contains("- histmersion.db.url") && ok.contains("after a restart")
        && ok.contains("- migrations") && ok.contains("- pmc"),
      ok
    )
