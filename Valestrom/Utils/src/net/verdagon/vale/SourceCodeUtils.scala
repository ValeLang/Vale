package net.verdagon.vale

object SourceCodeUtils {
  def humanizePos(
      filenamesAndSources: List[(String, String)],
      file: Int,
      pos: Int) = {
    val (filename, source) = filenamesAndSources(file)
    var line = 0;
    var col = 0;
    var i = 0
    while (i <= pos) {
      if (source(i) == '\n') {
        line = line + 1;
        col = 0;
      } else {
        col = col + 1;
      }
      i = i + 1;
    }
    filename + ":" + (line + 1) + ":" + (col + 1)
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
    val text = filenamesAndSources(file)._2
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
