package compilerfixture

object Clash:
  trait Vault
  object Vault:
    val outer = 2
  enum Ref:
    case Vault(value: String)
    def shown: String = this match
      case Vault(value) => value
  object Ref:
    object Vault:
      val marker = 1
