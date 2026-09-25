package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Body of `POST /api/sources/{citekey}/rename`. */
final case class RenameRequest(newCitekey: String) derives KindlingsCodecAsObject
object RenameRequest:
  given Schema[RenameRequest] = KindlingsSchema.derived[RenameRequest].schema

/** How many rows of one table/column a rename rewrote — for the columns that hold a citekey (or a
  * finding id) WITHOUT a foreign key, which the cascade cannot reach. */
final case class RenameCount(table: String, rows: Int) derives KindlingsCodecAsObject
object RenameCount:
  given Schema[RenameCount] = KindlingsSchema.derived[RenameCount].schema

/** What a rename changed, as counted inside its transaction. */
final case class RenameCounts(
    /** Findings whose id moved from `<old>#<n>` to `<new>#<n>`. */
    findings: Int,
    /** Articles whose body or summary linked the old citekey (or a short form of it) and was
      * rewritten — each through the article write path, so each has a new entry in its history. */
    articles: List[String],
    /** Sources whose notes or summary linked the old citekey and were rewritten. */
    sources: List[String],
    /** `[[…]]` links rewritten across those articles and sources. */
    links: Int,
    /** Rows of the columns without a foreign key (events, jobs, place types, attestations…). */
    columns: List[RenameCount],
    /** Parked edges (`unresolved_edges`) that named the NEW citekey and became real edges. */
    edgesPromoted: Int
) derives KindlingsCodecAsObject
object RenameCounts:
  given Schema[RenameCounts] = KindlingsSchema.derived[RenameCounts].schema

/** The answer of a rename: the source under its new citekey (without its body) and the counts. */
final case class SourceRenamed(
    oldCitekey: String,
    newCitekey: String,
    source: SourceDetail,
    counts: RenameCounts,
    /** The Calibre book id, when the original is in Calibre. Calibre never stores our citekey, so
      * nothing there changes — but the host-side originals directory is keyed by citekey. */
    calibreId: Option[String] = None
) derives KindlingsCodecAsObject
object SourceRenamed:
  given Schema[SourceRenamed] = KindlingsSchema.derived[SourceRenamed].schema

/** An old citekey and the source it now names (`GET /api/source-aliases`). */
final case class SourceAlias(alias: String, citekey: String) derives KindlingsCodecAsObject
object SourceAlias:
  given Schema[SourceAlias] = KindlingsSchema.derived[SourceAlias].schema

/** The pure rules of a citekey rename. The database half (existence, aliases, the rewrite itself)
  * is `SourceRepo.rename`, one transaction. */
object Rename:

  /** The new citekey must be a valid citekey AND link-shaped: a key the wikilink resolver does not
    * take for a citekey (`smith-2019`, `x.v2`) would turn every `[[new]]` the rename writes into an
    * article link, and every citation of the source would go dead. */
  def validate(oldCk: String, newRaw: String): Either[ApiError, Citekey] =
    Citekey.of(newRaw).flatMap { ck =>
      if ck == oldCk.trim then Left(ApiError(s"'$ck' is already the citekey of that source", Some(Write.CodeInvalid)))
      else if !ArticleLinks.isCitekeyShaped(ck) then
        Left(ApiError(s"'$ck' is a valid citekey but not a LINK-shaped one — `[[$ck]]` would resolve to an " +
          "article, not the source. Use lower-case letters, digits and '_' with a four-digit year or an " +
          "underscore (e.g. page1959_history)", Some(Write.CodeInvalid)))
      else Right(ck)
    }

  /** Every short form of `ck` the wiki may have written for it — each prefix that ends right before
    * an `_` (`beckman2025` for `beckman2025_ahhiyawa`, `a` and `a_b` for `a_b_c`). The resolver
    * accepts one when it matches a single citekey, so these are the spellings that pointed at the old
    * key and would dangle after a rename. */
  def shortForms(ck: String): List[String] =
    ck.indices.filter(i => i > 0 && ck.charAt(i) == '_').map(ck.take(_)).toList

  /** `<old>#<n>` → `<new>#<n>`; any other id (a finding id that does not follow the convention) is
    * left alone. */
  def findingId(id: String, oldCk: String, newCk: String): Option[String] =
    Option.when(id.startsWith(oldCk + "#"))(newCk + id.drop(oldCk.length))

  /** Replace `oldCk` where it occurs as a WHOLE citekey token inside a composite id (an attestation
    * target such as `src|dst|kind`), never inside a longer key: `ab2020_x` does not match inside
    * `ab2020_xy` or `zab2020_x`. */
  def replaceToken(text: String, oldCk: String, newCk: String): String =
    val token = s"(?<![a-z0-9_.-])${java.util.regex.Pattern.quote(oldCk)}(?![a-z0-9_.-])".r
    token.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement(newCk))
