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
    _root_.com.histmersion.api.LatinFold.Pairs("ÀAÁAÂAÃAÄAÅAÇCÈEÉEÊEËEÌIÍIÎIÏIÐDÑNÒOÓOÔOÕOÖOØOÙUÚUÛUÜUÝYàaáaâaãaäaåaçcèeéeêeëeìi" +
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
      "ỴYỵyỶYỷyỸYỹy")

  /** The transliteration half rings (`ʿ` ayin, `ʾ` aleph) fold to nothing: `Baʿal` → `Baal`. */
  private val Multi: Map[com.histmersion.api.LatinFold.MultiPart1, com.histmersion.api.LatinFold.MultiPart2] = Map(
    { val refactorTuple31 = '\u02bf' -> ""; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple31._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple31._2)) },
    { val refactorTuple32 = '\u02be' -> ""; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple32._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple32._2)) },
    { val refactorTuple3 = 'Æ' -> "AE"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple3._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple3._2)) },
    { val refactorTuple34 = 'Þ' -> "TH"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple34._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple34._2)) },
    { val refactorTuple5 = 'ß' -> "ss"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple5._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple5._2)) },
    { val refactorTuple6 = 'æ' -> "ae"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple6._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple6._2)) },
    { val refactorTuple37 = 'þ' -> "th"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple37._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple37._2)) },
    { val refactorTuple38 = 'Ĳ' -> "IJ"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple38._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple38._2)) },
    { val refactorTuple9 = 'ĳ' -> "ij"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple9._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple9._2)) },
    { val refactorTuple40 = 'Œ' -> "OE"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple40._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple40._2)) },
    { val refactorTuple41 = 'œ' -> "oe"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple41._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple41._2)) },
    { val refactorTuple42 = 'Ǆ' -> "DZ"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple42._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple42._2)) },
    { val refactorTuple43 = 'ǅ' -> "Dz"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple43._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple43._2)) },
    { val refactorTuple14 = 'ǆ' -> "dz"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple14._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple14._2)) },
    { val refactorTuple15 = 'Ǉ' -> "LJ"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple15._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple15._2)) },
    { val refactorTuple46 = 'ǈ' -> "Lj"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple46._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple46._2)) },
    { val refactorTuple17 = 'ǉ' -> "lj"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple17._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple17._2)) },
    { val refactorTuple48 = 'Ǌ' -> "NJ"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple48._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple48._2)) },
    { val refactorTuple19 = 'ǋ' -> "Nj"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple19._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple19._2)) },
    { val refactorTuple50 = 'ǌ' -> "nj"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple50._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple50._2)) },
    { val refactorTuple21 = 'Ǣ' -> "AE"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple21._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple21._2)) },
    { val refactorTuple22 = 'ǣ' -> "ae"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple22._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple22._2)) },
    { val refactorTuple23 = 'Ǳ' -> "DZ"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple23._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple23._2)) },
    { val refactorTuple54 = 'ǲ' -> "Dz"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple54._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple54._2)) },
    { val refactorTuple55 = 'ǳ' -> "dz"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple55._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple55._2)) },
    { val refactorTuple56 = 'Ǽ' -> "AE"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple56._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple56._2)) },
    { val refactorTuple57 = 'ǽ' -> "ae"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple57._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple57._2)) },
    { val refactorTuple28 = 'ẞ' -> "SS"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple28._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple28._2)) },
    { val refactorTuple59 = 'Ỻ' -> "LL"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple59._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple59._2)) },
    { val refactorTuple30 = 'ỻ' -> "ll"; (_root_.com.histmersion.api.LatinFold.MultiPart1(refactorTuple30._1), _root_.com.histmersion.api.LatinFold.MultiPart2(refactorTuple30._2)) }
  )

  private val Table: Map[com.histmersion.api.LatinFold.TablePart1, com.histmersion.api.LatinFold.TablePart2] =
    Pairs.grouped(2).map(p => p(0) -> p(1).toString).toMap ++ Multi

  def tableChars: Set[com.histmersion.api.LatinFold.TableCharsResult] = Table.keySet

  private def isCombiningMark(c: com.histmersion.api.LatinFold.IsCombiningMarkC): com.histmersion.api.LatinFold.IsCombiningMarkResult = c >= '\u0300' && c <= '\u036f'

  def foldChar(c: com.histmersion.api.LatinFold.FoldCharC): com.histmersion.api.LatinFold.FoldCharResult =
    if isCombiningMark(c) then _root_.com.histmersion.api.LatinFold.FoldCharResult("") else Table.getOrElse(c, c.toString)

  def fold(s: com.histmersion.api.LatinFold.FoldS): com.histmersion.api.LatinFold.FoldResult =
    val b = new StringBuilder((s).value.length)
    (s).value.foreach { c =>
      if !isCombiningMark(c) then
        Table.get(c) match
          case Some(r) => b ++= r
          case None    => b += c
    }
    b.result()
