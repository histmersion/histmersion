package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Shared(
    left: Shared.Left,
    cap: Shared.Cap,
    backlogPending: Shared.BacklogPending,
    fetchedToday: Shared.FetchedToday
)

object Shared:
  type Left = Left.T
  object Left extends Newtype[Int, Pure]
  type Cap = Cap.T
  object Cap extends Newtype[Int, Pure]
  type BacklogPending = BacklogPending.T
  object BacklogPending extends Newtype[Int, Pure]
  type FetchedToday = FetchedToday.T
  object FetchedToday extends Newtype[Int, Pure]
