package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Period
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Loaded(
    citekey: Citekey,
    topics: List[Loaded.Topic],
    period: Option[Period],
    windows: List[Window],
    status: Status = Status.Open,
    note: Loaded.Note = Loaded.Note("")
) derives CanEqual, JsonCodec

object Loaded:
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
