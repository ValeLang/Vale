package net.verdagon.vale.parser

import net.verdagon.vale.vimpl
import net.verdagon.von.{IVonData, VonArray, VonBool, VonMember, VonObject, VonStr}

import scala.util.parsing.input.Position

object Vonifier {
  def vonifyProgram(program: Program0): IVonData = {
    val Program0(topLevelThings) = program
//    VonObject(
//      "Program",
//      None,
//      Vector(
//        VonMember("structs", VonArray(None, structs.map(vonifyStruct).toVector)),
//        VonMember("interfaces", VonArray(None, interfaces.map(vonifyInterface).toVector)),
//        VonMember("impl", VonArray(None, impls.map(vonifyImpl).toVector)),
//        VonMember("functions", VonArray(None, functions.map(vonifyFunction).toVector))))
    vimpl()
  }

//  def vonifyFunction(function: FunctionP): IVonData = {
//    val FunctionP(range, name, isExtern, isAbstract, userSpecifiedIdentifyingRunes, templateRules, params, ret, body) = function
//    VonObject("Function", None, Vector(
//        VonMember("range", vonifyRange(range)),
//        VonMember("name", vonifyOptional[String](name, VonStr)),
//        VonMember("isExtern", VonBool(isExtern)),
//        VonMember("isAbstract", VonBool(isAbstract)),
//        VonMember("userSpecifiedIdentifyingRunes", VonArray(None, userSpecifiedIdentifyingRunes.map(VonStr).toVector)),
//        VonMember("templateRules", VonArray(None, templateRules.map(vonifyRulexPR).toVector)),
//        VonMember("params", VonArray(None, params.map(vonifyPattern).toVector)),
//        VonMember("ret", vonifyOptional[ITemplexPT](ret, vonifyTemplexPT)),
//        VonMember("body", vonifyOptional[BlockPE](body, vonifyBlock))))
//  }
//
//  def vonifyRange(range: Range): IVonData = {
//    val Range(begin, end) = range
//    VonObject("Range", None, Vector(
//      VonMember("begin", vonifyPos(begin)),
//      VonMember("end", vonifyPos(end))))
//  }
//
//  def vonifyPos(position: Position): IVonData = {
//    VonObject("Pos", None, Vector(
//      VonMember("line", vonifyPos(position.line)),
//      VonMember("col", vonifyPos(position.column))))
//  }
//
//  def vonifyBlock(block: BlockPE): IVonData = {
//    val BlockPE(elements) = block
//    VonObject("Block", None, Vector(
//      VonMember("elements", VonArray(None, elements.map(vonifyExpression).toVector))))
//  }
//
//  def vonifyExpression(expr: IExpressionPE): IVonData = {
//    expr match {
//      case LookupPE(name, templateArgs) => {
//        VonObject("Block", None, Vector(
//          VonMember("name", VonStr(name)),
//          VonMember("templateArgs", VonArray(None, templateArgs.map(vonifyTemplexPT).toVector))))
//      }
//    }
//  }
//
//  def vonifyTemplexPT(templex: ITemplexPT): IVonData = {
//    vimpl()
//  }
//
//  def vonifyTemplexPT(templex: ITemplexPT): IVonData = {
//    vimpl()
//  }
//
//  def vonifyPattern(param: PatternPP): IVonData = {
//    vimpl()
//  }
//
//  def vonifyRulexPR(rulex: IRulexPR): IVonData = {
//    vimpl()
//  }
//
//  def vonifyStruct(struct: StructP): IVonData = {
//    vimpl()
//  }
//
//  def vonifyInterface(interface: InterfaceP): IVonData = {
//    vimpl()
//  }
//
//  def vonifyImpl(impl: ImplP): IVonData = {
//    vimpl()
//  }
//
//  def vonifyOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
//    opt match {
//      case None => VonObject("None", None, Vector())
//      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
//    }
//  }
}
