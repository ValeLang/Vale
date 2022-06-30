package dev.vale

class Keywords(interner: Interner) {

  val BEGIN = interner.intern(StrI("begin"))
  val NEXT = interner.intern(StrI("next"))
  val IS_EMPTY = interner.intern(StrI("isEmpty"))
  val GET = interner.intern(StrI("get"))

  val UNDERSCORE: StrI = interner.intern(StrI("_"))
  val INT: StrI = interner.intern(StrI("int"))
  val REF: StrI = interner.intern(StrI("Ref"))
  val KIND: StrI = interner.intern(StrI("Kind"))
  val PROT: StrI = interner.intern(StrI("Prot"))
  val REFLIST: StrI = interner.intern(StrI("RefList"))
  val OWNERSHIP: StrI = interner.intern(StrI("Ownership"))
  val VARIABILITY: StrI = interner.intern(StrI("Variability"))
  val MUTABILITY: StrI = interner.intern(StrI("Mutability"))
  val LOCATION: StrI = interner.intern(StrI("Location"))

  val func = interner.intern(StrI("func"))

  val truue: StrI = interner.intern(StrI("true"))
  val faalse: StrI = interner.intern(StrI("false"))
  val OWN: StrI = interner.intern(StrI("own"))
  val BORROW: StrI = interner.intern(StrI("borrow"))
  val WEAK: StrI = interner.intern(StrI("weak"))
  val SHARE: StrI = interner.intern(StrI("share"))
  val INL: StrI = interner.intern(StrI("inl"))
  val HEAP: StrI = interner.intern(StrI("heap"))
  val IMM: StrI = interner.intern(StrI("imm"))
  val MUT: StrI = interner.intern(StrI("mut"))
  val VARY: StrI = interner.intern(StrI("vary"))
  val FINAL: StrI = interner.intern(StrI("final"))

  val self: StrI = interner.intern(StrI("self"))
  val iff: StrI = interner.intern(StrI("if"))
  val elsse: StrI = interner.intern(StrI("else"))
  val foreeach: StrI = interner.intern(StrI("foreach"))
  val in: StrI = interner.intern(StrI("in"))
  val parallel: StrI = interner.intern(StrI("parallel"))
  val break: StrI = interner.intern(StrI("break"))
  val retuurn: StrI = interner.intern(StrI("return"))
  val whiile: StrI = interner.intern(StrI("while"))
  val destruct: StrI = interner.intern(StrI("destruct"))
  val set: StrI = interner.intern(StrI("set"))
  val unlet: StrI = interner.intern(StrI("unlet"))
  val block: StrI = interner.intern(StrI("block"))
  val pure: StrI = interner.intern(StrI("pure"))
  val unsafe: StrI = interner.intern(StrI("unsafe"))

  val DOT_DOT: StrI = interner.intern(StrI(".."))
  val ASTERISK: StrI = interner.intern(StrI("*"))
  val SLASH: StrI = interner.intern(StrI("/"))
  val MINUS: StrI = interner.intern(StrI("-"))
  val SPACESHIP: StrI = interner.intern(StrI("<=>"))
  val LESS_THAN_OR_EQUAL: StrI = interner.intern(StrI("<="))
  val LESS_THAN: StrI = interner.intern(StrI("<"))
  val GREATER_THAN_OR_EQUAL: StrI = interner.intern(StrI(">="))
  val GREATER_THAN: StrI = interner.intern(StrI(">"))
  val TRIPLE_EQUALS: StrI = interner.intern(StrI("==="))
  val DOUBLE_EQUALS: StrI = interner.intern(StrI("=="))
  val NOT_EQUAL: StrI = interner.intern(StrI("!="))
  val AND: StrI = interner.intern(StrI("and"))
  val OR: StrI = interner.intern(StrI("or"))
  val as: StrI = interner.intern(StrI("as"))

  val underscoresCall: StrI = interner.intern(StrI("__call"))

  val impoort: StrI = interner.intern(StrI("import"))
  val export: StrI = interner.intern(StrI("export"))

  val DeriveStructDrop: StrI = interner.intern(StrI("DeriveStructDrop"))
  val DeriveStructFree: StrI = interner.intern(StrI("DeriveStructFree"))
  val DeriveImplFree: StrI = interner.intern(StrI("DeriveImplFree"))
  val dropGenerator: StrI = interner.intern(StrI("dropGenerator"))
  val interfaceFreeGenerator: StrI = interner.intern(StrI("interfaceFreeGenerator"))
  val DeriveInterfaceFree: StrI = interner.intern(StrI("DeriveInterfaceFree"))
  val DeriveInterfaceDrop: StrI = interner.intern(StrI("DeriveInterfaceDrop"))
  val DeriveImplDrop: StrI = interner.intern(StrI("DeriveImplDrop"))
  val freeGenerator: StrI = interner.intern(StrI("freeGenerator"))

  val int: StrI = interner.intern(StrI("int"))
  val i64: StrI = interner.intern(StrI("i64"))
  val bool: StrI = interner.intern(StrI("bool"))
  val float: StrI = interner.intern(StrI("float"))
  val __Never: StrI = interner.intern(StrI("__Never"))
  val str: StrI = interner.intern(StrI("str"))
  val void: StrI = interner.intern(StrI("void"))

  val vale_static_sized_array_drop_into: StrI = interner.intern(StrI("vale_static_sized_array_drop_into"))
  val vale_runtime_sized_array_push: StrI = interner.intern(StrI("vale_runtime_sized_array_push"))
  val vale_runtime_sized_array_pop: StrI = interner.intern(StrI("vale_runtime_sized_array_pop"))
  val vale_runtime_sized_array_mut_new: StrI = interner.intern(StrI("vale_runtime_sized_array_mut_new"))
  val vale_runtime_sized_array_capacity: StrI = interner.intern(StrI("vale_runtime_sized_array_capacity"))
  val vale_runtime_sized_array_len: StrI = interner.intern(StrI("vale_runtime_sized_array_len"))
  val vale_runtime_sized_array_imm_new: StrI = interner.intern(StrI("vale_runtime_sized_array_imm_new"))
  val vale_runtime_sized_array_free: StrI = interner.intern(StrI("vale_runtime_sized_array_free"))
  val vale_runtime_sized_array_drop_into: StrI = interner.intern(StrI("vale_runtime_sized_array_drop_into"))


  val CALL_FUNCTION_NAME: StrI = interner.intern(StrI("__call"))

  // See NSIDN for more on this.
  // Every type, including interfaces, has a function of this name. These won't be virtual.
  val DROP_FUNCTION_NAME: StrI = interner.intern(StrI("drop"))

  val tupleHumanName: StrI = interner.intern(StrI("Tup"))
  val abstractBody: StrI = interner.intern(StrI("abstractBody"))
  val vale_as_subtype: StrI = interner.intern(StrI("vale_as_subtype"))
  val vale_lock_weak: StrI = interner.intern(StrI("vale_lock_weak"))
  val vale_same_instance: StrI = interner.intern(StrI("vale_same_instance"))

  val structConstructorGenerator: StrI = interner.intern(StrI("structConstructorGenerator"))
  val DeriveStructConstructor: StrI = interner.intern(StrI("DeriveStructConstructor"))
  val thiss: StrI = interner.intern(StrI("this"))

  val vale_static_sized_array_free: StrI = interner.intern(StrI("vale_static_sized_array_free"))
  val vale_static_sized_array_len: StrI = interner.intern(StrI("vale_static_sized_array_len"))

  val BOX_HUMAN_NAME: StrI = interner.intern(StrI("__Box"))

  val BOX_MEMBER_NAME: StrI = interner.intern(StrI("__boxee"))

  val emptyString = interner.intern(StrI(""))

  val T = interner.intern(StrI("T"))
  val V = interner.intern(StrI("V"))

  val DropP1 = interner.intern(StrI("DropP1"))
  val DropStruct = interner.intern(StrI("DropStruct"))
  val DropV = interner.intern(StrI("DropV"))
  val x = interner.intern(StrI("x"))

  val ro = interner.intern(StrI("ro"))
  val rw = interner.intern(StrI("rw"))
  val imm = interner.intern(StrI("imm"))

  val VIRTUAL = interner.intern(StrI("virtual"))
  val IMPL = interner.intern(StrI("impl"))
  val IN = interner.intern(StrI("in"))

  val SELF = interner.intern(StrI("self"))
  val PLUS = interner.intern(StrI("+"))
  val NOT = interner.intern(StrI("not"))
  val RANGE = interner.intern(StrI("range"))

  val v = interner.intern(StrI("v"))
  val builtins = interner.intern(StrI("builtins"))
  val arrays = interner.intern(StrI("arrays"))

  val __free_replaced = interner.intern(StrI("__free_replaced"))

  val IS_INTERFACE = interner.intern(StrI("isInterface"))
  val IMPLEMENTS = interner.intern(StrI("implements"))
  val REF_LIST_COMPOUND_MUTABILITY = interner.intern(StrI("refListCompoundMutability"))
  val REFS = interner.intern(StrI("Refs"))
  val ANY = interner.intern(StrI("any"))

  val IFUNCTION = interner.intern(StrI("IFunction"))
  val TUP = interner.intern(StrI("Tup"))

  val M = interner.intern(StrI("M"))
  val E = interner.intern(StrI("E"))
  val F = interner.intern(StrI("F"))
  val Array = interner.intern(StrI("Array"))

  val List = interner.intern(StrI("List"))
  val add = interner.intern(StrI("add"))
  val Opt = interner.intern(StrI("Opt"))
  val Some = interner.intern(StrI("Some"))
  val None = interner.intern(StrI("None"))
  val Result = interner.intern(StrI("Result"))
  val Ok = interner.intern(StrI("Ok"))
  val Err = interner.intern(StrI("Err"))

  val Functor1 = interner.intern(StrI("Functor1"))
  val asterisk = interner.intern(StrI("*"))

  val my_module: StrI = interner.intern(StrI("my_module"))
}
