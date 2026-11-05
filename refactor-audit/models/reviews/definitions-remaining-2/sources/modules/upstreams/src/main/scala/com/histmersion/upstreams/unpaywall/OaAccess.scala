package com.histmersion.upstreams.unpaywall
final case class OaAccess(isOa: OaAccess.IsOa, status: Option[OaAccess.Status], url: Option[OaAccess.Url])

object OaAccess:
  type IsOa = IsOa.T
  object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
