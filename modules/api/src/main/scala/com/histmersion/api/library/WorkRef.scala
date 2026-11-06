package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class WorkRef(
    doi: Option[com.histmersion.api.library.Doi] = None,
    openalexId: Option[WorkRef.OpenalexId] = None,
    arxivId: Option[WorkRef.ArxivId] = None,
    title: Option[com.histmersion.api.library.Title] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
    venue: Option[com.histmersion.api.library.Source.Venue] = None,
    citedByCount: Option[WorkRef.CitedByCount] = None,
    isOa: WorkRef.IsOa = _root_.com.histmersion.api.library.Source.Candidate.IsOa(false),
    oaStatus: Option[WorkRef.OaStatus] = None,
    oaUrl: Option[WorkRef.OaUrl] = None,
    isRetracted: WorkRef.IsRetracted = _root_.com.histmersion.api.library.WorkRef.IsRetracted(false),
    workType: Option[WorkRef.WorkType] = None,
    abstractText: Option[WorkRef.AbstractText] = None,
    referencedWorks: List[WorkRef.ReferencedWorks] = Nil,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    inDb: WorkRef.InDb = _root_.com.histmersion.api.library.WorkRef.InDb(false),
    oaLocations: List[WorkRef.OaLocations] = Nil,
    oaLicence: Option[WorkRef.OaLicence] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object WorkRef:
  type OpenalexId = OpenalexId.T
  object OpenalexId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivId = com.histmersion.api.library.WorkRef.ArxivId
  val ArxivId: _root_.com.histmersion.api.library.WorkRef.ArxivId.type = _root_.com.histmersion.api.library.WorkRef.ArxivId
  type CitedByCount = com.histmersion.api.library.Source.CitedByCount
  val CitedByCount: _root_.com.histmersion.api.library.Source.CitedByCount.type = _root_.com.histmersion.api.library.Source.CitedByCount
  type IsOa = com.histmersion.api.library.Source.Candidate.IsOa
  val IsOa: _root_.com.histmersion.api.library.Source.Candidate.IsOa.type = _root_.com.histmersion.api.library.Source.Candidate.IsOa
  type OaStatus = OaStatus.T
  object OaStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaUrl = OaUrl.T
  object OaUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorkType = WorkType.T
  object WorkType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AbstractText = com.histmersion.api.library.Source.Candidate.AbstractText
  val AbstractText: _root_.com.histmersion.api.library.Source.Candidate.AbstractText.type = _root_.com.histmersion.api.library.Source.Candidate.AbstractText
  type ReferencedWorks = ReferencedWorks.T
  object ReferencedWorks extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InDb = InDb.T
  object InDb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaLocations = OaLocations.T
  object OaLocations extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaLicence = OaLicence.T
  object OaLicence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
