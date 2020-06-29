; ModuleID = 'immstruct'
source_filename = "immstruct"

%"C(\22Vec3i\22)" = type { i64, i64, i64 }

define %"C(\22Vec3i\22)" @"F(\22Vec3i\22,[],[R(*,i),R(*,i),R(*,i)])"(i64, i64, i64) {
block1:
  %"C(\22Vec3i\22):CodeVarName(\22x\22)" = insertvalue %"C(\22Vec3i\22)" undef, i64 %0, 0
  %"C(\22Vec3i\22):CodeVarName(\22y\22)" = insertvalue %"C(\22Vec3i\22)" %"C(\22Vec3i\22):CodeVarName(\22x\22)", i64 %1, 1
  %"C(\22Vec3i\22):CodeVarName(\22z\22)" = insertvalue %"C(\22Vec3i\22)" %"C(\22Vec3i\22):CodeVarName(\22y\22)", i64 %2, 2
  ret %"C(\22Vec3i\22)" %"C(\22Vec3i\22):CodeVarName(\22z\22)"
}

define i64 @"F(\22main\22)"() {
block1:
  %"C(\22Vec3i\22):CodeVarName(\22y\22)" = extractvalue %"C(\22Vec3i\22)" { i64 4, i64 5, i64 6 }, 1
  ret i64 %"C(\22Vec3i\22):CodeVarName(\22y\22)"
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  ret i64 5
}
