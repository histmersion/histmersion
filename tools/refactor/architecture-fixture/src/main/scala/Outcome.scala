package com.histmersion.inventoryfixture

sealed trait Outcome
final case class Accepted(value: String) extends Outcome
case object Rejected extends Outcome
