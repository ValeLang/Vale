package net.verdagon.vale

object SourceCodeUtils {
  def lineAndCol(text: String, pos: Int) = {
    var line = 0;
    var col = 0;
    var i = 0
    while (i <= pos) {
      if (text(i) == '\n') {
        line = line + 1;
        col = 0;
      } else {
        col = col + 1;
      }
      i = i + 1;
    }
    (line + 1) + ":" + (col + 1)
  }

  def nextThingAndRestOfLine(text: String, position: Int) = {
    // TODO: can optimize this
    text.slice(position, text.length).trim().split("\\n")(0).trim()
  }

  def lineContaining(text: String, position: Int): String = {
    // TODO: can optimize this perhaps
    var lineBegin = 0;
    while (true) {
      val lineEnd = text.indexOf('\n', lineBegin)
      if (lineEnd < 0) {
        vfail()
      }
      if (lineBegin <= position && position < lineEnd) {
        return text.substring(lineBegin, lineEnd)
      }
      lineBegin = lineEnd + 1
    }
    vfail()
  }
}
