package com.histmersion
package cli

import com.histmersion.config.*
import java.nio.file.Files
import java.util.UUID
import com.histmersion.util.data.exchange.{BundleFormat, BundleTar}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount
import com.histmersion.lint.{LintRule, tolerated}

/** `histmersion bundle …`: the subcommands parse, and the manifest of a downloaded bundle is read locally (it is
  * printed after an export). No server.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class BundleCmdSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  private def parses(args: String*) = Parsing.parses(args*)

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
    val m = Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      Bundle.Manifest.FormatVersion(1),
      Bundle.Manifest.SchemaVersion("33"),
      UUID.randomUUID(),
      Bundle.Instance(UUID.randomUUID(), Some(Bundle.Instance.Label("w"))),
      None,
      None,
      Nil,
      Bundle.Blobs(Bundle.Blobs.Count(0), ByteCount(0)),
      Bundle.Reference(
        Bundle.Reference.Places(0),
        Bundle.Reference.PlacesDigest(""),
        Bundle.Reference.Periods(0),
        Bundle.Reference.PeriodsDigest("")
      )
    )
    val f = Files.createTempFile("bundle", ".hmx")
    val out = Files.newOutputStream(f)
    try
      val w = BundleTar.Writer(out)
      val _ = w.bytes(BundleTar.Entry.Name(Bundle.ManifestPath), BundleFormat.manifestBytes(m))
      w.finish()
    finally out.close()
    assertEquals(BundleCmd.manifestOf(f), Right(m))
    val g = Files.createTempFile("not-a-bundle", ".hmx")
    val out2 = Files.newOutputStream(g)
    try
      val w = BundleTar.Writer(out2)
      val _ = w.bytes(BundleTar.Entry.Name("entities/goals.ndjson"), Array.emptyByteArray)
      w.finish()
    finally out2.close()
    assert(BundleCmd.manifestOf(g).isLeft)
