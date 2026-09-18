package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import histdb.core.*

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

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("media", "Asset metadata: detail, patch") {
      detail orElse patch
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
