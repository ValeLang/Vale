package net.verdagon.vale.metal

import net.verdagon.von.{IVonData, VonPrinter, VonSyntax}

object MetalPrinter {
  def print(data: IVonData): String = {
    val nameMap =
      Map(
        "Str" -> "s",
        "Int" -> "i",
        "Float" -> "f",
        "Void" -> "v",
        "Bool" -> "b",
        "Function" -> "F",
        "Ref" -> "R",
        "Share" -> "@",
        "Pointer" -> "*",
        "Weak" -> "**",
        "Own" -> "^",
        "Readonly" -> "#",
        "Readwrite" -> "!",
        "Inline" -> "<",
        "Yonder" -> ">",
        "CoordTemplata" -> "TR",
        "KindTemplata" -> "TK",
        "CitizenName" -> "C",
        "CoordListTemplata" -> "TRL",
        "CitizenTemplateName" -> "CT",
        "Immutable" -> "imm",
        "MutabilityTemplata" -> "TM",
        "StructId" -> "SId",
        "InterfaceId" -> "IId",
        "AnonymousSubstructName" -> "AS",
        "LambdaCitizenName" -> "LC",
      )
    val printer = new VonPrinter(VonSyntax(false, true, false, false), Int.MaxValue, nameMap, false);
    printer.print(data)
  }
}
