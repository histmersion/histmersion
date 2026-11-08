package com.histmersion
package cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** The extension point a new plugin contributes to: its name on the command line, the suffix of the contributed class's
  * name, and what `histmersion plugin add` reports it provides.
  */
enum PluginKind(val name: PluginKind.Name, val classSuffix: PluginKind.ClassSuffix, val provides: PluginKind.Provides):
  case SourceFetcher extends PluginKind(PluginKind.Name("source-fetcher"), PluginKind.ClassSuffix("Fetcher"), PluginKind.Provides("source-fetcher"))
  case MediaCollection extends PluginKind(PluginKind.Name("media-collection"), PluginKind.ClassSuffix("Collection"), PluginKind.Provides("media-collection"))
  case EventCatalogue extends PluginKind(PluginKind.Name("event-catalogue"), PluginKind.ClassSuffix("Catalogue"), PluginKind.Provides("event-catalogue"))
  case AiProvider extends PluginKind(PluginKind.Name("ai-provider"), PluginKind.ClassSuffix("Provider"), PluginKind.Provides("ai-provider"))
  case SecretVault extends PluginKind(PluginKind.Name("secret-vault"), PluginKind.ClassSuffix("Vault"), PluginKind.Provides("secret-vault"))

object PluginKind:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type ClassSuffix = ClassSuffix.T
  object ClassSuffix extends RefinedType[String, Pure]
  type Provides = Provides.T
  object Provides extends RefinedType[String, Pure]


  val names: String = values.map(_.name).mkString(", ")
  def parse(raw: String): Either[String, PluginKind] =
    values.find(_.name.value == raw.trim).toRight(s"unknown plugin kind '$raw' (one of: $names)")
