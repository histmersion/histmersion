package ownerfixture.feature

import ownerfixture.old.*

object Helper:
  def value = Stay.value

final class Sample:
  def value = Helper.value
