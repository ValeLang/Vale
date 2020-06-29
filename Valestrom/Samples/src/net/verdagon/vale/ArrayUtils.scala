package net.verdagon.vale

object ArrayUtils {
  val code =
    """
      |fn toArray<M, N, E, SM>(seq &[<SM> N * E]) rules(M Mutability) {
      |  Array<M, E>(N, &IFunction1<SM, Int, E>((i){ seq[i]}))
      |}
      |
      |//fn each<N, E, F, M>(seq &[<M> N * E], func F) Void {
      |//  Array<mut, Int>(N, &IFunction1<mut, Int, Int>((i){ func(seq[i]); = 0; }));
      |//}
      |
      |//fn map<M>(seq &[<_> N * E], func F) rules(M Mutability) {
      |//  Array<M>(N, (i){ func(seq[i]) })
      |//}
      |
      |//fn each<E, F>(arr &Array<_, E>, func F) Void {
      |//  Array<mut, Int>(arr.len(), &IFunction1<mut, Int, Void>((i){ func(arr[i]); = 0; }));
      |//}
      |
      |//fn map<M>(arr &Array<_, E>, func F) rules(M Mutability) {
      |//  Array<M>(arr.len(), (i){ func(arr[i]) })
      |//}
      |
      |fn has<E, F>(arr &Array<_, E>, elem E, equator F) Bool {
      |  i = 0;
      |  while (i < arr.len()) {
      |    if ((equator)(arr[i], elem)) {
      |      ret true;
      |    }
      |    mut i = i + 1;
      |  }
      |  = false;
      |}
      |
      |fn has<E>(arr &Array<_, E>, elem E) Bool {
      |  has(arr, elem, ==)
      |}
      |
      |fn has<E, F>(seq &[<_> _ * E], elem E, equator F) Bool {
      |  i = 0;
      |  while (i < seq.len()) {
      |    if ((equator)(seq[i], elem)) {
      |      ret true;
      |    }
      |    mut i = i + 1;
      |  }
      |  = false;
      |}
      |
      |fn has<E>(seq &[<_> _ * E], elem E) Bool {
      |  has(seq, elem, ==)
      |}
      |
      |fn Arr<M, F>(n Int, generator &F) Array<M, T>
      |rules(M Mutability, T Ref, Prot("__call", (&F, Int), T))
      |{
      |  Array<M>(n, &IFunction1<mut, Int, T>(generator))
      |}
      |
      |fn each<M, T, F>(arr &Array<M, T>, func F) Void {
      |  i! = 0;
      |  l = len(&arr);
      |  while (i < l) {
      |    func(arr[i]);
      |    mut i = i + 1;
      |  }
      |}
      |
      |fn indices<>(arr &Array<_, _>) Array<imm, Int> {
      |  Arr<imm>(len(arr), {_})
      |}
      |
      |fn eachI<F>(arr &Array<_, _>, func F) Void {
      |  i! = 0;
      |  l = len(&arr);
      |  while (i < l) {
      |    func(i, arr[i]);
      |    mut i = i + 1;
      |  }
      |}
      |
      |""".stripMargin
}
