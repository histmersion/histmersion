package fixture.automation

object Approval:
  enum Mode derives CanEqual:
    case Manual, Auto

  def parse(raw: String): Option[Mode] = Mode.values.find(_.toString.equalsIgnoreCase(raw))
