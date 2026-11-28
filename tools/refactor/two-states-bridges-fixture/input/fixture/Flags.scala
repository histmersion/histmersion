package fixture

trait TwoStates[E <: reflect.Enum]:
  def values: Array[E]

trait TwoCases[E <: reflect.Enum]:
  def values: Array[E]

object Flags:
  enum Held derives CanEqual:
    case Held, Free
  object Held extends TwoCases[Held]:
    extension (held: Held) def held: Boolean = held == Held
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    def described: String = "dry run"
  enum Plain derives CanEqual:
    case On, Off
  object Plain:
    extension (plain: Plain) def on: Boolean = plain == On
