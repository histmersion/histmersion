package com.histmersion.values

/** A change an agent run proposed that a person may have to accept before code applies it: a source's status, a typed
  * disagreement edge between two sources, a goal's plan, or an article a run compiled. `blocking` = the run WAITS for
  * the decision (`waiting_approval`) and applies it itself when it resumes; the other kinds are applied by the decision
  * and the run never waits.
  */
enum ApprovalKind(val wire: String, val blocking: Boolean):
  case StatusChange extends ApprovalKind("status-change", false)
  case Edge extends ApprovalKind("edge", false)
  case GoalPlan extends ApprovalKind("goal-plan", true)
  case ArticlePublish extends ApprovalKind("article-publish", true)

  /** An article's inferred coverage filter (infer-filter): approving records it on the article. */
  case ArticleFilter extends ApprovalKind("article-filter", false)

  /** The articles planned for a research area (propose-articles): approving lets its seeding manager compile them.
    */
  case ArticlePlan extends ApprovalKind("article-plan", false)

  /** A structured action for a process manager (hold, prioritise, skip, a settings change) that a MODEL proposed from a
    * hint (phase 3): applied only when a person approves it. `subject` = the manager.
    */
  case HintEffect extends ApprovalKind("hint-effect", false)

  /** About a goal or an article (`subject`), not a source. */
  def aboutSubject: Boolean = this != StatusChange && this != Edge

  /** Its proposal is an article's coverage filter. */
  def proposesFilter: Boolean = this == ArticleFilter

  /** Its proposal is a plan of articles. */
  def proposesArticles: Boolean = this == ArticlePlan

object ApprovalKind:
  def parse(s: String): Option[ApprovalKind] = values.find(_.wire == s.trim.toLowerCase)

  given WireEnumValues[ApprovalKind]:
    def all: Array[ApprovalKind] = ApprovalKind.values
    def wire(e: ApprovalKind): String = e.wire
    def fromWire(s: String): Option[ApprovalKind] = parse(s)
