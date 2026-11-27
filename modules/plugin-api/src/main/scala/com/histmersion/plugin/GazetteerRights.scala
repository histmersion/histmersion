package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a gazetteer dataset says about reuse, as its provider states it — never judged by the plugin.
  *
  * @param licence
  *   the licence as stated: an SPDX-like id (`CC-BY-4.0`, `ODbL-1.0`), a short name (`CC BY 4.0`) or the provider's own
  *   words (`open access`)
  * @param credit
  *   the credit line in the provider's own form — stored with every place of the dataset that states no source of its
  *   own
  * @param licenceUrl
  *   the licence's URL, when the provider gives one
  */
final case class GazetteerRights(
    licence: Licence,
    credit: GazetteerRights.Credit,
    licenceUrl: Option[GazetteerRights.LicenceUrl] = None
)

object GazetteerRights:
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type LicenceUrl = LicenceUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenceUrl extends Newtype[String, Pure]
