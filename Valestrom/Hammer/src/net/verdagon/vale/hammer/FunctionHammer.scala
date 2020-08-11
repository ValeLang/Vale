package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.{vassert, vassertSome, vfail, vimpl, vwat, metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.{Export2, Extern2, FunctionHeader2, IFunctionAttribute2, Prototype2, UserFunction2}

object FunctionHammer {

  def translateFunctions(hinputs: Hinputs, hamuts: HamutsBox, functions2: List[Function2]):
  (List[FunctionRefH]) = {
    functions2.foldLeft((List[FunctionRefH]()))({
      case ((previousFunctionsH), function2) => {
        val (functionH) = translateFunction(hinputs, hamuts, function2)
        (functionH :: previousFunctionsH)
      }
    })
  }

  def translateFunction(hinputs: Hinputs, hamuts: HamutsBox, function2: Function2):
  (FunctionRefH) = {
    println("Translating function " + function2.header.fullName)
    hamuts.functionRefs.get(function2.header.toPrototype) match {
      case Some(functionRefH) => functionRefH
      case None => {
        val Function2(
            header @ FunctionHeader2(humanName, attrs2, params2, returnType2, _),
            locals2,
            body) = function2;

        val (prototypeH) = translatePrototype(hinputs, hamuts, header.toPrototype);
        val temporaryFunctionRefH = FunctionRefH(prototypeH);
        hamuts.forwardDeclareFunction(header.toPrototype, temporaryFunctionRefH)

        val locals =
          LocalsBox(
            Locals(
              Map[FullName2[IVarName2], VariableIdH](),
              Set[VariableIdH](),
              Map[VariableIdH,Local]()));
        val (bodyH, List()) =
          ExpressionHammer.translate(hinputs, hamuts, locals, body)
        vassert(locals.unstackifiedVars.size == locals.locals.size)
        val resultCoord = bodyH.resultType
        if (resultCoord.kind != NeverH() && resultCoord != prototypeH.returnType) {
          vfail(
            "Result of body's instructions didnt match return type!\n" +
            "Return type:   " + prototypeH.returnType + "\n" +
            "Body's result: " + resultCoord)
        }

        val isAbstract = header.getAbstractInterface != None
        val isExtern = header.attributes.contains(Extern2)
        val attrsH = translateFunctionAttributes(attrs2.filter(_ != Extern2))
        val functionH = FunctionH(prototypeH, isAbstract, isExtern, attrsH, bodyH);
        hamuts.addFunction(header.toPrototype, functionH)

        (temporaryFunctionRefH)
      }
    }
  }

  def translateFunctionAttributes(attributes: List[IFunctionAttribute2]) = {
    attributes.map({
      case UserFunction2 => UserFunctionH
      case Extern2 => vwat() // Should have been filtered out, hammer cares about extern directly
      case Export2 => ExportH
      case x => vimpl(x.toString)
    })
  }

  def translatePrototypes(
      hinputs: Hinputs, hamuts: HamutsBox,
      prototypes2: List[Prototype2]):
  (List[PrototypeH]) = {
    prototypes2 match {
      case Nil => Nil
      case headPrototype2 :: tailPrototypes2 => {
        val (headPrototypeH) = translatePrototype(hinputs, hamuts, headPrototype2)
        val (tailPrototypesH) = translatePrototypes(hinputs, hamuts, tailPrototypes2)
        (headPrototypeH :: tailPrototypesH)
      }
    }
  }

  def translatePrototype(
      hinputs: Hinputs, hamuts: HamutsBox,
      prototype2: Prototype2):
  (PrototypeH) = {
    val Prototype2(fullName2, returnType2) = prototype2;
    val (paramsTypesH) = TypeHammer.translateReferences(hinputs, hamuts, prototype2.paramTypes)
    val (returnTypeH) = TypeHammer.translateReference(hinputs, hamuts, returnType2)
    val (fullNameH) = NameHammer.translateFullName(hinputs, hamuts, fullName2)
    val prototypeH = PrototypeH(fullNameH, paramsTypesH, returnTypeH)
    (prototypeH)
  }

  def translateFunctionRef(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      prototype2: Prototype2):
  (FunctionRefH) = {
    val (prototypeH) = translatePrototype(hinputs, hamuts, prototype2);
    val functionRefH = FunctionRefH(prototypeH);
    (functionRefH)
  }
}
