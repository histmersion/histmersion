package com.histmersion.domain

import com.histmersion.values.LicenceId

/** Whom and how a reuse must credit: the licence, its URL, the author to name and the credit line in the provider's own
  * form. A picture carries one, and so does a dataset (its licence and credit).
  */
final case class Attribution(licence: Licence, licenceUrl: Option[String], author: Option[String], credit: String)

object Attribution:
  val Cc0Url = "https://creativecommons.org/publicdomain/zero/1.0/"
  val PdmUrl = "https://creativecommons.org/publicdomain/mark/1.0/"

  def cc0(author: Option[String], credit: String): Attribution =
    Attribution(Licence(LicenceId("CC0-1.0"), LicenceFamily.Cc0), Some(Cc0Url), author, credit)

  def publicDomain(author: Option[String], credit: String, url: Option[String] = Some(PdmUrl)): Attribution =
    Attribution(Licence(LicenceId("public-domain"), LicenceFamily.PublicDomain), url, author, credit)
