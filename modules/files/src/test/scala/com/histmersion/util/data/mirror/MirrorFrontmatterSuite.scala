package com.histmersion.util.data.mirror

import scala.jdk.CollectionConverters.*

/** The mirror's frontmatter writing: keys in the given order, block style, one-line empty lists, `null`s, no folding;
  * the note's fences and body.
  */
class MirrorFrontmatterSuite extends munit.FunSuite:

  test("keys in order, block lists, an empty list on one line, a null, a nested map"):
    val nested = new java.util.LinkedHashMap[String, Any]()
    nested.put("b", Long.box(-1200L))
    nested.put("a", "x")
    val fm = Frontmatter(
      List(
        "slug" -> "zz",
        "tags" -> List("a", "b").asJava,
        "regions" -> List.empty[String].asJava,
        "doi" -> null,
        "m" -> nested
      )
    )
    assertEquals(fm.yaml, "slug: zz\ntags:\n- a\n- b\nregions: []\ndoi: null\nm:\n  b: -1200\n  a: x\n")

  test("a long value stays on one line"):
    val long = List.fill(40)("word").mkString(" ")
    assertEquals(Frontmatter(List("summary" -> long)).yaml, s"summary: $long\n")

  test("the note: fenced frontmatter, then the body after a blank line when there is one"):
    val fm = Frontmatter(List("slug" -> "zz"))
    assertEquals(fm.note(None), "---\nslug: zz\n---\n")
    assertEquals(fm.note(Some("# Body")), "---\nslug: zz\n---\n\n# Body\n")
