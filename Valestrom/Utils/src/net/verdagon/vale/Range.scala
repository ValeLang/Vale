package net.verdagon.vale


object CodeLocationS {
  // Keep in sync with CodeLocation2
  val testZero = CodeLocationS.internal(-1)
  def internal(internalNum: Int): CodeLocationS = {
    vassert(internalNum < 0)
    CodeLocationS(FileCoordinate("", Vector.empty, "internal"), internalNum)
  }
}

object RangeS {
  // Should only be used in tests.
  val testZero = RangeS(CodeLocationS.testZero, CodeLocationS.testZero)

  def internal(internalNum: Int): RangeS = {
    vassert(internalNum < 0)
    RangeS(CodeLocationS.internal(internalNum), CodeLocationS.internal(internalNum))
  }
}

case class CodeLocationS(
  // The index in the original source code files list.
  // If negative, it means it came from some internal non-file code.
  file: FileCoordinate,
  offset: Int) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  // Just for debug purposes
  override def toString: String = {
    if (file == FileCoordinate.test) {
      "tv" + ":" + offset
    } else {
      file.toString + ":" + offset
    }
  }
}

case class RangeS(begin: CodeLocationS, end: CodeLocationS) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vassert(begin.file == end.file)
  vassert(begin.offset <= end.offset)
  def file: FileCoordinate = begin.file

  // Just for debug purposes
  override def toString: String = {
    if (file == FileCoordinate.test) {
      "tv" + ":" + begin.offset + "-" + end.offset
    } else {
      begin.toString + "-" + end.toString
    }
  }
}
