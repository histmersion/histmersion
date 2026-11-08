package com.histmersion
package upstreams.pmc

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class PmcObject(key: PmcObject.Key, md5: Option[PmcObject.Md5])

object PmcObject:
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Md5 = Md5.T
  object Md5 extends RefinedType[String, Pure]
