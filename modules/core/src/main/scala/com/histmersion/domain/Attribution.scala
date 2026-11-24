package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.media.DataCredit
import com.histmersion.lint.{LintRule, tolerated}

/** Whom and how a reuse must credit: the licence, its URL, the author to name and the credit line in the provider's own
  * form. A picture carries one, and so does a dataset (its licence and credit).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class Attribution(
    licence: Licence,
    licenceUrl: Option[Attribution.LicenceUrl],
    author: Option[Attribution.Author],
    credit: Attribution.Credit
)

object Attribution:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Cc0Author = AuthorName
  val Cc0Author: AuthorName.type = AuthorName
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Cc0Credit = Credit
  val Cc0Credit: Credit.type = Credit
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type PublicDomainAuthor = AuthorName
  val PublicDomainAuthor: AuthorName.type = AuthorName
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type PublicDomainCredit = Credit
  val PublicDomainCredit: Credit.type = Credit

  export Licence.Url as LicenceUrl
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Author = AuthorName
  val Author: AuthorName.type = AuthorName
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]

  val Cc0Url: Licence.Url = Licence.Url("https://creativecommons.org/publicdomain/zero/1.0/")
  val PdmUrl: Licence.Url = Licence.Url("https://creativecommons.org/publicdomain/mark/1.0/")

  def cc0(author: Option[Cc0Author], credit: Cc0Credit): Attribution =
    Attribution(Licence(Licence.Id("CC0-1.0"), Licence.Family.Cc0), Some(Cc0Url), author, credit)

  def publicDomain(
      author: Option[PublicDomainAuthor],
      credit: PublicDomainCredit,
      url: Option[Licence.Url] = Some(PdmUrl)
  ): Attribution =
    Attribution(Licence(Licence.Id("public-domain"), Licence.Family.PublicDomain), url, author, credit)

  /** A dataset's credit as the reuse it allows (Q26): its stated licence classified as it is read — a Creative Commons
    * URL read as one, else its licence URL when the statement names no known licence — and the credit line it asks for.
    * What the credits show (`DataCredit`) stays as stated.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ofDataset(credit: DataCredit): Attribution =
    val stated = Licence.ofStatement(Licence.Statement(credit.licence.value))
    val licence =
      if stated.family != Licence.Family.Unknown then stated
      else
        credit.licenceUrl
          .map(u => Licence.ofUrl(Licence.Url(u.value)))
          .filter(_.family != Licence.Family.Unknown)
          .getOrElse(stated)
    Attribution(licence, credit.licenceUrl.map(u => LicenceUrl(u.value)), None, Credit(credit.credit.value))
