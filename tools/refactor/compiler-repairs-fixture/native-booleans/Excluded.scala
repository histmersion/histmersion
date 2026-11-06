package compilerfixture

object NativeExcluded:
  def custom(flag: NativeFlags.CustomFlag): Int = if flag then 1 else 0
  def alias(flag: NativeFlags.AliasFlag): Int = if flag then 1 else 0
  def numeric(count: Values.Count): Boolean = count > 0
  def string(text: Values.Text): String = text.trim
