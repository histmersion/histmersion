package com.histmersion.server

import com.histmersion.server.acquire.Spool
import java.nio.file.{Files, Path}

/** `Spool.write` resolves `<dir>/<citekey>` on the server's disk. Defence in depth: the citekey must parse, AND the
  * resolved directory must still be under the base — a path-traversal guard should not rest on one regex being read
  * correctly. Pure filesystem, no database.
  */
class SpoolSuite extends munit.FunSuite:

  private val tmp = FunFixture[Path](
    setup = _ => Files.createTempDirectory("histmersion-spool-test"),
    teardown = d =>
      if Files.exists(d) then
        Files
          .walk(d)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach { p =>
            val _ = Files.deleteIfExists(p)
          }
  )

  private val bytes = "%PDF-1.4".getBytes("UTF-8")

  tmp.test("a valid citekey writes inside the spool") { d =>
    val p = Spool.write(d.toString, "anthony2007_horse", "pdf", bytes)
    assert(p.isRight, p.toString)
    assert(p.toOption.get.startsWith(d), p.toString)
    assertEquals(p.toOption.get.getFileName.toString, "paper.pdf")
    assert(Files.exists(p.toOption.get))
  }

  tmp.test("an escaping citekey writes NOTHING, anywhere") { d =>
    val outside = d.getParent.resolve("histmersion-spool-escaped")
    List("../histmersion-spool-escaped", "../../tmp/x", "/etc/histmersion", "a/../../b", "..")
      .foreach { evil =>
        val r = Spool.write(d.toString, evil, "pdf", bytes)
        assert(r.isLeft, s"'$evil' was NOT refused: $r")
      }
    assert(!Files.exists(outside), "the traversal must not have created a directory")
    assertEquals(Files.list(d).count(), 0L, "…nor anything inside the spool")
  }

  tmp.test("the extension is sanitised too — it is the other half of the file name") { d =>
    val p = Spool.write(d.toString, "x2020_y", "../../evil", bytes)
    assert(p.isRight, p.toString)
    assert(p.toOption.get.startsWith(d), p.toString)
    assert(!p.toOption.get.toString.contains(".."), p.toString)
  }
