package com.histmersion
package cli

import com.histmersion.config.*
import com.monovore.decline.Command
import java.nio.file.Files
import com.histmersion.api.exchange.EditionsPolicy
import com.histmersion.api.exchange.{MergeResolution, MergeResolutions}

/** `histmersion merge plan` and the resolution flags of `bundle import`: they parse, and the file plus the flags make
  * one resolution set, flags winning, every bad line reported. No server.
  */
class MergeCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val top = Command("histmersion", "test")(MergeCmd.command orElse BundleCmd.command)
  private def parses(args: String*) = top.parse(args.toList, Map.empty).isRight

  test("merge plan and bundle import parse their resolution flags"):
    assert(parses("merge", "plan", "work.hmx"))
    assert(
      parses(
        "merge",
        "plan",
        "work.hmx",
        "--resolutions",
        "r.yaml",
        "--resolve",
        "S-1=keep-left",
        "--resolve",
        "A-2=both",
        "--editions",
        "merge",
        "--write-template",
        "t.yaml",
        "--all",
        "--json"
      )
    )
    assert(parses("bundle", "import", "work.hmx", "--resolutions", "r.yaml", "--dry-run"))
    assert(!parses("merge", "plan"))
    assert(parses("merge", "apply", "work.hmx", "--resolutions", "r.yaml", "--plan", "7c41e9a2", "--yes"))
    assert(parses("merge", "apply", "work.hmx", "--resolve", "A-1=file:merged.md"))
    assert(!parses("merge", "apply"))

  test("the file, then the flags over it; every error at once"):
    val f = Files.createTempFile("resolutions", ".yaml")
    Files.writeString(
      f,
      "policy:\n  editions: separate\nresolve:\n  S-11111111: keep-left\n  S-22222222: both\n  A-33333333:\n"
    )
    val r = MergeCmd
      .resolutions(
        MergeCmd.ResolutionFlags(
          Some(MergeCmd.ResolutionFlags.File(f.toString)),
          List(MergeResolutions.Text.Flag("S-22222222=map:x2000_y")),
          None
        )
      )
      .fold(e => fail(e.message.value), identity)
    assertEquals(
      r.resolve.map(x => x.issue -> x.decision).toMap,
      Map(
        MergeResolution.Issue("S-11111111") -> MergeResolution.Decision("keep-left"),
        MergeResolution.Issue("S-22222222") -> MergeResolution.Decision("map:x2000_y")
      )
    )
    assertEquals(r.policy.editions, Some(EditionsPolicy.Separate))
    val bad =
      MergeCmd.resolutions(
        MergeCmd.ResolutionFlags(
          Some(MergeCmd.ResolutionFlags.File("/no/such/file.yaml")),
          List(MergeResolutions.Text.Flag("nonsense")),
          Some(MergeCmd.ResolutionFlags.Editions("sometimes"))
        )
      )
    val msg = bad.left.map(_.message.value).swap.getOrElse(fail("accepted"))
    assert(
      msg.contains("no such file") && msg.contains("--resolve nonsense") && msg.contains("--editions sometimes"),
      msg
    )
    assertEquals(bad.left.map(_.exitCode), Left(ExitCode.Usage))

  test("merge apply sends the merged text of a file: decision; a file that is not there is an error"):
    val f = Files.createTempFile("merged", ".md")
    Files.writeString(f, "The merged text.")
    val r = MergeCmd
      .withTexts(
        MergeResolutions(
          List(
            MergeResolution(MergeResolution.Issue("A-1"), MergeResolution.Decision(s"file:$f")),
            MergeResolution(MergeResolution.Issue("S-2"), MergeResolution.Decision("keep-left"))
          )
        )
      )
      .fold(e => fail(e.message.value), identity)
    assertEquals(
      r.resolve.map(x => x.issue -> x.text).toMap,
      Map(
        MergeResolution.Issue("A-1") -> Some(MergeResolution.Text("The merged text.")),
        MergeResolution.Issue("S-2") -> None
      )
    )
    val bad = MergeCmd.withTexts(
      MergeResolutions(
        List(MergeResolution(MergeResolution.Issue("A-1"), MergeResolution.Decision("file:/no/such/merged.md")))
      )
    )
    assert(bad.left.exists(_.message.value.contains("A-1: file:/no/such/merged.md — no such file")), bad.toString)
