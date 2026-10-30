package com.histmersion.domain

/** The licence of a collection record and the credit line in the provider's own form. */
final case class MediaRights(
    licence: MediaLicence.Licence,
    licenceUrl: Option[String],
    author: Option[String],
    credit: String
)

object MediaRights:
  val Cc0Url = "https://creativecommons.org/publicdomain/zero/1.0/"
  val PdmUrl = "https://creativecommons.org/publicdomain/mark/1.0/"
  def cc0(author: Option[String], credit: String): MediaRights =
    MediaRights(MediaLicence.Licence("CC0-1.0", MediaLicence.Cc0), Some(Cc0Url), author, credit)
  def publicDomain(author: Option[String], credit: String, url: Option[String] = Some(PdmUrl)): MediaRights =
    MediaRights(MediaLicence.Licence("public-domain", MediaLicence.PublicDomain), url, author, credit)
