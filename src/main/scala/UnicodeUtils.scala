object UnicodeUtils {
  def getUnicodeString(e: EmojiDefinition): Option[Char] = {
    val codepoints = e.unified.split("-")
    val ints = codepoints.map(a => Integer.parseInt(a, 16))
    val string = new String(ints, 0, ints.length)
    if (string.length > 0) Option(string.charAt(0)) else None
  }
}
