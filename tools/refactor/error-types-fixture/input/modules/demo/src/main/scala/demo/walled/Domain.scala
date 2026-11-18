package demo.walled

import demo.Refusals

object Domain:
  // C4 a caller in a layer that may not import the wrapper's owner
  def peek(s: String): Option[Int] = Refusals.fenced(s).toOption
