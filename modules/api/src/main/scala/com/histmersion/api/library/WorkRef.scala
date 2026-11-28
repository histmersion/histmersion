package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.TwoStates

final case class WorkRef(
    doi: Option[Doi] = None,
    openalexId: Option[WorkRef.OpenalexId] = None,
    arxivId: Option[WorkRef.ArxivId] = None,
    title: Option[Title] = None,
    year: Option[Year] = None,
    authors: List[AuthorName] = (Nil).map(AuthorName.apply),
    venue: Option[Source.Venue] = None,
    citedByCount: Option[WorkRef.CitedByCount] = None,
    isOa: WorkRef.IsOa = Source.Candidate.IsOa.Closed,
    oaStatus: Option[WorkRef.OaStatus] = None,
    oaUrl: Option[WorkRef.OaUrl] = None,
    isRetracted: WorkRef.IsRetracted = WorkRef.IsRetracted.Standing,
    workType: Option[WorkRef.WorkType] = None,
    abstractText: Option[WorkRef.AbstractText] = None,
    referencedWorks: List[WorkRef.ReferencedWorks] = Nil,
    citekey: Option[Citekey] = None,
    inDb: WorkRef.InDb = WorkRef.InDb.Missing,
    oaLocations: List[WorkRef.OaLocations] = Nil,
    oaLicence: Option[WorkRef.OaLicence] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

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
  enum IsRetracted derives CanEqual:
    case Retracted, Standing
  object IsRetracted extends TwoStates[IsRetracted]:
    extension (isRetracted: IsRetracted) def isRetracted: Boolean = isRetracted == Retracted
  type WorkType = WorkType.T
  object WorkType extends Newtype[String, Pure]
  export Source.Candidate.AbstractText
  type ReferencedWorks = ReferencedWorks.T
  object ReferencedWorks extends Newtype[String, Pure]
  enum InDb derives CanEqual:
    case InDb, Missing
  object InDb extends TwoStates[InDb]:
    extension (inDb: InDb) def inDb: Boolean = inDb == InDb
  type OaLocations = OaLocations.T
  object OaLocations extends Newtype[String, Pure]
  type OaLicence = OaLicence.T
  object OaLicence extends Newtype[String, Pure]
