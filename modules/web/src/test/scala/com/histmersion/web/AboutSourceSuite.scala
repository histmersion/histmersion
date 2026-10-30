package com.histmersion.web

import com.histmersion.api.*

/** The About window offers everyone who uses the server the source of the running version (the AGPL's network clause):
  * the licence, and a link to the repository at the commit.
  */
class AboutSourceSuite extends munit.FunSuite:

  private def about(build: BuildStamp) =
    About("histmersion", build, Health("ok", "ok", Some("97"), Some("ok")), JobCounts(), None, None, None)

  private def serverRows(build: BuildStamp) =
    AboutView.sections(about(build)).find(_.title == "Server").get.rows

  test("the Server section names the licence and links the source at the running commit"):
    val url = "https://github.com/histmersion/histmersion/tree/abc123def456"
    val rows =
      serverRows(BuildStamp("1.2.3", Some("abc123def456"), licence = Some("AGPL-3.0-or-later"), sourceUrl = Some(url)))
    val licence = rows.find(_.label == "licence").get
    assertEquals((licence.value, licence.href), ("AGPL-3.0-or-later", None))
    val source = rows.find(_.label == "source").get
    assertEquals(source.href, Some(url))
    assertEquals(source.detail, Some(url))
    assertEquals(rows.map(_.label).take(5), List("version", "commit", "commit date", "licence", "source"))

  test("an older server, which reports neither, shows unknown and no link"):
    val rows = serverRows(BuildStamp("1.2.3"))
    assertEquals(rows.find(_.label == "licence").map(_.value), Some("unknown"))
    val source = rows.find(_.label == "source").get
    assertEquals((source.value, source.href), ("unknown", None))
