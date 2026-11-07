package com.histmersion.cli

import io.github.iltotore.iron.*

/** The extension point a new plugin contributes to: its name on the command line, the suffix of the contributed class's
  * name, and what `histmersion plugin add` reports it provides.
  */
enum PluginKind(val name: PluginKind.Name, val classSuffix: PluginKind.ClassSuffix, val provides: PluginKind.Provides):
  case SourceFetcher extends PluginKind("source-fetcher", "Fetcher", "source-fetcher")
  case MediaCollection extends PluginKind("media-collection", "Collection", "media-collection")
  case EventCatalogue extends PluginKind("event-catalogue", "Catalogue", "event-catalogue")
  case AiProvider extends PluginKind("ai-provider", "Provider", "ai-provider")
  case SecretVault extends PluginKind("secret-vault", "Vault", "secret-vault")

object PluginKind:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassSuffix = ClassSuffix.T
  object ClassSuffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provides = Provides.T
  object Provides extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val names: String = values.map(_.name).mkString(", ")
  def parse(raw: com.histmersion.cli.PluginKind.Text): Either[com.histmersion.cli.PluginKind.Problem, PluginKind] =
    values.find(_.name == raw.trim).toRight(s"unknown plugin kind '$raw' (one of: $names)")
