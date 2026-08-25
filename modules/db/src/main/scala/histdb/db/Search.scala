package histdb.db

import com.augustnagro.magnum.Frag

/** A bound parameter. Typed (rather than `Any`) so tests can assert on it and the writer can bind it
  * without reflection — which also keeps the native image happy. */
enum Param:
  case S(v: String)
  case L(v: Long)
  case I(v: Int)
  case D(v: Double)

/** A composable SQL fragment: text with `?` placeholders plus the parameters that fill them, in order.
  *
  * Magnum's `sql""` interpolator cannot splice one query into another — an interpolated `Frag` is
  * treated as a bind *parameter* (`setObject`), not as SQL — so composition happens here instead, and
  * the finished fragment is handed to Magnum as a single [[Frag]] via [[frag]]. Parameters are always
  * bound, never inlined, so a fragment cannot be an injection vector.
  *
  * This is the ONE fragment type of the `db` module: [[FilterSql]] (`Filter → SQL`), [[Search]] (FTS)
  * and [[AggregateRepo]] all build and splice these, which is what lets `Filter.q` be an FTS
  * predicate instead of an `ILIKE`.
  */
final case class Sql(text: String, params: List[Param] = Nil):
  def ++(that: Sql): Sql   = Sql(text + that.text, params ++ that.params)
  def ++(raw: String): Sql = Sql(text + raw, params)
  def wrap(pre: String, post: String): Sql = Sql(pre + text + post, params)

  /** Hand off to Magnum. `Frag.params` is only used for logging; the writer does the binding. */
  def frag: Frag =
    val ps = params
    Frag(text, ps.map(Sql.value), (stmt: java.sql.PreparedStatement, pos: Int) => Sql.bind(stmt, pos, ps))

object Sql:
  val empty: Sql                          = Sql("", Nil)
  def raw(text: String): Sql              = Sql(text, Nil)
  def of(text: String, ps: Param*): Sql   = Sql(text, ps.toList)

  private def value(p: Param): Any = p match
    case Param.S(v) => v
    case Param.L(v) => v
    case Param.I(v) => v
    case Param.D(v) => v

  private def bind(stmt: java.sql.PreparedStatement, pos: Int, ps: List[Param]): Int =
    var i = pos
    ps.foreach { p =>
      p match
        case Param.S(v) => stmt.setString(i, v)
        case Param.L(v) => stmt.setLong(i, v)
        case Param.I(v) => stmt.setInt(i, v)
        case Param.D(v) => stmt.setDouble(i, v)
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
  def tsQuery(q: String): Sql = Sql.of("websearch_to_tsquery('english', ?)", Param.S(q))

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
      tsQuery(q) ++ Sql.of(", ?) AS snippet", Param.S(opts)) ++
      s" FROM source_chunks fts_b WHERE fts_b.citekey = $alias.citekey AND fts_b.tsv @@ " ++
      tsQuery(q) ++ " ORDER BY ts_rank_cd(fts_b.tsv, " ++ tsQuery(q) ++
      ") DESC, fts_b.ord LIMIT 1) fts ON true"
