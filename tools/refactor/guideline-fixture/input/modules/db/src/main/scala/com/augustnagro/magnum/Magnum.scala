package com.augustnagro.magnum

// a stand-in for Magnum's writes (G94 discardedOutcome): a connection, a fragment and its update
trait DbCon
final class Update(val text: String):
  def run()(using DbCon): Int = text.length
final class Frag(val text: String):
  def update: Update = Update(text)
extension (sc: StringContext) def sql(args: Any*): Frag = Frag(sc.parts.mkString("?"))
