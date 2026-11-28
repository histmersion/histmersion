package fixture.automation

object Approval:
  enum Mode derives CanEqual:
    case Manual, Auto

  object Mode:
    def configured(raw: String): Approval.Mode = Approval.parse(raw).getOrElse(Manual)

  def parse(raw: String): Option[Mode] = Mode.values.find(_.toString.equalsIgnoreCase(raw))
