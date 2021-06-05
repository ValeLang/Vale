package net.verdagon.vale

object SourceCodeUtils {
  def humanizePos(
    filenamesAndSources: FileCoordinateMap[String],
    file: FileCoordinate,
      pos: Int): String = {
    if (file.isInternal) {
      return "internal(" + file + ")"
    }
    val source = filenamesAndSources(file)

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
    file.filepath + ":" + (line + 1) + ":" + (i - lineBegin + 1)
  }

  def nextThingAndRestOfLine(
      filenamesAndSources: FileCoordinateMap[String],
      file: FileCoordinate,
      position: Int) = {
    val text = filenamesAndSources(file)
    // TODO: can optimize this
    text.slice(position, text.length).trim().split("\\n")(0).trim()
  }

  def lineContaining(
      filenamesAndSources: FileCoordinateMap[String],
      file: FileCoordinate,
      position: Int):
  String = {
    if (file.isInternal) {
      return "(internal(" + file + "))"
    }
    val text = filenamesAndSources(file)
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
