package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

object MediaCmd:

  private val detail = Opts.subcommand("detail", "Show asset metadata including spatial/temporal") {
    (Opts.argument[String]("citekey"), Opts.argument[String]("name"))
      .mapN { (ck, name) => (c: Client) =>
        c.assetDetail(ck, name).map(d => println(Markdown.assetDetail(ck, d)))
      }
  }

  private val patch = Opts.subcommand("patch", "Update asset metadata") {
    (Opts.argument[String]("citekey"), Opts.argument[String]("name"),
      Opts.option[String]("kind", s"Asset kind: ${AssetKind.all.mkString("|")}").orNone,
      Opts.option[String]("caption", "Caption text").orNone,
      Opts.option[String]("license", "License identifier").orNone,
      Opts.option[Double]("lat", "Latitude").orNone,
      Opts.option[Double]("lon", "Longitude").orNone,
      Opts.option[Long]("period-start", "Period start (signed year)").orNone,
      Opts.option[Long]("period-end", "Period end (signed year)").orNone)
      .mapN { (ck, name, kind, caption, license, lat, lon, ps, pe) => (c: Client) =>
        c.patchAsset(ck, name, AssetPatch(kind, caption, license, lat, lon, ps, pe))
          .map(d => println(Markdown.assetDetail(ck, d)))
      }
  }

  // ── media from outside providers (#1446) ──

  private val importWikidata = Opts.subcommand("wikidata",
      "Pictures of places and events from their Wikidata items (image, locator map, plan, aerial view, " +
        "route map), licensed on Wikimedia Commons; prints the counts per provider and licence first") {
    (Opts.flag("dry-run", "Read the statements and licences, store nothing").orFalse,
      Opts.option[Int]("limit", "Places/events to ask about (default media.default-limit, at most media.max-limit)").orNone,
      Opts.option[String]("place", "Only this place (its Wikidata link)").orNone,
      Opts.option[String]("event", "Only this event (its --wikidata item)").orNone,
      Opts.flag("refresh", "Ask again about ones already asked, and re-read the licences of stored pictures").orFalse)
      .mapN { (dry, limit, place, event, refresh) => (c: Client) =>
        c.mediaImport(MediaImportRequest("wikidata", dry, limit, place, event, refresh))
          .map(r => println(Markdown.mediaImportReport(r)))
      }
  }

  private val importCmd = Opts.subcommand("import", "Bring in pictures from an outside provider (legally reusable only)") {
    importWikidata
  }

  private val listCmd = Opts.subcommand("list", "Stored pictures — source figures and providers' — with licence and credit") {
    (Opts.option[String]("provider", s"${MediaProvider.all.mkString("|")}").orNone,
      Opts.option[String]("licence", "An SPDX id (CC-BY-SA-4.0), public-domain, or none").orNone,
      Opts.option[String]("place", "Linked to this place").orNone,
      Opts.option[String]("event", "Linked to this event").orNone,
      Opts.option[String]("q", "Text in the name, file or credit").orNone,
      Opts.option[Int]("limit", "How many to list").orNone,
      Opts.flag("facets", "Only the counts per provider and licence").orFalse)
      .mapN { (provider, licence, place, event, q, limit, facets) => (c: Client) =>
        if facets then c.mediaFacets.map(f => println(Markdown.mediaFacets(f)))
        else c.mediaList(Api.MediaQuery(provider, licence, place, event, q, limit)).map(l => println(Markdown.mediaList(l)))
      }
  }

  private val showCmd = Opts.subcommand("show", "One picture: origin, licence, credit, and what it shows") {
    Opts.argument[String]("uid").map { uid => (c: Client) =>
      c.mediaShow(uid.stripPrefix("media:")).map(m => println(Markdown.mediaItem(m)))
    }
  }

  private val fillCmd = Opts.subcommand("fill-licences",
      "Give a source's figures the licence the source records (only figures with none); counts unless --apply") {
    Opts.flag("apply", "Write the licences").orFalse.map { apply => (c: Client) =>
      c.fillFigureLicences(apply).map(f => println(Markdown.figureLicenceFill(f)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("media", "Pictures: import from providers, list, show, fill-licences; a figure's detail, patch") {
      importCmd orElse listCmd orElse showCmd orElse fillCmd orElse detail orElse patch
    }

object AssetLinksCmd:

  private val search = Opts.subcommand("search", "Find asset links") {
    (Opts.option[String]("citekey", "Filter by citekey").orNone,
      Opts.option[String]("asset", "Filter by asset name").orNone,
      Opts.option[String]("target-type", s"Target type: ${AssetLinkTarget.all.mkString("|")}").orNone,
      Opts.option[String]("target-id", "Filter by target id").orNone)
      .mapN { (ck, asset, tt, ti) => (c: Client) =>
        c.assetLinks(ck, asset, tt, ti).map(r => println(Markdown.assetLinkList(r)))
      }
  }

  private val addCmd = Opts.subcommand("add", "Create an asset link") {
    (Opts.option[String]("citekey", "Source citekey"),
      Opts.option[String]("asset", "Asset file name"),
      Opts.option[String]("relation", s"Relation: ${AssetRelation.all.mkString("|")}"),
      Opts.option[String]("target-type", s"Target type: ${AssetLinkTarget.all.mkString("|")}"),
      Opts.option[String]("target-id", "Target entity id"),
      Opts.option[String]("xywh", "Region within the asset (x,y,w,h)").orNone,
      Opts.option[String]("notes", "Free-text notes").orNone)
      .mapN { (ck, asset, rel, tt, ti, xywh, notes) => (c: Client) =>
        c.createAssetLink(AssetLinkPut(ck, asset, rel, tt, ti, xywh, notes))
          .map(l => println(Markdown.assetLink(l)))
      }
  }

  private val rmCmd = Opts.subcommand("rm", "Delete an asset link by id") {
    Opts.argument[Long]("id").map { id => (c: Client) =>
      c.deleteAssetLink(id).map(d => println(s"Deleted asset link #${d.id}."))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("asset-links", "Semantic links between assets and entities: search, add, rm") {
      search orElse addCmd orElse rmCmd
    }
