package com.histmersion.domain.media.imports

import com.histmersion.domain.{Licence, LicenceFamily}

/** The licence families a collection's pictures may be stored under: its allowlist. A first-party collection is judged
  * by its own `media.<provider>.licences`, a plugin's by `media.collections` (its `per-collection` entry, else the
  * default); both are read as a policy over the shared [[LicenceFamily]]. All rights reserved and an unreadable licence
  * are never allowed.
  */
final case class LicencePolicy(allowed: Set[LicenceFamily]):

  /** Whether a picture under `l` may be stored. */
  def allows(l: Licence): Boolean = Licence.allowed(l, allowed)

object LicencePolicy:

  /** The policy a settings value names (`"cc0,public-domain,cc-by,cc-by-sa"`). */
  def of(csv: String): LicencePolicy = LicencePolicy(Licence.allowList(csv))
