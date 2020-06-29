; ModuleID = 'addret'
source_filename = "addret"

define i64 @"F(\22__addIntInt\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %add = add i64 %0, %1
  ret i64 %add
}

define i64 @"F(\22+\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %2 = alloca i64
  store i64 %0, i64* %2
  %3 = alloca i64
  store i64 %1, i64* %3
  %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22a\22)" = load i64, i64* %2
  %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22b\22)" = load i64, i64* %3
  %4 = call i64 @"F(\22__addIntInt\22,[],[R(*,i),R(*,i)])"(i64 %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22a\22)", i64 %"F(\22+\22,[],[R(*,i),R(*,i)]):CodeVarName(\22b\22)")
  %5 = alloca i64
  store i64 %4, i64* %5
  %6 = load i64, i64* %3
  %7 = load i64, i64* %2
  %8 = load i64, i64* %5
  ret i64 %8
}

define i64 @"F(\22main\22)"() {
block1:
  %0 = alloca i64
  store i64 3, i64* %0
  %1 = load i64, i64* %0
  %2 = alloca i64
  store i64 4, i64* %2
  %3 = load i64, i64* %2
  %4 = call i64 @"F(\22+\22,[],[R(*,i),R(*,i)])"(i64 %1, i64 %3)
  ret i64 %4
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  %valeMainCall = call i64 @"F(\22main\22)"()
  ret i64 %valeMainCall
}
