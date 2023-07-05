package dev.vale

import scala.collection.mutable.ArrayBuffer

object SourceCodeUtils {
  def humanizePackage(packageCoord: PackageCoordinate): String = {
    val PackageCoordinate(module, packages) = packageCoord
    module.str + packages.map("." + _.str).mkString("")
  }

  def humanizeFile(coordinate: FileCoordinate): String = {
    val FileCoordinate(packageCoord, filepath) = coordinate
    humanizePackage(packageCoord) + ":" + filepath
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

    humanizePos(humanizeFile(file), source, pos)
  }

  def humanizePos(
    humanizedFilePath: String,
    source: String,
    pos: Int):
  String = {
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
    humanizedFilePath + ":" + (line + 1) + ":" + (i - lineBegin + 1)
  }

  def nextThingAndRestOfLine(
      filenamesAndSources: FileCoordinateMap[String],
      file: FileCoordinate,
      position: Int): String = {
    nextThingAndRestOfLine(filenamesAndSources(file), position)
  }

  def nextThingAndRestOfLine(
    text: String,
    position: Int): String = {
    // TODO: can optimize this
    text.slice(position, text.length).trim().split("\\n")(0).trim()
  }

  def lineBegin(
    filenamesAndSources: FileCoordinateMap[String],
    codeLocationS: CodeLocationS):
  CodeLocationS = {
    lineRangeContaining(filenamesAndSources, codeLocationS).begin
  }

  def lineRangeContaining(
    filenamesAndSources: FileCoordinateMap[String],
    codeLocationS: CodeLocationS):
  RangeS = {
    val CodeLocationS(file, offset) = codeLocationS
    if (offset < 0) {
      return RangeS(CodeLocationS(file, -1), CodeLocationS(file, 0))
    }
    val text = filenamesAndSources(file)
    // TODO: can optimize this perhaps
    var lineBegin = 0;
    while (lineBegin < text.length) {
      val lineEnd =
        text.indexOf('\n', lineBegin) match {
          case -1 => return RangeS(CodeLocationS(file, lineBegin), CodeLocationS(file, text.length))
          case other => other
        }
      if (lineBegin <= offset && offset <= lineEnd) {
        return RangeS(CodeLocationS(file, lineBegin), CodeLocationS(file, lineEnd))
      }
      lineBegin = lineEnd + 1
    }
    if (offset == text.length) {
      return RangeS(CodeLocationS(file, lineBegin), CodeLocationS(file, lineBegin))
    }
    vfail()
  }

  // Includes the line containing the begin and end code locs.
  def linesBetween(
    filenamesAndSources: FileCoordinateMap[String],
    beginCodeLoc: CodeLocationS,
    endCodeLoc: CodeLocationS):
  Vector[RangeS] = {
    vassert(beginCodeLoc.file == endCodeLoc.file)
    vassert(beginCodeLoc.offset <= endCodeLoc.offset)

    val CodeLocationS(file, offset) = beginCodeLoc
    if (file.isInternal) {
      return Vector()
    }
    val result = ArrayBuffer[(Int, Int)]()

    var RangeS(CodeLocationS(_, lineBegin), CodeLocationS(_, lineEnd)) =
      lineRangeContaining(filenamesAndSources, beginCodeLoc)
    result += ((lineBegin, lineEnd))
    val text = filenamesAndSources(file)
    while (lineBegin < endCodeLoc.offset && lineBegin < text.length) {
      lineEnd =
        text.indexOf('\n', lineBegin) match {
          case -1 => text.length
          case other => other
        }
      result += ((lineBegin, lineEnd))
      lineBegin = lineEnd + 1
    }
    result.map({ case (begin, end) =>
      RangeS(CodeLocationS(file, begin), CodeLocationS(file, end))
    }).toVector
  }

  def lineContaining(
      filenamesAndSources: FileCoordinateMap[String],
      codeLocationS: CodeLocationS):
  String = {
    if (codeLocationS.file.isInternal) {
      return humanizeFile(codeLocationS.file)
    }
    var RangeS(CodeLocationS(_, lineBegin), CodeLocationS(_, lineEnd)) =
        lineRangeContaining(filenamesAndSources, codeLocationS)
    val text = filenamesAndSources(codeLocationS.file)
    text.substring(lineBegin, lineEnd)
  }
}
