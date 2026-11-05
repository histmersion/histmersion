package com.histmersion.domain.media.imports

import com.histmersion.domain.Licence

/** The licence families a collection's pictures may be stored under: its allowlist. A first-party collection is judged
  * by its own `media.<provider>.licences`, a plugin's by `media.collections` (its `per-collection` entry, else the
  * default); both are read as a policy over the shared [[LicenceFamily]]. All rights reserved and an unreadable licence
  * are never allowed.
  */
final case class LicencePolicy(allowed: Set[Licence.Family]):

  /** Whether a picture under `l` may be stored. */
  def allows(l: Licence): com.histmersion.domain.media.imports.LicencePolicy.AllowsResult = Licence.allowed(l, allowed)

object LicencePolicy:

  /** The policy a settings value names (`"cc0,public-domain,cc-by,cc-by-sa"`). */
  type AllowsResult = AllowsResult.T
  object AllowsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfCsv = OfCsv.T
  object OfCsv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(csv: com.histmersion.domain.media.imports.LicencePolicy.OfCsv): LicencePolicy = LicencePolicy(Licence.allowList(csv))
