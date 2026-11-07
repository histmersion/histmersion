package com.histmersion.cli

import com.histmersion.config.*
import com.monovore.decline.Command
import java.nio.file.Files
import java.util.UUID
import com.histmersion.util.data.exchange.{BundleFormat, BundleTar}
import com.histmersion.api.exchange.Bundle

/** `histmersion bundle …`: the subcommands parse, and the manifest of a downloaded bundle is read locally (it is
  * printed after an export). No server.
  */
class BundleCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

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
    val m = Bundle.Manifest(
      Bundle.Manifest.Format(Bundle.Format),
      com.histmersion.api.exchange.Bundle.Manifest.FormatVersion(1),
      com.histmersion.api.exchange.Bundle.Manifest.SchemaVersion("33"),
      UUID.randomUUID(),
      Bundle.Instance(UUID.randomUUID(), Some(com.histmersion.api.exchange.Bundle.Instance.Label("w"))),
      None,
      None,
      Nil,
      Bundle.Blobs(com.histmersion.api.exchange.Bundle.Blobs.Count(0), com.histmersion.api.ByteCount(0)),
      Bundle.Reference(com.histmersion.api.exchange.Bundle.Reference.Places(0), com.histmersion.api.exchange.Bundle.Reference.PlacesDigest(""), com.histmersion.api.exchange.Bundle.Reference.Periods(0), com.histmersion.api.exchange.Bundle.Reference.PeriodsDigest(""))
    )
    val f = Files.createTempFile("bundle", ".hmx")
    val out = Files.newOutputStream(f)
    try
      val w = BundleTar.Writer(out)
      val _ = w.bytes(BundleTar.Entry.Name(Bundle.ManifestPath),BundleFormat.manifestBytes(m))
      w.finish()
    finally out.close()
    assertEquals(BundleCmd.manifestOf(f), Right(m))
    val g = Files.createTempFile("not-a-bundle", ".hmx")
    val out2 = Files.newOutputStream(g)
    try
      val w = BundleTar.Writer(out2)
      val _ = w.bytes(com.histmersion.util.data.exchange.BundleTar.Entry.Name("entities/goals.ndjson"), Array.emptyByteArray)
      w.finish()
    finally out2.close()
    assert(BundleCmd.manifestOf(g).isLeft)
