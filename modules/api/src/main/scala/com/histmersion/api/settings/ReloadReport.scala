package com.histmersion
package api.settings
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.TwoStates

final case class ReloadReport(
    published: ReloadReport.Published,
    generation: ReloadReport.Generation,
    applied: List[ReloadReport.Applied] = Nil,
    restartOnly: List[ReloadReport.RestartOnly] = Nil,
    rebuilt: List[ReloadReport.Rebuilt] = Nil,
    plugins: List[ReloadReport.Plugins] = Nil,
    draining: List[ReloadReport.Draining] = Nil,
    errors: List[ReloadReport.Errors] = Nil,
    warnings: List[ReloadReport.Warnings] = Nil,
    file: Option[ReloadReport.File] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def ok: Boolean = (errors.isEmpty)

object ReloadReport:
  enum Published derives CanEqual:
    case Published, Unpublished
  object Published extends TwoStates[Published]:
    extension (published: Published) def published: Boolean = published == Published
  type Generation = Generation.T
  object Generation extends Newtype[Long, Pure]
  type Applied = Applied.T
  object Applied extends Newtype[String, Pure]
  type RestartOnly = RestartOnly.T
  object RestartOnly extends Newtype[String, Pure]
  type Rebuilt = Rebuilt.T
  object Rebuilt extends Newtype[String, Pure]
  type Plugins = Plugins.T
  object Plugins extends Newtype[String, Pure]
  type Draining = Draining.T
  object Draining extends Newtype[String, Pure]
  type Errors = Errors.T
  object Errors extends Newtype[String, Pure]
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
  type File = File.T
  object File extends Newtype[String, Pure]
