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
    def apply(value: Boolean): Held = if value then Held else Free
    extension (x: Held) def value: Boolean = x == Held
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    def apply(value: Boolean): DryRun = if value then DryRun else Apply
    extension (x: DryRun) def value: Boolean = x == DryRun
    def described: String = "dry run"
  enum Plain derives CanEqual:
    case On, Off
  object Plain:
    extension (plain: Plain) def on: Boolean = plain == On
