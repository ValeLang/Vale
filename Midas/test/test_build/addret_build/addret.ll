; ModuleID = 'addret'
source_filename = "addret"

define i64 @"F(\22__addIntInt\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %add = add i64 %1, %0
  ret i64 %add
}

define i64 @"F(\22+\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %add.i = add i64 %1, %0
  ret i64 %add.i
}

define i64 @"F(\22main\22)"() {
block1:
  ret i64 7
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  ret i64 7
}
