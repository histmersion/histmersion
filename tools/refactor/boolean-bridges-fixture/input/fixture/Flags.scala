package fixture

trait TwoStates[E <: reflect.Enum]:
  def values: Array[E]

object Flags:
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    def apply(value: Boolean): DryRun = if value then DryRun else Apply
    extension (x: DryRun) def value: Boolean = x == DryRun
    def described: String = "dry run"

object Request:
  export Flags.DryRun as Preview

object Uses:
  def all(runs: List[Flags.DryRun]): List[Boolean] = runs.map(_.value)
  def second(t: (Int, Flags.DryRun)): Boolean = t._2.value
  def aliased: Request.Preview = Request.Preview(true)
  def parsed(flag: Boolean): Flags.DryRun = Flags.DryRun(flag)
