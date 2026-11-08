package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a record says about reuse, as the provider states it — never judged by the plugin.
  *
  * @param licence
  *   the licence as stated: an SPDX-like id (`CC-BY-4.0`, `CC0-1.0`), a short name (`CC BY-SA 3.0`, `Public domain`) or
  *   a Creative Commons URL. The host classifies it; a statement it cannot read is "unknown", which is never stored
  * @param licenceUrl
  *   the licence's URL, when the record gives one (read too when [[licence]] is unclear)
  * @param author
  *   the creator to name, when the record names one
  * @param credit
  *   the credit line in the provider's own form (required: shown with every use)
  */
final case class MediaRights(
    licence: Licence,
    licenceUrl: Option[MediaRights.LicenceUrl] = None,
    author: Option[MediaRights.Author] = None,
    credit: MediaRights.Credit = MediaRights.Credit("")
)

object MediaRights:
  type LicenceUrl = LicenceUrl.T
  object LicenceUrl extends RefinedType[String, Pure]
  type Author = Author.T
  object Author extends RefinedType[String, Pure]
  type Credit = Credit.T
  object Credit extends RefinedType[String, Pure]
