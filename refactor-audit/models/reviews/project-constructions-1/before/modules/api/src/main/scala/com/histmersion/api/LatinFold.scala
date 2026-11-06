package com.histmersion.api

/** Diacritic folding as a lookup table: this code is also compiled to Scala.js, which has no `java.text.Normalizer`.
  * The single-letter part was generated from Unicode NFD decompositions; the letters NFD does not decompose (`ß`, `æ`,
  * `ø`, `ł`, `đ`, `þ`, …) are mapped by hand.
  */
object LatinFold:

  type Pairs = Pairs.T
  object Pairs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultiPart1 = MultiPart1.T
  object MultiPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultiPart2 = MultiPart2.T
  object MultiPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TablePart1 = TablePart1.T
  object TablePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TablePart2 = TablePart2.T
  object TablePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableCharsResult = TableCharsResult.T
  object TableCharsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCombiningMarkResult = IsCombiningMarkResult.T
  object IsCombiningMarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCombiningMarkC = IsCombiningMarkC.T
  object IsCombiningMarkC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldCharResult = FoldCharResult.T
  object FoldCharResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldCharC = FoldCharC.T
  object FoldCharC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldResult = FoldResult.T
  object FoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldS = FoldS.T
  object FoldS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Pairs: com.histmersion.api.LatinFold.Pairs =
    // Latin-1 Supplement
    "ÀAÁAÂAÃAÄAÅAÇCÈEÉEÊEËEÌIÍIÎIÏIÐDÑNÒOÓOÔOÕOÖOØOÙUÚUÛUÜUÝYàaáaâaãaäaåaçcèeéeêeëeìi" +
      "íiîiïiðdñnòoóoôoõoöoøoùuúuûuüuýyÿy" +
      // Latin Extended-A
      "ĀAāaĂAăaĄAąaĆCćcĈCĉcĊCċcČCčcĎDďdĐDđdĒEēeĔEĕeĖEėeĘEęeĚEěeĜGĝgĞGğgĠGġgĢGģgĤHĥhĦHħh" +
      "ĨIĩiĪIīiĬIĭiĮIįiİIıiĴJĵjĶKķkĸqĹLĺlĻLļlĽLľlĿLŀlŁLłlŃNńnŅNņnŇNňnŉnŊNŋnŌOōoŎOŏoŐOőo" +
      "ŔRŕrŖRŗrŘRřrŚSśsŜSŝsŞSşsŠSšsŢTţtŤTťtŦTŧtŨUũuŪUūuŬUŭuŮUůuŰUűuŲUųuŴWŵwŶYŷyŸYŹZźzŻZ" +
      "żzŽZžzſs" +
      // Latin Extended-B
      "ƀbƁBƇCƈcƉDƊDƑFƒfƗIƘKƙkƚlƝNƞnƟOƠOơoƤPƥpƫtƬTƭtƮTƯUưuƲVƳYƴyƵZƶzǍAǎaǏIǐiǑOǒoǓUǔuǕUǖu" +
      "ǗUǘuǙUǚuǛUǜuǝeǞAǟaǠAǡaǤGǥgǦGǧgǨKǩkǪOǫoǬOǭoǰjǴGǵgǸNǹnǺAǻaǾOǿoȀAȁaȂAȃaȄEȅeȆEȇeȈIȉi" +
      "ȊIȋiȌOȍoȎOȏoȐRȑrȒRȓrȔUȕuȖUȗuȘSșsȚTțtȞHȟhȡdȤZȥzȦAȧaȨEȩeȪOȫoȬOȭoȮOȯoȰOȱoȲYȳyȴlȵnȶt" +
      "ȷjȺAȻCȼcȽLȾTȿsɀzɃBɄUɆEɇeɈJɉjɌRɍrɎYɏy" +
      // Latin Extended Additional
      "ḀAḁaḂBḃbḄBḅbḆBḇbḈCḉcḊDḋdḌDḍdḎDḏdḐDḑdḒDḓdḔEḕeḖEḗeḘEḙeḚEḛeḜEḝeḞFḟfḠGḡgḢHḣhḤHḥhḦHḧh" +
      "ḨHḩhḪHḫhḬIḭiḮIḯiḰKḱkḲKḳkḴKḵkḶLḷlḸLḹlḺLḻlḼLḽlḾMḿmṀMṁmṂMṃmṄNṅnṆNṇnṈNṉnṊNṋnṌOṍoṎOṏo" +
      "ṐOṑoṒOṓoṔPṕpṖPṗpṘRṙrṚRṛrṜRṝrṞRṟrṠSṡsṢSṣsṤSṥsṦSṧsṨSṩsṪTṫtṬTṭtṮTṯtṰTṱtṲUṳuṴUṵuṶUṷu" +
      "ṸUṹuṺUṻuṼVṽvṾVṿvẀWẁwẂWẃwẄWẅwẆWẇwẈWẉwẊXẋxẌXẍxẎYẏyẐZẑzẒZẓzẔZẕzẖhẗtẘwẙyẜsẝsẠAạaẢAảa" +
      "ẤAấaẦAầaẨAẩaẪAẫaẬAậaẮAắaẰAằaẲAẳaẴAẵaẶAặaẸEẹeẺEẻeẼEẽeẾEếeỀEềeỂEểeỄEễeỆEệeỈIỉiỊIịi" +
      "ỌOọoỎOỏoỐOốoỒOồoỔOổoỖOỗoỘOộoỚOớoỜOờoỞOởoỠOỡoỢOợoỤUụuỦUủuỨUứuỪUừuỬUửuỮUữuỰUựuỲYỳy" +
      "ỴYỵyỶYỷyỸYỹy"

  /** The transliteration half rings (`ʿ` ayin, `ʾ` aleph) fold to nothing: `Baʿal` → `Baal`. */
  private val Multi: Map[com.histmersion.api.LatinFold.MultiPart1, com.histmersion.api.LatinFold.MultiPart2] = Map(
    '\u02bf' -> "",
    '\u02be' -> "",
    'Æ' -> "AE",
    'Þ' -> "TH",
    'ß' -> "ss",
    'æ' -> "ae",
    'þ' -> "th",
    'Ĳ' -> "IJ",
    'ĳ' -> "ij",
    'Œ' -> "OE",
    'œ' -> "oe",
    'Ǆ' -> "DZ",
    'ǅ' -> "Dz",
    'ǆ' -> "dz",
    'Ǉ' -> "LJ",
    'ǈ' -> "Lj",
    'ǉ' -> "lj",
    'Ǌ' -> "NJ",
    'ǋ' -> "Nj",
    'ǌ' -> "nj",
    'Ǣ' -> "AE",
    'ǣ' -> "ae",
    'Ǳ' -> "DZ",
    'ǲ' -> "Dz",
    'ǳ' -> "dz",
    'Ǽ' -> "AE",
    'ǽ' -> "ae",
    'ẞ' -> "SS",
    'Ỻ' -> "LL",
    'ỻ' -> "ll"
  )

  private val Table: Map[com.histmersion.api.LatinFold.TablePart1, com.histmersion.api.LatinFold.TablePart2] =
    Pairs.grouped(2).map(p => p(0) -> p(1).toString).toMap ++ Multi

  def tableChars: Set[com.histmersion.api.LatinFold.TableCharsResult] = Table.keySet

  private def isCombiningMark(c: com.histmersion.api.LatinFold.IsCombiningMarkC): com.histmersion.api.LatinFold.IsCombiningMarkResult = c >= '\u0300' && c <= '\u036f'

  def foldChar(c: com.histmersion.api.LatinFold.FoldCharC): com.histmersion.api.LatinFold.FoldCharResult =
    if isCombiningMark(c) then "" else Table.getOrElse(c, c.toString)

  def fold(s: com.histmersion.api.LatinFold.FoldS): com.histmersion.api.LatinFold.FoldResult =
    val b = new StringBuilder(s.length)
    s.foreach { c =>
      if !isCombiningMark(c) then
        Table.get(c) match
          case Some(r) => b ++= r
          case None    => b += c
    }
    b.result()
