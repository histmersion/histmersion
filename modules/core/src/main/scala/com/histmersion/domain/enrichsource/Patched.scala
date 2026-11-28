package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Patched(
    findingsAdded: Patched.FindingsAdded,
    fieldsSet: List[Patched.FieldsSet],
    statusProposal: Option[Patched.StatusProposal]
) derives JsonCodec

object Patched:
  type FindingsAdded = FindingsAdded.T
  object FindingsAdded extends Newtype[Int, Pure]
  type FieldsSet = FieldsSet.T
  object FieldsSet extends Newtype[String, Pure]
  type StatusProposal = StatusProposal.T
  object StatusProposal extends Newtype[String, Pure]
