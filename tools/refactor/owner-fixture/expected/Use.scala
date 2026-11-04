package ownerfixture.consumer

import ownerfixture.feature.{Helper as Renamed, Sample}
import ownerfixture.old.{Stay}

object Use:
  val a = Renamed.value
  val b: Sample = new Sample
  val c = ownerfixture.feature.Helper.value
  val d = Stay.value

object Lookalike:
  object Helper:
    val value = 9
  val untouched = Helper.value
