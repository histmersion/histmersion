package histdb.db

import com.augustnagro.magnum.{Frag, FragWriter}

/** A composable SQL fragment: text with `?` placeholders plus the parameters that fill them, in order.
  *
  * Magnum's `sql""` interpolator cannot splice one query into another — an interpolated `Frag` is
  * treated as a bind *parameter* (`setObject`), not as SQL — so composition happens here instead, and
  * the finished fragment is handed to Magnum as a single [[Frag]] via [[frag]]. Parameters are always
  * bound, never inlined, so a fragment cannot be an injection vector.
  */
final case class Sql(text: String, params: List[Any] = Nil):
  def ++(that: Sql): Sql    = Sql(text + that.text, params ++ that.params)
  def ++(raw: String): Sql  = Sql(text + raw, params)
  def frag: Frag            = Frag(text, params, Sql.writer(params))

object Sql:
  /** Raw SQL with no parameters. */
  def raw(text: String): Sql = Sql(text, Nil)

  private def writer(params: List[Any]): FragWriter = (ps, pos) =>
    var i = pos
    params.foreach { p =>
      p match
        case s: String  => ps.setString(i, s)
        case n: Int     => ps.setInt(i, n)
        case n: Long    => ps.setLong(i, n)
        case d: Double  => ps.setDouble(i, d)
        case b: Boolean => ps.setBoolean(i, b)
        case other      => ps.setObject(i, other)
      i += 1
    }
    i

/** Postgres full-text search, as composable SQL fragments (plan §1.4).
  *
  * These are the building blocks the repos assemble; they are deliberately NOT a query, so that
  * `Filter → SQL` (stream 1.1) can drop `q` handling into any `WHERE`/`ORDER BY` it builds:
  *
  * {{{
  * val q = "\"sea peoples\" -egypt"
  * val query = Sql.raw("SELECT s.citekey, fts.snippet, fts.heading FROM sources s ") ++
  *   Search.bestChunkJoin(q, cfg.limits.snippetWords) ++
  *   Sql.raw(" WHERE ") ++ Search.ftsPredicate(q) ++ Sql.raw(" AND ") ++ otherFilters ++
  *   Sql.raw(" ORDER BY ") ++ Search.rankExpr(q) ++ Sql.raw(" DESC")
  * query.frag.query[SearchHit].run()
  * }}}
  *
  * Every fragment takes the alias the `sources` table is bound to (default `s`) and emits its own
  * private aliases (`fts`, `fts_*`) so it cannot collide with the caller's. Splicing a fragment more
  * than once is safe: each occurrence carries its own bind parameter, in textual order — so build the
  * final statement in the order the SQL reads.
  *
  * Query syntax is `websearch_to_tsquery`: bare words are AND-ed, `"quoted phrases"` are phrases,
  * `-word` excludes, `or` alternates. A query that reduces to nothing (only stop-words) matches
  * nothing — that is the honest FTS answer, not an error.
  *
  * Known limit of chunking: an AND is evaluated **within one vector**, so `sea peoples pottery` matches
  * only if all three words are in the title/summary or all three are in the SAME body chunk. Terms
  * scattered across a whole paper do not match. That is the price of staying under tsvector's 1 MB cap;
  * a phrase or a topical query — what people actually type — is unaffected.
  */
object Search:

  /** The parsed query, as a fragment. Spliced once per use site so parameters stay in textual order. */
  def tsQuery(q: String): Sql = Sql("websearch_to_tsquery('english', ?)", List(q))

  /** True when the source matches: its weighted title(A)/summary(B) vector, or ANY of its body
    * chunks. Use in a `WHERE`. */
  def ftsPredicate(q: String, alias: String = "s"): Sql =
    Sql.raw(s"($alias.tsv @@ ") ++ tsQuery(q) ++
      s" OR EXISTS (SELECT 1 FROM source_chunks fts_p WHERE fts_p.citekey = $alias.citekey AND fts_p.tsv @@ " ++
      tsQuery(q) ++ "))"

  /** Relevance: title/summary rank plus the best body chunk's rank. `ts_rank_cd`'s default weights
    * (A = 1.0 … D = 0.1) do the work — chunk lexemes are unweighted, i.e. D — so a title match
    * outranks a body match without any hand-tuned multiplier. Use in an `ORDER BY … DESC`. */
  def rankExpr(q: String, alias: String = "s"): Sql =
    Sql.raw(s"(ts_rank_cd($alias.tsv, ") ++ tsQuery(q) ++
      ") + coalesce((SELECT max(ts_rank_cd(fts_r.tsv, " ++ tsQuery(q) ++
      s")) FROM source_chunks fts_r WHERE fts_r.citekey = $alias.citekey AND fts_r.tsv @@ " ++
      tsQuery(q) ++ "), 0))"

  /** `LEFT JOIN LATERAL` exposing `fts.snippet` (a `ts_headline` of the best-matching chunk, hits
    * wrapped in `**`) and `fts.heading` (that chunk's heading breadcrumb). Both are NULL when only the
    * title/summary matched. Goes straight after `FROM sources <alias>`; `maxWords` comes from config
    * (`limits.snippetWords`). */
  def bestChunkJoin(q: String, maxWords: Int, alias: String = "s"): Sql =
    val opts = s"MaxFragments=2, MaxWords=$maxWords, MinWords=8, StartSel=**, StopSel=**"
    Sql.raw("LEFT JOIN LATERAL (SELECT fts_b.heading AS heading, ts_headline('english', fts_b.body, ") ++
      tsQuery(q) ++ Sql(", ?) AS snippet", List(opts)) ++
      s" FROM source_chunks fts_b WHERE fts_b.citekey = $alias.citekey AND fts_b.tsv @@ " ++
      tsQuery(q) ++ " ORDER BY ts_rank_cd(fts_b.tsv, " ++ tsQuery(q) ++
      ") DESC, fts_b.ord LIMIT 1) fts ON true"
