package com.histmersion.domain.library.search

import com.histmersion.util.database.given
import com.histmersion.util.database.{Param, Sql}

/** Postgres full-text search, as composable SQL fragments (plan §1.4).
  *
  * These are the building blocks the repos assemble; they are deliberately NOT a query, so that `Filter → SQL` (stream
  * 1.1) can drop `q` handling into any `WHERE`/`ORDER BY` it builds:
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
  * Every fragment takes the alias the `sources` table is bound to (default `s`) and emits its own private aliases
  * (`fts`, `fts_*`) so it cannot collide with the caller's. Splicing a fragment more than once is safe: each occurrence
  * carries its own bind parameter, in textual order — so build the final statement in the order the SQL reads.
  *
  * Query syntax is `websearch_to_tsquery`: bare words are AND-ed, `"quoted phrases"` are phrases, `-word` excludes,
  * `or` alternates. A query that reduces to nothing (only stop-words) matches nothing — that is the honest FTS answer,
  * not an error.
  *
  * Known limit of chunking: an AND is evaluated **within one vector**, so `sea peoples pottery` matches only if all
  * three words are in the title/summary or all three are in the SAME body chunk. Terms scattered across a whole paper
  * do not match. That is the price of staying under tsvector's 1 MB cap; a phrase or a topical query — what people
  * actually type — is unaffected.
  */
object Search:
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SnippetWords = SnippetWords.T
  object SnippetWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The parsed query, as a fragment. Spliced once per use site so parameters stay in textual order. */
  type FtsPredicateQ = _root_.com.histmersion.domain.library.search.Search.Query
  val FtsPredicateQ: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query
  type RankExprQ = _root_.com.histmersion.domain.library.search.Search.Query
  val RankExprQ: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query
  type BestChunkJoinQ = _root_.com.histmersion.domain.library.search.Search.Query
  val BestChunkJoinQ: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query
  type BestChunkCteQ = _root_.com.histmersion.domain.library.search.Search.Query
  val BestChunkCteQ: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query
  type RankExprCteQ = _root_.com.histmersion.domain.library.search.Search.Query
  val RankExprCteQ: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query
  type SnippetsQueryQ = _root_.com.histmersion.domain.library.search.Search.Query
  val SnippetsQueryQ: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query

  def tsQuery(q: com.histmersion.domain.library.search.Search.Query): Sql = Sql.of(Sql.OfText("websearch_to_tsquery('english', ?)"), Param.S(Param.S.V(q.value)))

  /** True when the source matches: its weighted title(A)/summary(B) vector, or ANY of its body chunks. Use in a
    * `WHERE`.
    */
  def ftsPredicate(q: com.histmersion.domain.library.search.Search.FtsPredicateQ, alias: String = "s"): Sql =
    Sql.raw(Sql.RawText(s"($alias.tsv @@ ")) ++ tsQuery(q) ++
      s" OR EXISTS (SELECT 1 FROM source_chunks fts_p WHERE fts_p.citekey = $alias.citekey AND fts_p.tsv @@ " ++
      tsQuery(q) ++ "))"

  /** Relevance: title/summary rank plus the best body chunk's rank. `ts_rank_cd`'s default weights (A = 1.0 … D = 0.1)
    * do the work — chunk lexemes are unweighted, i.e. D — so a title match outranks a body match without any hand-tuned
    * multiplier. Use in an `ORDER BY … DESC`.
    */
  def rankExpr(q: com.histmersion.domain.library.search.Search.RankExprQ, alias: String = "s"): Sql =
    Sql.raw(Sql.RawText(s"(ts_rank_cd($alias.tsv, ")) ++ tsQuery(q) ++
      ") + coalesce((SELECT max(ts_rank_cd(fts_r.tsv, " ++ tsQuery(q) ++
      s")) FROM source_chunks fts_r WHERE fts_r.citekey = $alias.citekey AND fts_r.tsv @@ " ++
      tsQuery(q) ++ "), 0))"

  /** Headings whose chunks are reference lists, acknowledgements, etc. — they match many queries but carry no useful
    * content. Down-weighted in [[bestChunkJoin]] (preferred only when no body chunk matches). The pattern is compiled
    * into the SQL `~*` operator, not the JVM regex.
    */
  val BoilerplateHeading = "references|bibliography|notes|index|acknowledg"

  /** `LEFT JOIN LATERAL` exposing `fts.snippet` (a `ts_headline` of the best-matching chunk, hits wrapped in `**`) and
    * `fts.heading` (that chunk's heading breadcrumb). Both are NULL when only the title/summary matched. Goes straight
    * after `FROM sources <alias>`; `maxWords` comes from config (`limits.snippetWords`).
    *
    * Chunks whose heading matches [[BoilerplateHeading]] are ranked at 0.001× their actual score, so a "References"
    * section only surfaces when nothing else matched. `MaxFragments=1` returns a single contiguous excerpt;
    * `HighlightAll=false` highlights the first match only.
    *
    * **Deprecated in favour of the two-phase approach**: [[bestChunkCte]] + [[snippetsQuery]]. Kept for reference; new
    * callers should use the CTE path.
    */
  def bestChunkJoin(q: com.histmersion.domain.library.search.Search.BestChunkJoinQ, maxWords: com.histmersion.domain.library.search.Search.SnippetWords, alias: String = "s"): Sql =
    val maxW = math.max(2, maxWords.value)
    val minW = math.max(1, math.min(8, maxW - 1))
    val opts = s"MaxFragments=1, MaxWords=$maxW, MinWords=$minW, StartSel=**, StopSel=**, HighlightAll=false"
    Sql.raw(Sql.RawText("LEFT JOIN LATERAL (SELECT fts_b.heading AS heading, ts_headline('english', fts_b.body, ")) ++
      tsQuery(q) ++ Sql.of(Sql.OfText(", ?) AS snippet"), Param.S(Param.S.V(opts))) ++
      s" FROM source_chunks fts_b WHERE fts_b.citekey = $alias.citekey AND fts_b.tsv @@ " ++
      tsQuery(q) ++ " ORDER BY CASE WHEN fts_b.heading ~* '" ++ BoilerplateHeading ++
      "' THEN ts_rank_cd(fts_b.tsv, " ++ tsQuery(q) ++ ") * 0.001 ELSE ts_rank_cd(fts_b.tsv, " ++
      tsQuery(q) ++ ") END DESC, fts_b.ord LIMIT 1) fts ON true"

  // ── two-phase FTS: CTE for ranking, deferred snippets for the page only ─────────────────────

  /** A CTE that finds the best-ranking chunk per citekey among ALL chunks matching the query. Scans `source_chunks`
    * ONCE (via the GIN index on `tsv`), then `DISTINCT ON (citekey)` picks the best-scoring chunk per source. The CTE
    * exposes `rank` for ordering and `citekey` for the join; snippets are NOT computed here — they are deferred to
    * [[snippetsQuery]] so that `ts_headline` runs only for the page's sources, not every matching source.
    *
    * Boilerplate headings (references, bibliography, etc.) are penalised at 0.001x, matching the behaviour of
    * [[bestChunkJoin]].
    */
  def bestChunkCte(q: com.histmersion.domain.library.search.Search.BestChunkCteQ): Sql =
    // `fts_m` ranks each matching chunk ONCE (MATERIALIZED stops Postgres inlining it back into the
    // sort key): `ts_rank_cd` de-TOASTs the chunk's tsvector, and evaluating it three times per
    // chunk (rank + both CASE arms) tripled the cost of a common word — 6.7k chunks for "bronze".
    Sql.raw(com.histmersion.util.database.Sql.RawText("WITH fts_m AS MATERIALIZED (SELECT citekey, ord, heading, ts_rank_cd(tsv, ")) ++ tsQuery(q) ++
      ") AS r FROM source_chunks WHERE tsv @@ " ++ tsQuery(q) ++ "), " ++
      "best_chunks AS (SELECT DISTINCT ON (citekey) citekey, r AS rank FROM fts_m" ++
      " ORDER BY citekey, CASE WHEN heading ~* '" ++ BoilerplateHeading ++
      "' THEN r * 0.001 ELSE r END DESC, ord) "

  /** Relevance ranking using the precomputed `best_chunks` CTE instead of a per-source correlated subquery. Use in
    * `ORDER BY … DESC` when [[bestChunkCte]] is the query's CTE. Falls back gracefully: `coalesce(bc.rank, 0)` when the
    * source matched only on title/summary.
    */
  def rankExprCte(q: com.histmersion.domain.library.search.Search.RankExprCteQ, alias: String = "s"): Sql =
    Sql.raw(Sql.RawText(s"(ts_rank_cd($alias.tsv, ")) ++ tsQuery(q) ++ ") + coalesce(bc.rank, 0))"

  /** Compute the snippet and heading for a specific set of citekeys. Uses a two-level query:
    *
    *   1. An inner subquery (`DISTINCT ON (citekey)`) finds the `(citekey, ord)` of each source's best-matching chunk —
    *      no `ts_headline`, so this is cheap.
    *   2. The outer query joins back to `source_chunks` on `(citekey, ord)` and computes `ts_headline` for just those N
    *      rows (one per citekey on the page).
    *
    * This avoids the trap of running `ts_headline` inside `DISTINCT ON`, which evaluates it for EVERY matching chunk
    * before the dedup — hundreds of calls discarded.
    *
    * Call this AFTER the page is known so that `ts_headline` runs only for the page's sources (typically 10–25), not
    * every matching source in the corpus.
    *
    * Returns `(citekey, heading, snippet)` rows. An empty `citekeys` list returns an empty fragment that matches
    * nothing (the caller should skip the query).
    */
  def snippetsQuery(q: com.histmersion.domain.library.search.Search.SnippetsQueryQ, maxWords: com.histmersion.domain.library.search.Search.SnippetWords, citekeys: List[com.histmersion.domain.library.Citekey]): Sql =
    if citekeys.isEmpty then return Sql.raw(com.histmersion.util.database.Sql.RawText("SELECT NULL::text, NULL::text, NULL::text WHERE FALSE"))
    val maxW = math.max(2, maxWords.value)
    val minW = math.max(1, math.min(8, maxW - 1))
    val opts = s"MaxFragments=1, MaxWords=$maxW, MinWords=$minW, StartSel=**, StopSel=**, HighlightAll=false"
    val qmarks = List.fill(citekeys.size)("?").mkString(", ")
    // inner: find the best chunk (citekey, ord) per source — no ts_headline
    val inner = Sql.raw(com.histmersion.util.database.Sql.RawText("SELECT DISTINCT ON (citekey) citekey, ord")) ++
      Sql(Sql.Text(" FROM source_chunks WHERE citekey IN (" + qmarks + ") AND tsv @@ "), citekeys.map(c => Param.S(Param.S.V(c.value)))) ++
      tsQuery(q) ++ " ORDER BY citekey, CASE WHEN heading ~* '" ++ BoilerplateHeading ++
      "' THEN ts_rank_cd(tsv, " ++ tsQuery(q) ++ ") * 0.001 ELSE ts_rank_cd(tsv, " ++
      tsQuery(q) ++ ") END DESC, ord"
    // outer: ts_headline only for those N rows
    Sql.raw(Sql.RawText("SELECT sc.citekey, sc.heading, ts_headline('english', sc.body, ")) ++
      tsQuery(q) ++ Sql.of(Sql.OfText(", ?) AS snippet"), Param.S(Param.S.V(opts))) ++
      " FROM source_chunks sc INNER JOIN (" ++ inner ++
      ") best ON best.citekey = sc.citekey AND best.ord = sc.ord"
