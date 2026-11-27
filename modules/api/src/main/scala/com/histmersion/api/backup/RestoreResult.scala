package com.histmersion
package api.backup
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** `pg_restore` exits 0 with "errors ignored on restore" when the dump came from a newer `pg_dump` than the server, so
  * the lines it complained about are reported.
  */
final case class RestoreResult(
    file: RestoreResult.File,
    schemaVersion: RestoreResult.SchemaVersion,
    sourcesBefore: RestoreResult.SourcesBefore,
    sourcesAfter: RestoreResult.SourcesAfter,
    durationMs: RestoreResult.DurationMs,
    warnings: List[RestoreResult.Warnings] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RestoreResult:
  export Backup.Result.File
  type SchemaVersion = SchemaVersion.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SchemaVersion extends Newtype[String, Pure]
  type SourcesBefore = SourcesBefore.T
  object SourcesBefore extends Newtype[Int, Pure]
  type SourcesAfter = SourcesAfter.T
  object SourcesAfter extends Newtype[Int, Pure]
  export Backup.Result.DurationMs
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
