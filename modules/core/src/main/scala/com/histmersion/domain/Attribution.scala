package com.histmersion.domain

/** Whom and how a reuse must credit: the licence, its URL, the author to name and the credit line in the provider's own
  * form. A picture carries one, and so does a dataset (its licence and credit).
  */
final case class Attribution(licence: Licence, licenceUrl: Option[Attribution.LicenceUrl], author: Option[Attribution.Author], credit: Attribution.Credit)

object Attribution:
  type Cc0Author = _root_.com.histmersion.domain.AuthorName
  val Cc0Author: _root_.com.histmersion.domain.AuthorName.type = _root_.com.histmersion.domain.AuthorName
  type Cc0Credit = _root_.com.histmersion.domain.Attribution.Credit
  val Cc0Credit: _root_.com.histmersion.domain.Attribution.Credit.type = _root_.com.histmersion.domain.Attribution.Credit
  type PublicDomainAuthor = _root_.com.histmersion.domain.AuthorName
  val PublicDomainAuthor: _root_.com.histmersion.domain.AuthorName.type = _root_.com.histmersion.domain.AuthorName
  type PublicDomainCredit = _root_.com.histmersion.domain.Attribution.Credit
  val PublicDomainCredit: _root_.com.histmersion.domain.Attribution.Credit.type = _root_.com.histmersion.domain.Attribution.Credit

  type LicenceUrl = com.histmersion.domain.Licence.Url
  val LicenceUrl: _root_.com.histmersion.domain.Licence.Url.type = _root_.com.histmersion.domain.Licence.Url
  type Author = com.histmersion.domain.AuthorName
  val Author: _root_.com.histmersion.domain.AuthorName.type = _root_.com.histmersion.domain.AuthorName
  type Credit = Credit.T
  object Credit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Cc0Url: Licence.Url = Licence.Url("https://creativecommons.org/publicdomain/zero/1.0/")
  val PdmUrl: Licence.Url = Licence.Url("https://creativecommons.org/publicdomain/mark/1.0/")

  def cc0(author: Option[com.histmersion.domain.Attribution.Cc0Author], credit: com.histmersion.domain.Attribution.Cc0Credit): Attribution =
    Attribution(Licence(Licence.Id("CC0-1.0"), Licence.Family.Cc0), Some(Cc0Url), author, credit)

  def publicDomain(author: Option[com.histmersion.domain.Attribution.PublicDomainAuthor], credit: com.histmersion.domain.Attribution.PublicDomainCredit, url: Option[com.histmersion.domain.Licence.Url] = Some(PdmUrl)): Attribution =
    Attribution(Licence(Licence.Id("public-domain"), Licence.Family.PublicDomain), url, author, credit)

  /** A dataset's credit as the reuse it allows (Q26): its stated licence classified as it is read — a Creative Commons
    * URL read as one, else its licence URL when the statement names no known licence — and the credit line it asks for.
    * What the credits show (`DataCredit`) stays as stated.
    */
  def ofDataset(credit: com.histmersion.api.media.DataCredit): Attribution =
    val stated = Licence.ofStatement(Licence.Statement(credit.licence.value))
    val licence =
      if stated.family != Licence.Family.Unknown then stated
      else credit.licenceUrl.map(u => Licence.ofUrl(Licence.Url(u.value))).filter(_.family != Licence.Family.Unknown).getOrElse(stated)
    Attribution(licence, credit.licenceUrl.map(u => LicenceUrl(u.value)), None, Credit(credit.credit.value))
