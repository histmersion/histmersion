package enumfixture

enum Payload:
  case Named(name: String)
  case Number(value: Long)
  case Empty

enum Existing(val tag: String):
  case Data(n: Int) extends Existing(???)

object Existing:
  val marker = 1
