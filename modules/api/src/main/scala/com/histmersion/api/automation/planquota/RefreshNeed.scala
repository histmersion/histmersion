package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

enum RefreshNeed(val text: RefreshNeed.Text):
  case StaleOnly extends RefreshNeed(RefreshNeed.Text("the reading is stale"))

  case NoReading extends RefreshNeed(RefreshNeed.Text("no reading yet"))

object RefreshNeed:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
