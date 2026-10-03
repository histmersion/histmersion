package com.histmersion.server.agents

import java.nio.file.Files

/** The prompt files: bundled resources, one file each overridable from `agents.prompts-dir`, the
  * shared agent definitions read without their frontmatter. */
class PromptsSuite extends munit.FunSuite:

  test("every prompt the server reads is bundled"):
    val p = Prompts("")
    Prompts.bundled.foreach(rel => assert(p.load(rel).exists(_.trim.nonEmpty), s"missing or empty: agents/$rel"))

  test("a review agent is its shared definition without the frontmatter"):
    val skeptic = Prompts("").agent("article-skeptic").toOption.get
    assert(skeptic.startsWith("You are the **specialist skeptic**"), skeptic.take(80))
    assert(!skeptic.contains("model:"))
    assertEquals(Prompt.stripFrontmatter("---\nname: x\n---\n\nBody\n"), "Body")
    assertEquals(Prompt.stripFrontmatter("No frontmatter"), "No frontmatter")

  test("the override directory wins file by file; a path out of it is never read"):
    val dir = Files.createTempDirectory("prompts")
    try
      Files.createDirectories(dir.resolve("prompts/enrich-source"))
      Files.writeString(dir.resolve("prompts/enrich-source/notes.md"), "OVERRIDDEN notes")
      Files.writeString(dir.getParent.resolve("outside.md"), "outside")
      val p = Prompts(dir.toString)
      assertEquals(p.step("enrich-source", "notes"), Right("OVERRIDDEN notes"))
      assert(p.step("enrich-source", "classify").exists(_.contains("You classify one source")), "the rest stay bundled")
      assert(p.load("../outside.md").isLeft)
    finally
      Files.deleteIfExists(dir.getParent.resolve("outside.md"))
      Files.deleteIfExists(dir.resolve("prompts/enrich-source/notes.md"))
