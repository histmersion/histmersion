package compilerfixture

object Owned:
  def brokenFlags(rows: List[FlagRecord]): List[FlagRecord] = rows.filterNot(_.flag)
