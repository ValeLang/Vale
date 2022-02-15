package net.verdagon.vale.hammer

import net.verdagon.vale.metal._
import net.verdagon.vale.{vassert, vassertSome, vfail, vimpl, vwat, metal => m}
import net.verdagon.vale.templar.{Hinputs, _}
import net.verdagon.vale.templar.ast.{ExternT, FunctionHeaderT, FunctionT, IFunctionAttributeT, PrototypeT, PureT, UserFunctionT}
import net.verdagon.vale.templar.names.{FullNameT, IVarNameT}

object FunctionHammer {

  def translateFunctions(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    functions2: Vector[FunctionT]):
  (Vector[FunctionRefH]) = {
    functions2.foldLeft((Vector[FunctionRefH]()))({
      case ((previousFunctionsH), function2) => {
        val (functionH) = translateFunction(hinputs, hamuts, function2)
        Vector(functionH) ++ previousFunctionsH
      }
    })
  }

  def translateFunction(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    function2: FunctionT):
  (FunctionRefH) = {
//    opts.debugOut("Translating function " + function2.header.fullName)
    hamuts.functionRefs.get(function2.header.toPrototype) match {
      case Some(functionRefH) => functionRefH
      case None => {
        val FunctionT(
            header @ FunctionHeaderT(humanName, attrs2, params2, returnType2, _),
            body) = function2;

        val (prototypeH) = translatePrototype(hinputs, hamuts, header.toPrototype);
        val temporaryFunctionRefH = FunctionRefH(prototypeH);
        hamuts.forwardDeclareFunction(header.toPrototype, temporaryFunctionRefH)

        val locals =
          LocalsBox(
            Locals(
              Map[FullNameT[IVarNameT], VariableIdH](),
              Set[VariableIdH](),
              Map[VariableIdH,Local](),
              1));
        val (bodyH, Vector()) =
          ExpressionHammer.translate(hinputs, hamuts, header, locals, body)
        vassert(locals.unstackifiedVars.size == locals.locals.size)
        val resultCoord = bodyH.resultType
        if (resultCoord != prototypeH.returnType) {
          resultCoord.kind match {
            case NeverH(_) => // meh its fine
            case _ => {
              vfail(
                "Result of body's instructions didnt match return type!\n" +
                  "Return type:   " + prototypeH.returnType + "\n" +
                  "Body's result: " + resultCoord)
            }
          }
        }

        val isAbstract = header.getAbstractInterface.nonEmpty
        val isExtern = header.attributes.exists({ case ExternT(packageCoord) => true case _ => false })
        val attrsH = translateFunctionAttributes(attrs2.filter(a => !a.isInstanceOf[ExternT]))
        val functionH = FunctionH(prototypeH, isAbstract, isExtern, attrsH, bodyH);
        hamuts.addFunction(header.toPrototype, functionH)

        (temporaryFunctionRefH)
      }
    }
  }

  def translateFunctionAttributes(attributes: Vector[IFunctionAttributeT]) = {
    attributes.map({
      case UserFunctionT => UserFunctionH
      case PureT => PureH
      case ExternT(_) => vwat() // Should have been filtered out, hammer cares about extern directly
      case x => vimpl(x.toString)
    })
  }

  def translatePrototypes(
      hinputs: Hinputs, hamuts: HamutsBox,
      prototypes2: Vector[PrototypeT]):
  (Vector[PrototypeH]) = {
    prototypes2.map(translatePrototype(hinputs, hamuts, _))
  }

  def translatePrototype(
      hinputs: Hinputs, hamuts: HamutsBox,
      prototype2: PrototypeT):
  (PrototypeH) = {
    val PrototypeT(fullName2, returnType2) = prototype2;
    val (paramsTypesH) = TypeHammer.translateReferences(hinputs, hamuts, prototype2.paramTypes)
    val (returnTypeH) = TypeHammer.translateReference(hinputs, hamuts, returnType2)
    val (fullNameH) = NameHammer.translateFullName(hinputs, hamuts, fullName2)
    val prototypeH = PrototypeH(fullNameH, paramsTypesH, returnTypeH)
    (prototypeH)
  }

  def translateFunctionRef(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      prototype2: PrototypeT):
  (FunctionRefH) = {
    val (prototypeH) = translatePrototype(hinputs, hamuts, prototype2);
    val functionRefH = FunctionRefH(prototypeH);
    (functionRefH)
  }
}
