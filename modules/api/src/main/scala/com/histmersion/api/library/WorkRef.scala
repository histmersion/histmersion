package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class WorkRef(
    doi: Option[Doi] = None,
    openalexId: Option[WorkRef.OpenalexId] = None,
    arxivId: Option[WorkRef.ArxivId] = None,
    title: Option[Title] = None,
    year: Option[Year] = None,
    authors: List[AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
    venue: Option[com.histmersion.api.library.Source.Venue] = None,
    citedByCount: Option[WorkRef.CitedByCount] = None,
    isOa: WorkRef.IsOa = _root_.com.histmersion.api.library.Source.Candidate.IsOa(false),
    oaStatus: Option[WorkRef.OaStatus] = None,
    oaUrl: Option[WorkRef.OaUrl] = None,
    isRetracted: WorkRef.IsRetracted = WorkRef.IsRetracted(false),
    workType: Option[WorkRef.WorkType] = None,
    abstractText: Option[WorkRef.AbstractText] = None,
    referencedWorks: List[WorkRef.ReferencedWorks] = Nil,
    citekey: Option[Citekey] = None,
    inDb: WorkRef.InDb = WorkRef.InDb(false),
    oaLocations: List[WorkRef.OaLocations] = Nil,
    oaLicence: Option[WorkRef.OaLicence] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object WorkRef:
  type OpenalexId = OpenalexId.T
  object OpenalexId extends Newtype[String, Pure]
  export Source.Candidate.ArxivId
  export Source.CitedByCount
  export Source.Candidate.IsOa
  type OaStatus = OaStatus.T
  object OaStatus extends Newtype[String, Pure]
  type OaUrl = OaUrl.T
  object OaUrl extends Newtype[String, Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends Newtype[Boolean, Pure]
  type WorkType = WorkType.T
  object WorkType extends Newtype[String, Pure]
  export Source.Candidate.AbstractText
  type ReferencedWorks = ReferencedWorks.T
  object ReferencedWorks extends Newtype[String, Pure]
  type InDb = InDb.T
  object InDb extends Newtype[Boolean, Pure]
  type OaLocations = OaLocations.T
  object OaLocations extends Newtype[String, Pure]
  type OaLicence = OaLicence.T
  object OaLicence extends Newtype[String, Pure]
