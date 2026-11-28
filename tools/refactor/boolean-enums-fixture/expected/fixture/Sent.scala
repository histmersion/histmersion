package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.fixture.wire.TwoStates

final case class Sent(dryRun: Sent.DryRun)

object Sent:
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun

  def preview: Sent = Sent(DryRun.DryRun)
  def writes(sent: Sent): Boolean = !sent.dryRun.dryRun
