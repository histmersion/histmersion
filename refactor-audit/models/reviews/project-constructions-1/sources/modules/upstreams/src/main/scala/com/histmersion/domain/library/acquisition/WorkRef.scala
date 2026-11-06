package com.histmersion.domain.library.acquisition
final case class WorkRef(
    doi: Option[com.histmersion.domain.library.Doi] = None,
    openalexId: Option[WorkRef.OpenalexId] = None,
    arxivId: Option[WorkRef.ArxivId] = None,
    title: Option[WorkRef.Title] = None,
    year: Option[com.histmersion.Year] = None,
    authors: List[WorkRef.Authors] = Nil,
    venue: Option[WorkRef.Venue] = None,
    citedByCount: Option[WorkRef.CitedByCount] = None,
    isOa: WorkRef.IsOa = _root_.com.histmersion.domain.library.acquisition.WorkRef.IsOa(false),
    oaStatus: Option[WorkRef.OaStatus] = None,
    oaUrl: Option[WorkRef.OaUrl] = None,
    isRetracted: WorkRef.IsRetracted = _root_.com.histmersion.domain.library.acquisition.WorkRef.IsRetracted(false),
    workType: Option[WorkRef.WorkType] = None,
    abstractText: Option[WorkRef.AbstractText] = None,
    referencedWorks: List[WorkRef.ReferencedWorks] = Nil,
    oaLocations: List[WorkRef.OaLocations] = Nil,
    oaLicence: Option[WorkRef.OaLicence] = None
)

object WorkRef:
  type OpenalexId = OpenalexId.T
  object OpenalexId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivId = ArxivId.T
  object ArxivId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsOa = IsOa.T
  object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaStatus = OaStatus.T
  object OaStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaUrl = OaUrl.T
  object OaUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorkType = WorkType.T
  object WorkType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AbstractText = AbstractText.T
  object AbstractText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencedWorks = ReferencedWorks.T
  object ReferencedWorks extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaLocations = OaLocations.T
  object OaLocations extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaLicence = OaLicence.T
  object OaLicence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
