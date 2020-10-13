package net.verdagon.vale

object SourceCodeUtils {
  def humanizePos(
      filenamesAndSources: List[(String, String)],
      file: Int,
      pos: Int): String = {
    if (file < 0) {
      return "internal(" + file + ")"
    }
    val (filename, source) = filenamesAndSources(file)

    var line = 0
    var lineBegin = 0
    var i = 0
    while (i < pos) {
      if (source(i) == '\n') {
        lineBegin = i + 1
        line = line + 1
      }
      i = i + 1
    }
    filename + ":" + (line + 1) + ":" + (i - lineBegin + 1)
  }

  def nextThingAndRestOfLine(
      filenamesAndSources: List[(String, String)],
      file: Int,
      position: Int) = {
    val text = filenamesAndSources(file)._2
    // TODO: can optimize this
    text.slice(position, text.length).trim().split("\\n")(0).trim()
  }

  def lineContaining(
      filenamesAndSources: List[(String, String)],
      file: Int,
      position: Int):
  String = {
    if (file < 0) {
      return "(internal(" + file + "))"
    }
    val text = filenamesAndSources(file)._2
    // TODO: can optimize this perhaps
    var lineBegin = 0;
    while (lineBegin < text.length) {
      val lineEnd =
        text.indexOf('\n', lineBegin) match {
          case -1 => text.length
          case other => other
        }
      if (lineBegin <= position && position <= lineEnd) {
        return text.substring(lineBegin, lineEnd)
      }
      lineBegin = lineEnd + 1
    }
    vfail()
  }
}
