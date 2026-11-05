package com.histmersion.domain

/** Whom and how a reuse must credit: the licence, its URL, the author to name and the credit line in the provider's own
  * form. A picture carries one, and so does a dataset (its licence and credit).
  */
final case class Attribution(licence: Licence, licenceUrl: Option[String], author: Option[String], credit: String)

object Attribution:
  val Cc0Url = "https://creativecommons.org/publicdomain/zero/1.0/"
  val PdmUrl = "https://creativecommons.org/publicdomain/mark/1.0/"

  def cc0(author: Option[String], credit: String): Attribution =
    Attribution(Licence(Licence.Id("CC0-1.0"), Licence.Family.Cc0), Some(Cc0Url), author, credit)

  def publicDomain(author: Option[String], credit: String, url: Option[String] = Some(PdmUrl)): Attribution =
    Attribution(Licence(Licence.Id("public-domain"), Licence.Family.PublicDomain), url, author, credit)

  /** A dataset's credit as the reuse it allows (Q26): its stated licence classified as it is read — a Creative Commons
    * URL read as one, else its licence URL when the statement names no known licence — and the credit line it asks for.
    * What the credits show (`DataCredit`) stays as stated.
    */
  def ofDataset(credit: com.histmersion.api.media.DataCredit): Attribution =
    val stated = Licence.ofStatement(credit.licence)
    val licence =
      if stated.family != Licence.Family.Unknown then stated
      else credit.licenceUrl.map(Licence.ofUrl).filter(_.family != Licence.Family.Unknown).getOrElse(stated)
    Attribution(licence, credit.licenceUrl, None, credit.credit)
