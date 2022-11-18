package dev.vale.lexing

import dev.vale.{IInterning, StrI, U, vassert, vcurious, vpass, vwat}

case class RangeL(begin: Int, end: Int) {
  override def hashCode(): Int = vcurious()
  vassert(begin == end || begin <= end)
}
object RangeL {
  val zero = RangeL(0, 0)
}

case class FileL(
  denizens: Vector[IDenizenL],
  commentRanges: Vector[RangeL]
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

sealed trait IDenizenL
case class TopLevelFunctionL(function: FunctionL) extends IDenizenL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelStructL(struct: StructL) extends IDenizenL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelInterfaceL(interface: InterfaceL) extends IDenizenL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImplL(impl: ImplL) extends IDenizenL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelExportAsL(export: ExportAsL) extends IDenizenL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class TopLevelImportL(imporrt: ImportL) extends IDenizenL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ImplL(
  range: RangeL,
  identifyingRunes: Option[AngledLE],
  templateRules: Option[ScrambleLE],
  // Option because we can say `impl MyInterface;` inside a struct.
  struct: Option[ScrambleLE],
  interface: ScrambleLE,
  attributes: Vector[IAttributeL]
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ExportAsL(
  range: RangeL,
  contents: ScrambleLE) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ImportL(
  range: RangeL,
  moduleName: WordLE,
  packageSteps: Vector[WordLE],
  importeeName: WordLE) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class StructL(
  range: RangeL,
  name: WordLE,
  attributes: Vector[IAttributeL],
  mutability: Option[ScrambleLE],
  identifyingRunes: Option[AngledLE],
  templateRules: Option[ScrambleLE],
  members: ScrambleLE) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class InterfaceL(
  range: RangeL,
  name: WordLE,
  attributes: Vector[IAttributeL],
  mutability: Option[ScrambleLE],
  maybeIdentifyingRunes: Option[AngledLE],
  templateRules: Option[ScrambleLE],
  bodyRange: RangeL,
  members: Vector[FunctionL]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IAttributeL

case class AbstractAttributeL(range: RangeL) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExportAttributeL(range: RangeL) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class PureAttributeL(range: RangeL) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExternAttributeL(range: RangeL, maybeCustomName: Option[ParendLE]) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class WeakableAttributeL(range: RangeL) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class SealedAttributeL(range: RangeL) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IMacroInclusionL
case object CallMacroL extends IMacroInclusionL
case object DontCallMacroL extends IMacroInclusionL
case class MacroCallL(range: RangeL, inclusion: IMacroInclusionL, name: WordLE) extends IAttributeL { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionL(
  range: RangeL,
  header: FunctionHeaderL,
  body: Option[FunctionBodyL]) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionBodyL(
  defaultRegion: Option[INodeLE],
  body: CurliedLE
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class FunctionHeaderL(
  range: RangeL,
  name: WordLE,
  attributes: Vector[IAttributeL],

  maybeUserSpecifiedIdentifyingRunes: Option[AngledLE],
  templateRules: Option[ScrambleLE],

  params: ParendLE,
  ret: FunctionReturnL
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class FunctionReturnL(
  range: RangeL,
  inferRet: Option[RangeL],
  retType: Option[ScrambleLE]
) { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

trait INodeLE {
  def range: RangeL
}

case class ScrambleLE(
  range: RangeL,
  elements: Vector[INodeLE],

//  // This is redundant with the above, a cache/index for the parser to be able
//  // to look ahead in constant time.
//  // For example if we see a = before the next ; then we're in a let statement.
//  sequencingSymbols: Vector[SymbolLE]

//  // These are redundant with the above, a cache/index for the parser to be able
//  // to look ahead in constant time.
//  // For example if we see a = before the next ; then we're in a let statement.
//  // In the eventual arena-based AST thing we might make these linked lists.
//  commaPositions: Vector[Int],
//  semicolonPositions: Vector[Int],
//  equalsPositions: Vector[Int]
) extends INodeLE {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();

  U.foreach[INodeLE](elements, {
    case ScrambleLE(_, _) => vwat()
    case _ =>
  })
}

case class ParendLE(range: RangeL, contents: ScrambleLE) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}

case class AngledLE(range: RangeL, contents: ScrambleLE) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}

case class SquaredLE(range: RangeL, contents: ScrambleLE) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}

case class CurliedLE(range: RangeL, contents: ScrambleLE) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}

case class WordLE(range: RangeL, str: StrI) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}

case class SymbolLE(range: RangeL, c: Char) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}

case class StringLE(range: RangeL, parts: Vector[StringPart]) extends INodeLE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
}
sealed trait StringPart
case class StringPartLiteral(range: RangeL, s: String) extends StringPart {
  vpass()
}
case class StringPartExpr(expr: ScrambleLE) extends StringPart


sealed trait IParsedNumberLE extends INodeLE
case class ParsedIntegerLE(range: RangeL, int: Long, bits: Option[Long]) extends IParsedNumberLE
case class ParsedDoubleLE(range: RangeL, double: Double, bits: Option[Long]) extends IParsedNumberLE
