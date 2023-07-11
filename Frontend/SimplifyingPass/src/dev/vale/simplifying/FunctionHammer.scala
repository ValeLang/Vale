package dev.vale.simplifying

import dev.vale.finalast.{FunctionH, Local, PureH, UserFunctionH, VariableIdH}
import dev.vale.{Keywords, vassert, vfail, vimpl, vwat, finalast => m}
import dev.vale.finalast._
import dev.vale.instantiating.ast._

class FunctionHammer(
    keywords: Keywords,
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer) {
  val expressionHammer =
    new ExpressionHammer(keywords, typeHammer, nameHammer, structHammer, this)

  def translateFunctions(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    functions2: Vector[FunctionDefinitionI]):
  (Vector[FunctionRefH]) = {
    functions2.foldLeft((Vector[FunctionRefH]()))({
      case ((previousFunctionsH), function2) => {
        val (functionH) = translateFunction(hinputs, hamuts, function2)
        Vector(functionH) ++ previousFunctionsH
      }
    })
  }

  def translateFunction(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    function2: FunctionDefinitionI):
  (FunctionRefH) = {
//    opts.debugOut("Translating function " + function2.header.fullName)
    hamuts.functionRefs.get(function2.header.toPrototype) match {
      case Some(functionRefH) => functionRefH
      case None => {
        val FunctionDefinitionI(
            header @ FunctionHeaderI(humanName, attrs2, params2, returnType2),
            _,
            _,
            body) = function2;

        val (prototypeH) = typeHammer.translatePrototype(hinputs, hamuts, header.toPrototype);
        val temporaryFunctionRefH = FunctionRefH(prototypeH);
        hamuts.forwardDeclareFunction(header.toPrototype, temporaryFunctionRefH)

        val locals =
          LocalsBox(
            Locals(
              Map[IVarNameI[cI], VariableIdH](),
              Set[VariableIdH](),
              Map[VariableIdH,Local](),
              1));
        val (bodyH, Vector()) =
          expressionHammer.translate(hinputs, hamuts, header, locals, body)
        vassert(locals.unstackifiedVars.size == locals.locals.size)
        val resultCoord = bodyH.resultType
        if (resultCoord != prototypeH.returnType) {
          resultCoord.kind match {
            case NeverHT(_) => // meh its fine
            case _ => {
              vfail(
                "Result of body's instructions didnt match return type!\n" +
                  "Return type:   " + prototypeH.returnType + "\n" +
                  "Body's result: " + resultCoord)
            }
          }
        }

        val isAbstract = header.getAbstractInterface.nonEmpty
        val isExtern = header.attributes.exists({ case ExternI(packageCoord) => true case _ => false })
        val attrsH = translateFunctionAttributes(attrs2.filter(a => !a.isInstanceOf[ExternI]))
        val functionH = FunctionH(prototypeH, isAbstract, isExtern, attrsH, bodyH);
        hamuts.addFunction(header.toPrototype, functionH)

        (temporaryFunctionRefH)
      }
    }
  }

  def translateFunctionAttributes(attributes: Vector[IFunctionAttributeI]) = {
    attributes.map({
      case UserFunctionI => UserFunctionH
      case PureI => PureH
      case ExternI(_) => vwat() // Should have been filtered out, hammer cares about extern directly
      case x => vimpl(x.toString)
    })
  }

  def translateFunctionRef(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      prototype2: PrototypeI[cI]):
  (FunctionRefH) = {
    val (prototypeH) = typeHammer.translatePrototype(hinputs, hamuts, prototype2);
    val functionRefH = FunctionRefH(prototypeH);
    (functionRefH)
  }
}
