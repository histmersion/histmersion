package fixture

trait TwoStates[E <: reflect.Enum]:
  def values: Array[E]

object Flags:
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    def described: String = "dry run"

object Request:
  export Flags.DryRun as Preview

object Uses:
  def all(runs: List[Flags.DryRun]): List[Boolean] = runs.map(_.dryRun)
  def second(t: (Int, Flags.DryRun)): Boolean = t._2.dryRun
  def aliased: Request.Preview = Request.Preview.DryRun
  def parsed(flag: Boolean): Flags.DryRun = (if flag then Flags.DryRun.DryRun else Flags.DryRun.Apply)
