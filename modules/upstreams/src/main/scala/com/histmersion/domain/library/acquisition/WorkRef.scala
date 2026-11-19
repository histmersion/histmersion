package com.histmersion
package domain.library.acquisition

import com.histmersion.plugin.Doi
import com.histmersion.plugin.Year
import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Licence
final case class WorkRef(
    doi: Option[Doi] = None,
    openalexId: Option[WorkRef.OpenalexId] = None,
    arxivId: Option[WorkRef.ArxivId] = None,
    title: Option[WorkRef.Title] = None,
    year: Option[Year] = None,
    authors: List[WorkKey.Authors] = Nil,
    venue: Option[WorkRef.Venue] = None,
    citedByCount: Option[WorkRef.CitedByCount] = None,
    isOa: WorkRef.IsOa = WorkRef.IsOa(false),
    oaStatus: Option[WorkRef.OaStatus] = None,
    oaUrl: Option[WorkRef.OaUrl] = None,
    isRetracted: WorkRef.IsRetracted = WorkRef.IsRetracted(false),
    workType: Option[WorkRef.WorkType] = None,
    abstractText: Option[WorkRef.AbstractText] = None,
    referencedWorks: List[WorkRef.ReferencedWorks] = Nil,
    oaLocations: List[WorkRef.OaLocations] = Nil,
    oaLicence: Option[WorkRef.OaLicence] = None
)

object WorkRef:
  type OpenalexId = OpenalexId.T
  object OpenalexId extends Newtype[String, Pure]
  export com.histmersion.upstreams.ArxivId
  export WorkKey.Title
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends Newtype[Int, Pure]
  type IsOa = IsOa.T
  object IsOa extends Newtype[Boolean, Pure]
  type OaStatus = OaStatus.T
  object OaStatus extends Newtype[String, Pure]
  type OaUrl = OaUrl.T
  object OaUrl extends Newtype[String, Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends Newtype[Boolean, Pure]
  type WorkType = WorkType.T
  object WorkType extends Newtype[String, Pure]
  type AbstractText = AbstractText.T
  object AbstractText extends Newtype[String, Pure]
  type ReferencedWorks = ReferencedWorks.T
  object ReferencedWorks extends Newtype[String, Pure]
  type OaLocations = OaLocations.T
  object OaLocations extends Newtype[String, Pure]
  type OaLicence = Licence
  val OaLicence: Licence.type = Licence
