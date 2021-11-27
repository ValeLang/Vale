package net.verdagon.vale.templar.names

import net.verdagon.vale._
import net.verdagon.vale.scout.IRuneS
import net.verdagon.vale.templar.ast.LocationInFunctionEnvironment
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.templata.{CoordTemplata, ITemplata}
import net.verdagon.vale.templar.types._

// Scout's/Astronomer's name parts correspond to where they are in the source code,
// but Templar's correspond more to what packages and stamped functions / structs
// they're in. See TNAD.

case class FullNameT[+T <: INameT](
  packageCoord: PackageCoordinate,
  initSteps: Vector[INameT],
  last: T
)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  // PackageTopLevelName2 is just here because names have to have a last step.
  vassert(!initSteps.contains(PackageTopLevelNameT()))

  vassert(!initSteps.exists({
    case AnonymousSubstructTemplateNameT(_) => true
    case _ => false
  }))
  vcurious(initSteps.distinct == initSteps)

  this match {
    case FullNameT(PackageCoordinate.TEST_TLD, Vector(), FunctionNameT("main", Vector(), Vector())) =>
    case _ =>
  }

  def steps: Vector[INameT] = {
    last match {
      case PackageTopLevelNameT() => initSteps
      case _ => initSteps :+ last
    }
  }
  def addStep[Y <: INameT](newLast: Y): FullNameT[Y] = {
    FullNameT[Y](packageCoord, steps, newLast)
  }
  def init: FullNameT[INameT] = {
    if (initSteps.isEmpty) {
      if (last == PackageTopLevelNameT()) {
        vimpl()
      } else {
        FullNameT(packageCoord, Vector(), PackageTopLevelNameT())
      }
    } else {
      FullNameT(packageCoord, initSteps.init, initSteps.last)
    }
  }

  def parent: Option[FullNameT[INameT]] = {
    if (initSteps.isEmpty) {
      packageCoord.parent match {
        case None => None
        case Some(parentPackage) => Some(FullNameT(parentPackage, Vector(), PackageTopLevelNameT()))
      }
    } else {
      Some(FullNameT(packageCoord, initSteps.init, initSteps.last))
    }
  }

  def selfAndParents: List[FullNameT[INameT]] = {
    parent match {
      case None => List(this)
      case Some(parent) => this :: parent.selfAndParents
    }
  }

  def parents: List[FullNameT[INameT]] = {
    parent match {
      case None => List()
      case Some(parent) => parent.selfAndParents
    }
  }
}

sealed trait INameT
sealed trait ITemplateNameT extends INameT
sealed trait IFunctionTemplateNameT extends ITemplateNameT {
  def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT
}
sealed trait IFunctionNameT extends INameT {
  def templateArgs: Vector[ITemplata]
  def parameters: Vector[CoordT]
}
sealed trait ICitizenTemplateNameT extends ITemplateNameT {
  def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT
}
sealed trait ICitizenNameT extends INameT {
  def template: ICitizenTemplateNameT
  def templateArgs: Vector[ITemplata]
}
case class ImplDeclareNameT(codeLocation: CodeLocationS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class LetNameT(codeLocation: CodeLocationS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class ExportAsNameT(codeLocation: CodeLocationS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }

case class RawArrayNameT(mutability: MutabilityT, elementType: CoordT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class StaticSizedArrayNameT(size: Int, arr: RawArrayNameT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class RuntimeSizedArrayNameT(arr: RawArrayNameT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
sealed trait IVarNameT extends INameT
case class TemplarBlockResultVarNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class TemplarFunctionResultVarNameT() extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class TemplarTemporaryVarNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class TemplarPatternMemberNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class TemplarIgnoredParamNameT(num: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class TemplarPatternDestructureeNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class UnnamedLocalNameT(codeLocation: CodeLocationS) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class ClosureParamNameT() extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class ConstructingMemberNameT(name: String) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class MagicParamNameT(codeLocation2: CodeLocationS) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class CodeVarNameT(name: String) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
// We dont use CodeVarName2(0), CodeVarName2(1) etc because we dont want the user to address these members directly.
case class AnonymousSubstructMemberNameT(index: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class PrimitiveNameT(humanName: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
// Only made in templar
case class PackageTopLevelNameT() extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class ProjectNameT(name: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class PackageNameT(name: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }
case class RuneNameT(rune: IRuneS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;   }

// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables, but are still figuring out its template args and params.
case class BuildingFunctionNameWithClosuredsT(
  templateName: IFunctionTemplateNameT,
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


}
// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables and template args, but are still figuring out its params.
case class BuildingFunctionNameWithClosuredsAndTemplateArgsT(
  templateName: IFunctionTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


}

case class ExternFunctionNameT(
  humanName: String,
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def templateArgs: Vector[ITemplata] = Vector.empty



}

case class FunctionNameT(
  humanName: String,
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


}

case class FunctionTemplateNameT(
    humanName: String,
    codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    FunctionNameT(humanName, templateArgs, params)
  }
}

case class LambdaTemplateNameT(
  codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    FunctionNameT(CallTemplar.CALL_FUNCTION_NAME, templateArgs, params)
  }

}
case class ConstructorTemplateNameT(
  codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = vimpl()
}

case class FreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    val Vector(CoordT(ShareT, ReadonlyT, kind)) = params
    FreeNameT(templateArgs, kind)
  }
}
case class FreeNameT(templateArgs: Vector[ITemplata], kind: KindT) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, kind))
}

// See NSIDN for why we have these virtual names
case class VirtualFreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    val Vector(CoordT(ShareT, ReadonlyT, kind)) = params
    VirtualFreeNameT(templateArgs, kind)
  }
}
// See NSIDN for why we have these virtual names
case class VirtualFreeNameT(templateArgs: Vector[ITemplata], param: KindT) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, param))
}

// Vale has no Self, its just a convenient first name parameter.
// See also SelfNameS.
case class SelfNameT() extends IVarNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class ArbitraryNameT() extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class ConstructorNameT(
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def templateArgs: Vector[ITemplata] = Vector.empty

}

case class CitizenNameT(
  template: ICitizenTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  vpass()
}

case class LambdaCitizenTemplateNameT(
  codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty


  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    vassert(templateArgs.isEmpty)
    LambdaCitizenNameT(this)
  }
}
case class LambdaCitizenNameT(
  template: LambdaCitizenTemplateNameT
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty


}
case class AnonymousSubstructLambdaTemplateNameT(
  codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty


  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    vassert(templateArgs.isEmpty)
    AnonymousSubstructLambdaNameT(this)
  }
}
case class AnonymousSubstructLambdaNameT(
  template: AnonymousSubstructLambdaTemplateNameT
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty


}
case class CitizenTemplateNameT(
  humanName: String,
  // We don't include a CodeLocation here because:
  // - There's no struct overloading, so there should only ever be one, we don't have to disambiguate
  //   with code locations
  // - It makes it easier to determine the CitizenTemplateNameT from a CitizenNameT which doesn't
  //   remember its code location.
  //codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    CitizenNameT(this, templateArgs)
  }
}
case class AnonymousSubstructTemplateNameT(
  // This happens to be the same thing that appears before this AnonymousSubstructNameT in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  interface: ICitizenTemplateNameT
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  interface match {
    case AnonymousSubstructTemplateNameT(_) => vwat()
    case _ =>
  }

  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    AnonymousSubstructNameT(this, templateArgs)
  }
}
case class AnonymousSubstructConstructorTemplateNameT(
  substruct: ICitizenTemplateNameT
) extends IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;


  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    AnonymousSubstructConstructorNameT(templateArgs, params)
  }
}
case class AnonymousSubstructConstructorNameT(
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class AnonymousSubstructNameT(
  // This happens to be the same thing that appears before this AnonymousSubstructNameT in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  template: AnonymousSubstructTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class AnonymousSubstructImplNameT() extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}