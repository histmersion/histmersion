package com.histmersion.util.jobs

/** A job that runs as checkpointed steps (an agent run): what its row records when it is created. Its `status`, `kind`
  * and `params` are the owner's words, stored as given; `citekey` is the source it works on, when it works on one.
  */
final case class RunRow(
    kind: String,
    status: String,
    pipeline: String,
    pipelineVersion: Int,
    targetKind: String,
    target: String,
    citekey: Option[String],
    model: Option[String],
    budgetUsd: BigDecimal,
    budgetTokens: Long,
    params: String,
    acquireItem: Option[Int],
    goal: Option[String],
    parent: Option[String],
    origin: String,
    dryRun: Boolean,
    managerId: Option[String],
    managerItem: Option[Long]
)
