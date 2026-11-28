package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AuthorName
import com.histmersion.domain.EnrichSource.Topic
import com.histmersion.domain.EnrichSource.Tag
import com.histmersion.domain.EnrichSource.Region
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ListPatch
import com.histmersion.api.library.FindingsPatch
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Finding
import com.histmersion.domain.EnrichSource.PatchNotes
import com.histmersion.domain.EnrichSource.Description

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Loaded(
    citekey: Citekey,
    title: Loaded.Title,
    authors: List[AuthorName],
    year: Option[com.histmersion.Year],
    venue: Option[Loaded.Venue],
    status: Source.Status.Wire,
    hasSummary: Loaded.HasSummary,
    hasNotes: Loaded.HasNotes,
    hasPeriod: Loaded.HasPeriod,
    period: Option[Period],
    topics: List[Topic],
    regions: List[Region],
    existingClaims: List[Loaded.ExistingClaims],
    updatedAt: Option[Source.UpdatedAt],
    head: Loaded.Head,
    windows: List[Window],
    tags: List[Tag] = Nil,
    doi: Option[Doi] = None
) derives JsonCodec

object Loaded:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type HasSummary = HasSummary.T
  object HasSummary extends Newtype[Boolean, Pure]
  type HasNotes = HasNotes.T
  object HasNotes extends Newtype[Boolean, Pure]
  type HasPeriod = HasPeriod.T
  object HasPeriod extends Newtype[Boolean, Pure]
  type ExistingClaims = ExistingClaims.T
  object ExistingClaims extends Newtype[String, Pure]
  type Head = Head.T
  object Head extends Newtype[String, Pure]

  extension (l: Loaded)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def patch(
        c: Classified,
        findings: List[Finding.Input],
        notes: Option[PatchNotes]
    ): (Source.Patch, List[Description], Option[Description]) =
      def merge[A](have: List[A], add: List[A]): Option[List[A]] =
        Option.when(add.exists(a => !have.contains(a)))((have ++ add).distinct)
      val setStatus = Option.when(
        l.status == Source.Status.Unassessed.wire &&
          (c.status == Source.Status.Current || c.status == Source.Status.Foundational)
      )(c.status)
      val proposal =
        Option.when(setStatus.isEmpty && c.status != Source.Status.Unassessed && c.status.wire != l.status)(
          Description(s"${c.status.wire}: ${c.statusEvidence}")
        )
      val p = Source.Patch(
        metadata = setStatus.map(s => MetadataPatch(status = Some(s))),
        summary = Option.when(!l.hasSummary.value)(Source.Summary(c.summary.value)),
        notes = notes.filter(_ => !l.hasNotes.value).map(n => Source.Notes(n.value)),
        topics = merge(l.topics, c.topics).map(_.map(t => Source.Topics(t.value))),
        regions = merge(l.regions, c.regions).map(_.map(r => Source.Regions(r.value))),
        period = c.period.filter(_ => !l.hasPeriod.value).map(p => Period.Input(p.start, p.end)),
        tags = Some(c.tags.filterNot(l.tags.contains))
          .filter(_.nonEmpty)
          .map(t => ListPatch(add = t.map(x => ListPatch.Add(x.value)))),
        findings = Option.when(findings.nonEmpty)(FindingsPatch(add = findings)),
        ifUpdatedAt = l.updatedAt
      )
      val fields = List(
        p.metadata.map(_ => "status"),
        p.summary.map(_ => "summary"),
        p.notes.map(_ => "notes"),
        p.topics.map(_ => "topics"),
        p.regions.map(_ => "regions"),
        p.period.map(_ => "period"),
        p.tags.map(_ => "tags"),
        p.findings.map(f => s"${f.add.size} finding(s)")
      ).flatten
      (p, fields.map(Description(_)), proposal)
