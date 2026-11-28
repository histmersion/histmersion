package fixture.automation

object ApprovalModes:
  def configured(raw: String): Approval.Mode = Approval.parse(raw).getOrElse(Approval.Mode.Manual)
