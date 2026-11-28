package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The licence fields a re-read refreshes on a stored picture (the bytes stay). */
final case class Rights(
    license: Rights.License,
    licenseUrl: Option[Rights.LicenseUrl],
    author: Option[Rights.Author],
    credit: Rights.Credit
)

object Rights:
  export ProviderAsset.License
  export ProviderAsset.LicenseUrl
  export ProviderAsset.Author
  export ProviderAsset.Credit
