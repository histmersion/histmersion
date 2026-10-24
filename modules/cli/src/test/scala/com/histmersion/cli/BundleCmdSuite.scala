package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.monovore.decline.Command
import java.nio.file.Files
import java.util.UUID

/** `histmersion bundle …`: the subcommands parse, and the manifest of a downloaded bundle is read
  * locally (it is printed after an export). No server. */
class BundleCmdSuite extends munit.FunSuite:

  private val top = Command("histmersion", "test")(BundleCmd.command)
  private def parses(args: String*) = top.parse(args.toList, Map.empty).isRight

  test("export, verify, import and instance parse; a missing file is a usage error"):
    assert(parses("bundle", "export", "work.hmx"))
    assert(parses("bundle", "export", "work.hmx", "--since", "2026-10-03T09:12:00Z"))
    assert(parses("bundle", "verify", "work.hmx", "--json"))
    assert(parses("bundle", "import", "work.hmx", "--dry-run"))
    assert(parses("bundle", "instance"))
    assert(parses("bundle", "instance", "--label", "work"))
    assert(!parses("bundle", "import"))
    assert(!parses("bundle", "export"))

  test("the manifest of a bundle on disk is its first entry; anything else is refused"):
    val m = BundleManifest(Bundle.Format, 1, "33", UUID.randomUUID(), BundleInstance(UUID.randomUUID(), Some("w")),
      None, None, Nil, BundleBlobs(0, 0), BundleReference(0, "", 0, ""))
    val f   = Files.createTempFile("bundle", ".hmx")
    val out = Files.newOutputStream(f)
    try
      val w = BundleTar.Writer(out)
      val _ = w.bytes(Bundle.ManifestPath, BundleFormat.manifestBytes(m))
      w.finish()
    finally out.close()
    assertEquals(BundleCmd.manifestOf(f), Right(m))
    val g   = Files.createTempFile("not-a-bundle", ".hmx")
    val out2 = Files.newOutputStream(g)
    try
      val w = BundleTar.Writer(out2)
      val _ = w.bytes("entities/goals.ndjson", Array.emptyByteArray)
      w.finish()
    finally out2.close()
    assert(BundleCmd.manifestOf(g).isLeft)
