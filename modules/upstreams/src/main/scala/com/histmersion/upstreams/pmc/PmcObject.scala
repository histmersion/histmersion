package com.histmersion
package upstreams.pmc

import io.github.iltotore.iron.constraint.any.Pure
final case class PmcObject(key: PmcObject.Key, md5: Option[PmcObject.Md5])

object PmcObject:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  export com.histmersion.upstreams.identifiers.Md5
