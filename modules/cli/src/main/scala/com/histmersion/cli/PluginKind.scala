package com.histmersion.cli

import io.github.iltotore.iron.*

/** The extension point a new plugin contributes to: its name on the command line, the suffix of the contributed class's
  * name, and what `histmersion plugin add` reports it provides.
  */
enum PluginKind(val name: String, val classSuffix: String, val provides: String):
  case SourceFetcher extends PluginKind("source-fetcher", "Fetcher", "source-fetcher")
  case MediaCollection extends PluginKind("media-collection", "Collection", "media-collection")
  case EventCatalogue extends PluginKind("event-catalogue", "Catalogue", "event-catalogue")
  case AiProvider extends PluginKind("ai-provider", "Provider", "ai-provider")
  case SecretVault extends PluginKind("secret-vault", "Vault", "secret-vault")

object PluginKind:
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val names: com.histmersion.cli.PluginKind.Names = values.map(_.name).mkString(", ")
  def parse(raw: com.histmersion.cli.PluginKind.ParseRaw): Either[com.histmersion.cli.PluginKind.ParseResultPart1, PluginKind] =
    values.find(_.name == raw.trim).toRight(s"unknown plugin kind '$raw' (one of: $names)")
