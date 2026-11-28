package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import com.histmersion.api.research.CitedSource
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiquePair
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Snapshot(
    slug: Snapshot.Slug,
    title: Snapshot.Title,
    kind: Snapshot.Kind,
    body: Snapshot.Body,
    updatedAt: Option[Snapshot.UpdatedAt],
    truncated: Snapshot.Truncated,
    coverage: Option[Coverage] = None,
    cited: List[CitedSource] = Nil,
    critiques: List[CritiquePair] = Nil
) derives JsonCodec

object Snapshot:
  export Article.Kind.Wire as Kind
  export Article.Body
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type Truncated = Truncated.T
  object Truncated extends Newtype[Boolean, Pure]
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
