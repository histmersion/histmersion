package constructorfixture

final class Connection(val host: String, val port: Int)

enum Mode(val wire: String):
  case Off extends Mode(???)

final class Binary(val bytes: Array[Byte])
