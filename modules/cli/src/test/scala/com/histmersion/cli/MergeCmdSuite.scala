package com.histmersion.cli

import com.histmersion.core.*
import com.monovore.decline.Command
import java.nio.file.Files

/** `histmersion merge plan` and the resolution flags of `bundle import`: they parse, and the file
  * plus the flags make one resolution set, flags winning, every bad line reported. No server. */
class MergeCmdSuite extends munit.FunSuite:

  private val top = Command("histmersion", "test")(MergeCmd.command orElse BundleCmd.command)
  private def parses(args: String*) = top.parse(args.toList, Map.empty).isRight

  test("merge plan and bundle import parse their resolution flags"):
    assert(parses("merge", "plan", "work.hmx"))
    assert(parses("merge", "plan", "work.hmx", "--resolutions", "r.yaml", "--resolve", "S-1=keep-left",
      "--resolve", "A-2=both", "--editions", "merge", "--write-template", "t.yaml", "--all", "--json"))
    assert(parses("bundle", "import", "work.hmx", "--resolutions", "r.yaml", "--dry-run"))
    assert(!parses("merge", "plan"))

  test("the file, then the flags over it; every error at once"):
    val f = Files.createTempFile("resolutions", ".yaml")
    Files.writeString(f, "policy:\n  editions: separate\nresolve:\n  S-11111111: keep-left\n  S-22222222: both\n  A-33333333:\n")
    val r = MergeCmd.resolutions(MergeCmd.ResolutionFlags(Some(f.toString), List("S-22222222=map:x2000_y"), None))
      .fold(e => fail(e.message), identity)
    assertEquals(r.resolve.map(x => x.issue -> x.decision).toMap,
      Map("S-11111111" -> "keep-left", "S-22222222" -> "map:x2000_y"))
    assertEquals(r.policy.editions, Some(EditionsPolicy.Separate))
    val bad = MergeCmd.resolutions(MergeCmd.ResolutionFlags(Some("/no/such/file.yaml"), List("nonsense"), Some("sometimes")))
    val msg = bad.left.map(_.message).swap.getOrElse(fail("accepted"))
    assert(msg.contains("no such file") && msg.contains("--resolve nonsense") && msg.contains("--editions sometimes"), msg)
    assertEquals(bad.left.map(_.exitCode), Left(1))
