package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure

object Flags:
  type Json = Json.T
  object Json extends Newtype[Boolean, Pure]
  type Write = Write.T
  object Write extends Newtype[Boolean, Pure]:
    def of(method: String): Write = Write(method != "GET")

    extension (write: Write) def fold[A](changes: => A, reads: => A): A = if write.value then changes else reads
  type Quiet = Quiet.T
  object Quiet extends Newtype[Boolean, Pure]
  type Import = Import.T
  object Import extends Newtype[Boolean, Pure]
