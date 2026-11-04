package operationinventoryfixture

object Duplicates:
  def sameLine(total: Total): (Total, Total) = (total.copy(value = 1), total.copy(value = 1))
