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
  type License = License.T
  object License extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
