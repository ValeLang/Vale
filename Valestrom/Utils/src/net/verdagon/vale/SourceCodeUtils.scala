package net.verdagon.vale

import scala.collection.mutable.ArrayBuffer

object SourceCodeUtils {
  def humanizeFile(coordinate: FileCoordinate): String = {
    val FileCoordinate(module, packages, filepath) = coordinate
    module + packages.map("." + _).mkString("") + ":" + filepath
  }

  def humanizePos(
    filenamesAndSources: FileCoordinateMap[String],
    codeLocationS: CodeLocationS):
  String = {
    val CodeLocationS(file, pos) = codeLocationS
//    if (file.isInternal) {
//      return humanizeFile(file)
//    }

    if (codeLocationS.offset < 0) {
      return humanizeFile(file) + ":" + codeLocationS.offset.toString
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
    humanizeFile(file) + ":" + (line + 1) + ":" + (i - lineBegin + 1)
  }

  def nextThingAndRestOfLine(
      filenamesAndSources: FileCoordinateMap[String],
      file: FileCoordinate,
      position: Int) = {
    val text = filenamesAndSources(file)
    // TODO: can optimize this
    text.slice(position, text.length).trim().split("\\n")(0).trim()
  }

  def lineBegin(
    filenamesAndSources: FileCoordinateMap[String],
    codeLocationS: CodeLocationS):
  CodeLocationS = {
    val (begin, end) = lineRangeContaining(filenamesAndSources, codeLocationS)
    CodeLocationS(codeLocationS.file, begin)
  }

  def lineRangeContaining(
    filenamesAndSources: FileCoordinateMap[String],
    codeLocationS: CodeLocationS):
  (Int, Int) = {
    val CodeLocationS(file, offset) = codeLocationS
    if (file.isInternal) {
      return (-1, 0)
    }
    val text = filenamesAndSources(file)
    // TODO: can optimize this perhaps
    var lineBegin = 0;
    while (lineBegin < text.length) {
      val lineEnd =
        text.indexOf('\n', lineBegin) match {
          case -1 => return (lineBegin, text.length)
          case other => other
        }
      if (lineBegin <= offset && offset <= lineEnd) {
        return (lineBegin, lineEnd)
      }
      lineBegin = lineEnd + 1
    }
    if (offset == text.length) {
      return (lineBegin, lineBegin)
    }
    vfail()
  }

  // Includes the line containing the begin and end code locs.
  def linesBetween(
    filenamesAndSources: FileCoordinateMap[String],
    beginCodeLoc: CodeLocationS,
    endCodeLoc: CodeLocationS):
  Array[(Int, Int)] = {
    vassert(beginCodeLoc.file == endCodeLoc.file)
    vassert(beginCodeLoc.offset <= endCodeLoc.offset)

    val CodeLocationS(file, offset) = beginCodeLoc
    if (file.isInternal) {
      return Array()
    }
    val result = ArrayBuffer[(Int, Int)]()

    var (lineBegin, lineEnd) = lineRangeContaining(filenamesAndSources, beginCodeLoc)
    result += ((lineBegin, lineEnd))
    val text = filenamesAndSources(file)
    while (lineBegin < endCodeLoc.offset && lineBegin < text.length) {
      lineBegin = lineEnd + 1
      lineEnd =
        text.indexOf('\n', lineBegin) match {
          case -1 => text.length
          case other => other
        }
      result += ((lineBegin, lineEnd))
    }
    return result.toArray
  }

  def lineContaining(
      filenamesAndSources: FileCoordinateMap[String],
      codeLocationS: CodeLocationS):
  String = {
    if (codeLocationS.file.isInternal) {
      return humanizeFile(codeLocationS.file)
    }
    val (lineBegin, lineEnd) = lineRangeContaining(filenamesAndSources, codeLocationS)
    val text = filenamesAndSources(codeLocationS.file)
    text.substring(lineBegin, lineEnd)
  }
}
