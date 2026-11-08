package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.constraint.any.Pure

/** The licence fields a re-read refreshes on a stored picture (the bytes stay). */
final case class Rights(license: Rights.License, licenseUrl: Option[Rights.LicenseUrl], author: Option[Rights.Author], credit: Rights.Credit)

object Rights:
  type License = License.T
  object License extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
