package ownerfixture.consumer

import ownerfixture.old.{Helper as Renamed, Sample, Stay}

object Use:
  val a = Renamed.value
  val b: Sample = new Sample
  val c = ownerfixture.old.Helper.value
  val d = Stay.value

object Lookalike:
  object Helper:
    val value = 9
  val untouched = Helper.value
