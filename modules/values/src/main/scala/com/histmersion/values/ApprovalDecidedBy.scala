package com.histmersion.values

/** Who decided an approval. `Autopilot` = the checked auto approvals (their note starts [[AutopilotNote]]); `Auto` =
  * applied the moment it was proposed, by an `agents.approvals.*` mode of `auto` (its result starts [[AutoResult]]);
  * `Person` = everything else that is decided.
  */
enum ApprovalDecidedBy(val wire: String, val describe: String):
  case Person extends ApprovalDecidedBy("person", "decided by a person")
  case Autopilot extends ApprovalDecidedBy("autopilot", "decided by the autopilot's checks")
  case Auto extends ApprovalDecidedBy("auto", "applied when proposed (an auto approval mode)")

object ApprovalDecidedBy:
  /** The note every autopilot decision starts with (`AutopilotPolicy.note`). */
  val AutopilotNote = "autopilot:"

  /** The result of a proposal applied at once (`Approvals.propose`, an `auto` mode). */
  val AutoResult = "applied automatically"

  def parse(s: String): Option[ApprovalDecidedBy] = values.find(_.wire == s.trim.toLowerCase)

  /** Who decided an approval in `state` with this `note` and `result`; `None` while it is open. The SQL that filters
    * and counts by it says the same (`AgentRepo`).
    */
  def of(state: String, note: Option[String], result: Option[String]): Option[ApprovalDecidedBy] =
    if state == ApprovalState.Open.wire then None
    else if note.exists(_.startsWith(AutopilotNote)) then Some(Autopilot)
    else if note.isEmpty && result.exists(_.startsWith(AutoResult)) then Some(Auto)
    else Some(Person)
