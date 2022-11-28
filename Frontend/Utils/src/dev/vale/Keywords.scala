package dev.vale

class Keywords(interner: Interner) {
  val func: StrI = interner.intern(StrI("func"))
  val impoort: StrI = interner.intern(StrI("import"))
  val export: StrI = interner.intern(StrI("export"))
  val truue: StrI = interner.intern(StrI("true"))
  val faalse: StrI = interner.intern(StrI("false"))
  val own: StrI = interner.intern(StrI("own"))
  val borrow: StrI = interner.intern(StrI("borrow"))
  val weak: StrI = interner.intern(StrI("weak"))
  val share: StrI = interner.intern(StrI("share"))
  val where: StrI = interner.intern(StrI("where"))
  val inl: StrI = interner.intern(StrI("inl"))
  val heap: StrI = interner.intern(StrI("heap"))
  val imm: StrI = interner.intern(StrI("imm"))
  val mut: StrI = interner.intern(StrI("mut"))
  val vary: StrI = interner.intern(StrI("vary"))
  val fiinal: StrI = interner.intern(StrI("final"))
  val exists: StrI = interner.intern(StrI("exists"))
  val resolve: StrI = interner.intern(StrI("resolve"))
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
  val and: StrI = interner.intern(StrI("and"))
  val or: StrI = interner.intern(StrI("or"))
  val as: StrI = interner.intern(StrI("as"))
  val ro: StrI = interner.intern(StrI("ro"))
  val rw: StrI = interner.intern(StrI("rw"))
  val virtual: StrI = interner.intern(StrI("virtual"))
  val impl: StrI = interner.intern(StrI("impl"))
  val IntCapitalized: StrI = interner.intern(StrI("Int"))
  val Ref: StrI = interner.intern(StrI("Ref"))
  val Kind: StrI = interner.intern(StrI("Kind"))
  val Prot: StrI = interner.intern(StrI("Prot"))
  val RefList: StrI = interner.intern(StrI("RefList"))
  val Ownership: StrI = interner.intern(StrI("Ownership"))
  val Variability: StrI = interner.intern(StrI("Variability"))
  val Mutability: StrI = interner.intern(StrI("Mutability"))
  val Location: StrI = interner.intern(StrI("Location"))
  val Refs: StrI = interner.intern(StrI("Refs"))

  // Symbols the language uses
  val UNDERSCORE: StrI = interner.intern(StrI("_"))
  val DOT_DOT: StrI = interner.intern(StrI(".."))

  // Built-in types
  val int: StrI = interner.intern(StrI("int"))
  val bool: StrI = interner.intern(StrI("bool"))
  val float: StrI = interner.intern(StrI("float"))
  val __Never: StrI = interner.intern(StrI("__Never"))
  val str: StrI = interner.intern(StrI("str"))
  val void: StrI = interner.intern(StrI("void"))
  val i64: StrI = interner.intern(StrI("i64"))
  val i32: StrI = interner.intern(StrI("i32"))
  val i16: StrI = interner.intern(StrI("i16"))
  val i8: StrI = interner.intern(StrI("i8"))
  val u64: StrI = interner.intern(StrI("u64"))
  val u32: StrI = interner.intern(StrI("u32"))
  val u16: StrI = interner.intern(StrI("u16"))
  val u8: StrI = interner.intern(StrI("u8"))

  // Function and type names the language knows about, but aren't really part of the core language
  val plus: StrI = interner.intern(StrI("+"))
  val asterisk: StrI = interner.intern(StrI("*"))
  val slash: StrI = interner.intern(StrI("/"))
  val minus: StrI = interner.intern(StrI("-"))
  val spaceship: StrI = interner.intern(StrI("<=>"))
  val lessEquals: StrI = interner.intern(StrI("<="))
  val less: StrI = interner.intern(StrI("<"))
  val greaterEquals: StrI = interner.intern(StrI(">="))
  val greater: StrI = interner.intern(StrI(">"))
  val tripleEquals: StrI = interner.intern(StrI("==="))
  val doubleEquals: StrI = interner.intern(StrI("=="))
  val notEquals: StrI = interner.intern(StrI("!="))
  // See NSIDN for more on this.
  // Every type, including interfaces, has a function of this name. These won't be virtual.
  val drop: StrI = interner.intern(StrI("drop"))
  val free: StrI = interner.intern(StrI("free"))
  val not: StrI = interner.intern(StrI("not"))
  val range: StrI = interner.intern(StrI("range"))
  val begin: StrI = interner.intern(StrI("begin"))
  val next: StrI = interner.intern(StrI("next"))
  val isEmpty: StrI = interner.intern(StrI("isEmpty"))
  val get: StrI = interner.intern(StrI("get"))
  val underscoresCall: StrI = interner.intern(StrI("__call"))
  val tupleHumanName: StrI = interner.intern(StrI("Tup"))

  // Macros exposed to the user
  val DeriveStructDrop: StrI = interner.intern(StrI("DeriveStructDrop"))
//  val DeriveStructFree: StrI = interner.intern(StrI("DeriveStructFree"))
//  val DeriveImplFree: StrI = interner.intern(StrI("DeriveImplFree"))
  val DeriveAnonymousSubstruct: StrI = interner.intern(StrI("DeriveAnonymousSubstruct"))
//  val DeriveInterfaceFree: StrI = interner.intern(StrI("DeriveInterfaceFree"))
  val DeriveInterfaceDrop: StrI = interner.intern(StrI("DeriveInterfaceDrop"))

  // Everything below here is for compiler internal use only, and aren't exposed to the user.

  // Generators
  val freeGenerator: StrI = interner.intern(StrI("freeGenerator"))
  val dropGenerator: StrI = interner.intern(StrI("dropGenerator"))
  val interfaceFreeGenerator: StrI = interner.intern(StrI("interfaceFreeGenerator"))
  val vale_static_sized_array_drop_into: StrI = interner.intern(StrI("vale_static_sized_array_drop_into"))
  val vale_runtime_sized_array_push: StrI = interner.intern(StrI("vale_runtime_sized_array_push"))
  val vale_runtime_sized_array_pop: StrI = interner.intern(StrI("vale_runtime_sized_array_pop"))
  val vale_runtime_sized_array_mut_new: StrI = interner.intern(StrI("vale_runtime_sized_array_mut_new"))
  val vale_runtime_sized_array_capacity: StrI = interner.intern(StrI("vale_runtime_sized_array_capacity"))
  val vale_runtime_sized_array_len: StrI = interner.intern(StrI("vale_runtime_sized_array_len"))
  val vale_runtime_sized_array_imm_new: StrI = interner.intern(StrI("vale_runtime_sized_array_imm_new"))
  val vale_runtime_sized_array_free: StrI = interner.intern(StrI("vale_runtime_sized_array_free"))
  val vale_runtime_sized_array_drop_into: StrI = interner.intern(StrI("vale_runtime_sized_array_drop_into"))
  val abstractBody: StrI = interner.intern(StrI("abstractBody"))
  val vale_as_subtype: StrI = interner.intern(StrI("vale_as_subtype"))
  val vale_lock_weak: StrI = interner.intern(StrI("vale_lock_weak"))
  val vale_same_instance: StrI = interner.intern(StrI("vale_same_instance"))
  val structConstructorGenerator: StrI = interner.intern(StrI("structConstructorGenerator"))
  val DeriveStructConstructor: StrI = interner.intern(StrI("DeriveStructConstructor"))
  val vale_static_sized_array_free: StrI = interner.intern(StrI("vale_static_sized_array_free"))
  val vale_static_sized_array_len: StrI = interner.intern(StrI("vale_static_sized_array_len"))

  // Random miscellaneous strings used by the compiler
  val emptyString = interner.intern(StrI(""))
  val thiss: StrI = interner.intern(StrI("this"))
  val BOX_HUMAN_NAME: StrI = interner.intern(StrI("__Box"))
  val BOX_MEMBER_NAME: StrI = interner.intern(StrI("__boxee"))
  val T = interner.intern(StrI("T"))
  val V = interner.intern(StrI("V"))
  val DropP1 = interner.intern(StrI("DropP1"))
  val DropStruct = interner.intern(StrI("DropStruct"))
  val DropStructTemplate = interner.intern(StrI("DropStructTemplate"))
  val DropV = interner.intern(StrI("DropV"))
  val FreeP1 = interner.intern(StrI("FreeP1"))
  val FreeStructTemplate = interner.intern(StrI("FreeStructTemplate"))
  val FreeStruct = interner.intern(StrI("FreeStruct"))
  val FreeV = interner.intern(StrI("FreeV"))
  val x = interner.intern(StrI("x"))
  val D = interner.intern(StrI("D"))
  val v = interner.intern(StrI("v"))
  val builtins = interner.intern(StrI("builtins"))
  val arrays = interner.intern(StrI("arrays"))
  val __free_replaced = interner.intern(StrI("__free_replaced"))
  val IS_INTERFACE = interner.intern(StrI("isInterface"))
  val IMPLEMENTS = interner.intern(StrI("implements"))
  val isCallable = interner.intern(StrI("isCallable"))
  val REF_LIST_COMPOUND_MUTABILITY = interner.intern(StrI("refListCompoundMutability"))
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
  val my_module: StrI = interner.intern(StrI("my_module"))
}
