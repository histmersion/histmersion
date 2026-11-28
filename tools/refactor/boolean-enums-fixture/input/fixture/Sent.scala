package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure

final case class Sent(dryRun: Sent.DryRun)

object Sent:
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]

  def preview: Sent = Sent(DryRun(true))
  def writes(sent: Sent): Boolean = !sent.dryRun.value
