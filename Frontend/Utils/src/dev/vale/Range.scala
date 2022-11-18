package dev.vale

object CodeLocationS {
  // Keep in sync with CodeLocation2
  def testZero(interner: Interner): CodeLocationS = {
    CodeLocationS.internal(interner, -1)
  }
  def internal(interner: Interner, internalNum: Int): CodeLocationS = {
    vassert(internalNum < 0)
    CodeLocationS(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate(
          interner.intern(StrI("")),
          Vector.empty)),
        "internal")),
      internalNum)
  }
}

sealed trait ICrumb
case class RangeCrumb(range: RangeS) extends ICrumb
case class InternalCrumb(num: Int) extends ICrumb

object RangeS {
  // Should only be used in tests.
  def testZero(interner: Interner): RangeS = {
    RangeS(CodeLocationS.testZero(interner), CodeLocationS.testZero(interner))
  }

  def internal(interner: Interner, internalNum: Int): RangeS = {
    vassert(internalNum < 0)
    RangeS(CodeLocationS.internal(interner, internalNum), CodeLocationS.internal(interner, internalNum))
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
    if (file.isTest()) {
      "tvl" + ":" + offset
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
    if (file.isTest()) {
      "tvr" + ":" + begin.offset + "-" + end.offset
    } else {
      "RangeS(" + begin.toString + ", " + end.toString + ")"
    }
  }
}
