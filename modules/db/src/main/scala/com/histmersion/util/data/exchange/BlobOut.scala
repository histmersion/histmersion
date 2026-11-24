package com.histmersion
package util.data.exchange

import java.io.OutputStream
import com.histmersion.lint.{LintRule, tolerated}

/** A blob a bundle carries for a kind's lines: its digest, its size, and where its bytes are read from as it is written
  * into the archive.
  */
final case class BlobOut(sha256: BlobOut.Sha256, size: BlobOut.Size, copy: OutputStream => Unit)

object BlobOut:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  type Sha256 = com.histmersion.Sha256
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Sha256: com.histmersion.Sha256.type = com.histmersion.Sha256
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Size = ByteCount
  val Size: ByteCount.type = ByteCount
