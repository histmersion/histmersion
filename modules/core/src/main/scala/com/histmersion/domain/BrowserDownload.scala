package com.histmersion
package domain

import com.histmersion.api.library.WorkRef

import com.histmersion.domain.library.{Citekey, Doi}

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.string.{Blank, Match}
import io.github.iltotore.iron.constraint.any.Pure

/** A gold open-access work whose publisher refuses scripts (`acquire.landing.browser-download-hosts`): the acquisition
  * list carries it as a download in the browser, where the person reads it under its licence, and then an ingest of the
  * downloaded file.
  */
final case class BrowserDownload(
    doi: Doi,
    citekey: Citekey,
    landing: BrowserDownload.LandingUrl,
    publisher: BrowserDownload.PublisherHost,
    oaStatus: BrowserDownload.OaStatus,
    licence: Option[BrowserDownload.Licence],
    citation: BrowserDownload.CitationText
):

  /** The acquisition item's text: it names the DOI as a doi.org link, which is what finds the item again. */
  def text: BrowserDownload.Description =
    BrowserDownload.Description(
      s"${citation.value} https://doi.org/${doi.value} — ${oaStatus.value} open access" +
        licence.fold("")(l => s", licence ${l.value}") +
        s", at ${landing.value}; ${publisher.value} refuses downloads by scripts: download it in the browser, then " +
        s"histmersion ingest <file> --citekey ${citekey.value}"
    )

object BrowserDownload:
  type Citation = Citation.T
  object Citation extends RefinedType[String, Pure]
  type Description = Description.T
  object Description extends RefinedType[String, Pure]
  /** An open-access location of a work, as the work lists it. */
  type Location = Location.T
  object Location extends RefinedType[String, Pure]
  /** The host of a location, as the configuration names it. */
  type Host = Host.T
  object Host extends RefinedType[String, Pure]

  type LandingUrl = LandingUrl.T
  object LandingUrl extends RefinedType[String, Match["(?i)https?://\\S+"]]

  type PublisherHost = PublisherHost.T
  object PublisherHost extends RefinedType[String, Not[Blank]]

  type OaStatus = OaStatus.T
  object OaStatus extends RefinedType[String, Match["gold|diamond"]]

  type Licence = Licence.T
  object Licence extends RefinedType[String, Not[Blank]]

  /** The work as the acquisition item cites it: `Author et al. (2019). Title.` */
  type CitationText = CitationText.T
  object CitationText extends RefinedType[String, Not[Blank]]

  /** The work as a browser download, when it is gold (or diamond) open access, has a DOI, and one of its open-access
    * locations is on a host that refuses scripts — `refusingHost(url)`, the host of a location when it is one of
    * `acquire.landing.browser-download-hosts` (that location is where the person downloads it).
    */
  def of(work: WorkRef, citekey: Citekey, refusingHost: Location => Option[Host]): Option[BrowserDownload] =
    val onRefusingHost =
      (work.oaLocations.map(l => Location(l.value)) ++ work.oaUrl.map(u => Location(u.value)).toList).distinct.flatMap(url => refusingHost(url).map(url -> _))
    for
      status <- work.oaStatus.flatMap(s => OaStatus.option(s.value.trim.toLowerCase(java.util.Locale.ROOT)))
      doi <- work.doi.flatMap(d => Doi.option(d.value.trim.toLowerCase(java.util.Locale.ROOT)))
      (url, host) <- onRefusingHost.headOption
      landing <- LandingUrl.option(url.value)
      publisher <- PublisherHost.option(host.value)
      citation <- CitationText.option(citationOf(work, doi).value)
    yield BrowserDownload(
      doi,
      citekey,
      landing,
      publisher,
      status,
      work.oaLicence.flatMap(l => Licence.option(l.value.trim)),
      citation
    )

  /** `Author et al. (2019). Title.` — the DOI stands in for a missing title. */
  def citationOf(work: WorkRef, doi: Doi): Citation =
    val who = work.authors.headOption.map(a => if work.authors.size > 1 then s"${a.value} et al." else a.value)
    val head = List(who, work.year.map(y => s"($y)")).flatten.mkString(" ")
    val title = work.title.map(_.value.trim).filter(_.nonEmpty).getOrElse(doi.value)
    Citation(s"${if head.isEmpty then "" else s"$head. "}$title.")
