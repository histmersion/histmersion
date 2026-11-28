package com.histmersion
package api.catalogue.gazetteer

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.gazetteer.Kind
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.catalogue.Gazetteer.slug
import com.histmersion.api.catalogue.Gazetteer.DatasetName
import com.histmersion.api.catalogue.Gazetteer.PointAreaSources
import com.histmersion.api.catalogue.Gazetteer.pointOnly
import com.histmersion.api.catalogue.GazRecord
import com.histmersion.api.catalogue.Gazetteer.PointAreaKinds

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SourceInfo(
    key: SourceInfo.Key,
    kind: Kind,
    title: SourceInfo.Title,
    file: Option[SourceInfo.File],
    present: SourceInfo.Present,
    pinnedVersion: Option[SourceInfo.PinnedVersion],
    importedVersion: Option[SourceInfo.ImportedVersion] = None,
    importedAt: Option[SourceInfo.ImportedAt] = None,
    places: Option[SourceInfo.Places] = None,
    names: Option[SourceInfo.Names] = None,
    links: Option[SourceInfo.Links] = None,
    licence: Option[SourceInfo.Licence] = None,
    citation: Option[SourceInfo.Citation] = None,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SourceInfo:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type File = File.T
  object File extends Newtype[String, Pure]
  enum Present derives CanEqual:
    case Present, Absent
  object Present extends TwoStates[Present]:
    extension (present: Present) def present: Boolean = present == Present
  type PinnedVersion = PinnedVersion.T
  object PinnedVersion extends Newtype[String, Pure]
  type ImportedVersion = ImportedVersion.T
  object ImportedVersion extends Newtype[String, Pure]
  type ImportedAt = ImportedAt.T
  object ImportedAt extends Newtype[String, Pure]
  type Places = Places.T
  object Places extends Newtype[Int, Pure]
  type Names = Names.T
  object Names extends Newtype[Int, Pure]
  type Links = Links.T
  object Links extends Newtype[Int, Pure]
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]

  def datasetSlug(fileName: File): DatasetName =
    val base = fileName.value
      .replaceAll("(?i)(\\.meta)?\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
      .replaceAll("(?i)\\.lpf$", "")
    DatasetName((slug(base)).take(48))

  def unknownWhenSilent(sourceKey: Key): Boolean =
    Kind
      .ofKey(sourceKey)
      .exists(k => k != Kind.Lpf)

  def dropsPointArea(source: Key, r: GazRecord, kinds: Set[PointAreaKinds]): Boolean =
    PointAreaSources.contains(source) && kinds.map(_.value).contains(r.kind.value) && pointOnly(r.geometry)
