package com.histmersion
package api.automation.managers

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum ItemOrigin(val wire: ItemOrigin.Wire):
  case Selection extends ItemOrigin(ItemOrigin.Wire("selection"))
  case Person extends ItemOrigin(ItemOrigin.Wire("person"))
  case Chain extends ItemOrigin(ItemOrigin.Wire("chain"))
  case Hint extends ItemOrigin(ItemOrigin.Wire("hint"))

  /** An item an ingest added, by a name no other type takes. */
  case FromIngest extends ItemOrigin(ItemOrigin.Wire("ingest"))

  case Cron extends ItemOrigin(ItemOrigin.Wire("cron"))

object ItemOrigin:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[ItemOrigin] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
