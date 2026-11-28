package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure

object Flags:
  enum Json derives CanEqual:
    case Asked, Text
  object Json:
    extension (json: Json) def asked: Boolean = json == Asked
  enum Write derives CanEqual:
    case Changes, Reads
  object Write:
    extension (write: Write) def changes: Boolean = write == Changes
    def of(method: String): Write = (if method != "GET" then Changes else Reads)

    extension (write: Write) def fold[A](changes: => A, reads: => A): A = if write.changes then changes else reads
  type Quiet = Quiet.T
  object Quiet extends Newtype[Boolean, Pure]
  enum Import derives CanEqual:
    case Import, Inspect
  object Import:
    extension (`import`: Import) def imports: Boolean = `import` == Import
