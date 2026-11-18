package demo

object Elsewhere:
  // C2 a caller that does not see the wrapper's names gets their imports
  def twice(s: String) = Parse.number(s).map(_ * 2)
