package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.acquirerecheck.LastCheck

import com.histmersion.domain.AcquireRecheck

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

object AcquireAttemptRow:
  type Id = Id.T
  object Id extends Newtype[Int, Pure]

  val LegacyWire: domain.acquirerecheck.Upshot.Wire = domain.acquirerecheck.Upshot.Wire("legacy")
  val NoNote: AcquireAttempt.Outcome.LegacyNote = AcquireAttempt.Outcome.LegacyNote("")

  def unreadable(at: Instant): AcquireAttempt =
    AcquireAttempt(
      at,
      AcquireAttempt.Trigger.Legacy,
      LastCheck.Generation("unreadable"),
      AcquireAttempt.Outcome.Legacy(
        AcquireAttempt.Outcome.LegacyNote("this attempt was stored in a shape this version cannot read"),
        AcquireAttempt.Tries.One
      ),
      Vector.empty,
      None
    )

  import Trigger.Wire

  enum Trigger(val wire: Wire, val domain: AcquireAttempt.Trigger) derives CanEqual:
    case PersonIngest extends Trigger(Wire("person-ingest"), AcquireAttempt.Trigger.PersonIngest)
    case WatcherBacklog extends Trigger(Wire("watcher-backlog"), AcquireAttempt.Trigger.WatcherBacklog)
    case Recheck extends Trigger(Wire("recheck"), AcquireAttempt.Trigger.Recheck)
    case Legacy extends Trigger(Wire("legacy"), AcquireAttempt.Trigger.Legacy)

  object Trigger:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(w: Wire): Option[Trigger] = values.find(_.wire == w)

    def of(t: AcquireAttempt.Trigger): Trigger = t match
      case AcquireAttempt.Trigger.PersonIngest   => PersonIngest
      case AcquireAttempt.Trigger.WatcherBacklog => WatcherBacklog
      case AcquireAttempt.Trigger.Recheck        => Recheck
      case AcquireAttempt.Trigger.Legacy         => Legacy
