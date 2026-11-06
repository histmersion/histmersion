package com.histmersion.domain

import com.histmersion.api.library.WorkRef

import com.histmersion.domain.library.{Citekey, Doi}

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.string.{Blank, Match}

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
  def text: com.histmersion.domain.BrowserDownload.Description =
    s"${citation.value} https://doi.org/${doi.value} — ${oaStatus.value} open access" +
      licence.fold("")(l => s", licence ${l.value}") +
      s", at ${landing.value}; ${publisher.value} refuses downloads by scripts: download it in the browser, then " +
      s"histmersion ingest <file> --citekey ${citekey.value}"

object BrowserDownload:
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfRefusingHostArg1 = OfRefusingHostArg1.T
  object OfRefusingHostArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfRefusingHostResult = OfRefusingHostResult.T
  object OfRefusingHostResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  def of(work: WorkRef, citekey: Citekey, refusingHost: com.histmersion.domain.BrowserDownload.OfRefusingHostArg1 => Option[com.histmersion.domain.BrowserDownload.OfRefusingHostResult]): Option[BrowserDownload] =
    val onRefusingHost =
      (work.oaLocations ++ work.oaUrl.toList).distinct.flatMap(url => refusingHost(url).map(url -> _))
    for
      status <- work.oaStatus.flatMap(s => OaStatus.option(s.trim.toLowerCase(java.util.Locale.ROOT)))
      doi <- work.doi.flatMap(d => Doi.option(d.value.trim.toLowerCase(java.util.Locale.ROOT)))
      (url, host) <- onRefusingHost.headOption
      landing <- LandingUrl.option(url)
      publisher <- PublisherHost.option(host)
      citation <- CitationText.option(citationOf(work, doi))
    yield BrowserDownload(
      doi,
      citekey,
      landing,
      publisher,
      status,
      work.oaLicence.flatMap(l => Licence.option(l.trim)),
      citation
    )

  /** `Author et al. (2019). Title.` — the DOI stands in for a missing title. */
  def citationOf(work: WorkRef, doi: Doi): com.histmersion.domain.BrowserDownload.Citation =
    val who = work.authors.headOption.map(a => if work.authors.size > 1 then s"$a et al." else a)
    val head = List(who, work.year.map(y => s"($y)")).flatten.mkString(" ")
    val title = work.title.map(_.value.trim).filter(_.nonEmpty).getOrElse(doi.value)
    s"${if head.isEmpty then "" else s"$head. "}$title."
