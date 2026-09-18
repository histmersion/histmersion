package histdb.core

import java.nio.file.Paths

/** Three surfaces take a SERVER-side path from the caller (`seed {dir}`, MCP `ingest {path}`, a
  * note's `md_path`). Confinement to `corpus.dir` is what stops them being a file-read primitive. */
class SafePathSuite extends munit.FunSuite:

  private val base = Some("/srv/corpus")

  test("a path inside the corpus resolves"):
    assertEquals(SafePath.confine(base, "/srv/corpus/sources").map(_.toString),
      Right("/srv/corpus/sources"))

  test("a RELATIVE path is resolved against the corpus, not the process's cwd"):
    assertEquals(SafePath.confine(base, "sources").map(_.toString), Right("/srv/corpus/sources"))

  test("escaping is refused, however it is spelled"):
    List("/etc/passwd", "../../etc/passwd", "/srv/corpus/../etc", "/srv/corpus/a/../../etc",
      "/srv/corpusevil")
      .foreach(p => assert(SafePath.confine(base, p).isLeft, s"'$p' MUST be refused"))

  test("the refusal names the configured directory, so it is actionable"):
    val e = SafePath.confine(base, "/etc/passwd").swap.toOption.get
    assert(e.contains("/srv/corpus"), e)

  test("no corpus configured = the old behaviour, unchanged"):
    assert(SafePath.confine(None, "/etc/passwd").isRight)
    assert(SafePath.confine(Some("  "), "/etc/passwd").isRight)
    assert(!SafePath.configured(None))
    assert(!SafePath.configured(Some("")))
    assert(SafePath.configured(base))

  test("an empty path is refused whatever the configuration"):
    assert(SafePath.confine(base, "").isLeft)
    assert(SafePath.confine(None, "   ").isLeft)

  test("`within` is the same test for an already-built path"):
    assert(SafePath.within(base, Paths.get("/srv/corpus/md/x/paper.md")))
    assert(!SafePath.within(base, Paths.get("/srv/corpus/../etc/shadow")))
    assert(SafePath.within(None, Paths.get("/etc/shadow")), "unconfigured = unconfined")
