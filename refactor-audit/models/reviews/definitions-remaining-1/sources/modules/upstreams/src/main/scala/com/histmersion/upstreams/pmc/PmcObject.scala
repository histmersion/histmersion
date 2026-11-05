package com.histmersion.upstreams.pmc
final case class PmcObject(key: PmcObject.Key, md5: Option[PmcObject.Md5])

object PmcObject:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Md5 = Md5.T
  object Md5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
