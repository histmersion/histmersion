package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class Classification(
    @description("3-6 sentences: what the source argues and on what evidence") summary: Classification.Summary,
    topics: List[Classification.Topic] = Nil,
    regions: List[Classification.Region] = Nil,
    @description("lowercase_snake_case; prefer tags the facets tool lists") tags: List[Classification.Tag] = Nil,
    @description("The deep-time span the source is ABOUT (not its publication date); null if none") period: Option[StatedPeriod],
    @description("foundational | current | contested | superseded | debunked | retracted | unassessed") status: Source.Status.Wire,
    @description("Why that status, from the text; empty if unassessed") statusEvidence: Classification.StatusEvidence
) derives JsonCodec, KindlingsSchema, Schema

object Classification:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type StatusEvidence = StatusEvidence.T
  object StatusEvidence extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
