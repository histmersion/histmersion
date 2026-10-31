package com.histmersion.util.data.exchange

import com.histmersion.util.data.DataOwner

/** A kind of record a bundle carries for its owner. */
trait CarriedKind:
  def owner: DataOwner
