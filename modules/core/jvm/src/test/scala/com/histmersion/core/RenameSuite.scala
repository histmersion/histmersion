package com.histmersion.core

/** The pure half of a citekey rename: which new keys are acceptable, which short forms pointed at the
  * old key, how finding ids and composite ids move, and the link rewrite that must change the link
  * target and nothing else. */
class RenameSuite extends munit.FunSuite:

  test("validate: a valid, link-shaped, different key passes (trimmed)"):
    assertEquals(Rename.validate("page2024_history", " page1959_history ").map(ck => ck.value: String),
      Right("page1959_history"))
    assertEquals(Rename.validate("x2020_a", "knapp2016").map(ck => ck.value: String), Right("knapp2016"))

  test("validate: the same key, an invalid key and a key the link resolver would not take are refused"):
    def refused(to: String, says: String) =
      Rename.validate("page2024_history", to) match
        case Left(e)  =>
          assertEquals(e.code, Some(Write.CodeInvalid))
          assert(e.message.contains(says), e.message)
        case Right(k) => fail(s"$to should be refused, got $k")
    refused("page2024_history", "already the citekey")
    refused("Page 1959", "invalid citekey")
    refused("../etc", "invalid citekey")
    refused("page-1959", "LINK-shaped")     // a valid citekey, but [[page-1959]] is an article link
    refused("page1959.v2", "LINK-shaped")
    refused("nodigits", "LINK-shaped")

  test("shortForms: every prefix that ends right before an underscore"):
    assertEquals(Rename.shortForms("beckman2025_ahhiyawa"), List("beckman2025"))
    assertEquals(Rename.shortForms("a2020_b_c"), List("a2020", "a2020_b"))
    assertEquals(Rename.shortForms("knapp2016"), Nil)
    assertEquals(Rename.shortForms("_x"), Nil)

  test("findingId: only `<old>#…` moves"):
    assertEquals(Rename.findingId("old2020_a#3", "old2020_a", "new1999_a"), Some("new1999_a#3"))
    assertEquals(Rename.findingId("old2020_ab#3", "old2020_a", "new1999_a"), None)
    assertEquals(Rename.findingId("custom-id", "old2020_a", "new1999_a"), None)

  test("replaceToken: the whole key only, never inside a longer one"):
    assertEquals(Rename.replaceToken("b2021_y|old2020_a|references", "old2020_a", "new1999_a"),
      "b2021_y|new1999_a|references")
    assertEquals(Rename.replaceToken("old2020_a#2", "old2020_a", "new1999_a"), "new1999_a#2")
    assertEquals(Rename.replaceToken("zold2020_a|old2020_ab|old2020_a.x", "old2020_a", "new1999_a"),
      "zold2020_a|old2020_ab|old2020_a.x")
    // a `.` in the key is literal, not a regex wildcard
    assertEquals(Rename.replaceToken("x2020_v1x2", "x2020_v1.2", "y"), "x2020_v1x2")

  test("retarget: target moved; label, anchor, escapes, spacing and embeds kept"):
    val to: String => Option[String] = t => Option.when(t == "old2020_a" || t == "old2020")("new1999_a")
    val md =
      """[[old2020_a]] [[old2020_a|the book]] [[old2020_a#ch-1|ch. 1]] [[ Old2020_A ]]
        |\[\[old2020_a\]\] ![[old2020_a]] [[old2020]] [[old2020_ab]] [[other2019_x]] [old2020_a]""".stripMargin
    val (out, n) = ArticleLinks.retarget(md, to)
    assertEquals(out,
      """[[new1999_a]] [[new1999_a|the book]] [[new1999_a#ch-1|ch. 1]] [[ new1999_a ]]
        |\[\[new1999_a\]\] ![[old2020_a]] [[new1999_a]] [[old2020_ab]] [[other2019_x]] [old2020_a]""".stripMargin)
    assertEquals(n, 6)

  test("retarget: nothing to move is the identity"):
    val md = "No [[links]] here to [[other2019_x]]."
    assertEquals(ArticleLinks.retarget(md, _ => None), (md, 0))

  test("the rename report names the alias, the counts, and what Calibre and the mirror need"):
    val src = SourceDetail("page1959_history", "History", List("Page"), Some(1959), None,
      SourceStatus.Foundational, Nil, Nil, Nil, None, None, None, None, None)
    val counts = RenameCounts(3, List("troy"), List("x2020_y"), 5, List(RenameCount("jobs.citekey", 1)), 0)
    val pushed = Markdown.sourceRenamed(SourceRenamed("page2024_history", "page1959_history", src, counts, Some("12")))
    List("`page2024_history` → `page1959_history`", "findings renumbered: 3", "`troy`", "jobs.citekey: 1",
      "is now an alias", "book 12", "sources/page2024_history.md")
      .foreach(s => assert(pushed.contains(s), s"missing '$s' in\n$pushed"))
    val notPushed = Markdown.sourceRenamed(SourceRenamed("page2024_history", "page1959_history", src, counts, None))
    assert(notPushed.contains("calibre push page1959_history"), notPushed)
    assert(notPushed.contains("`page2024_history` directory"), notPushed)
