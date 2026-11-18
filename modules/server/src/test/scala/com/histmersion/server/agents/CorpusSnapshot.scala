package com.histmersion
package server.agents

import com.augustnagro.magnum.*

import javax.sql.DataSource

/** Every row of the tables an agent run could write — the corpus, the queues, the approvals, the goals and their links
  * — as one fingerprint per table. Two equal snapshots: nothing was written between them (what a dry run promises,
  * #992).
  */
object CorpusSnapshot:

  val Tables: List[String] = List(
    "sources",
    "findings",
    "edges",
    "unresolved_edges",
    "assessments",
    "articles",
    "article_versions",
    "article_links",
    "queue_items",
    "approvals",
    "goals",
    "goal_links",
    "review_ledgers"
  )

  def of(ds: DataSource): Map[String, String] =
    connect(ds):
      Tables.map { t =>
        t -> sql"SELECT coalesce(md5(string_agg(x::text, '|' ORDER BY x::text)), '') || ':' || count(*) FROM ${SqlLiteral(t)} x"
          .query[String]
          .run()
          .head
      }.toMap
