package net.verdagon.vale

object OptingArrayList {
val code =
  """struct List<E> rules(E Ref) {
    |  array Array<mut, Opt<E>>;
    |  size Int;
    |}
    |fn List<E>() rules(E Ref) {
    |  List<E>(Array<mut, Opt<E>>(0, &IFunction1<mut, Int, Opt<E>>((index){ panic()})), 0)
    |}
    |fn len<E>(list &List<E>) { list.size }
    |fn add<E>(list &List<E>, newElement E) {
    |  if (list.size == list.len()) {
    |    newLen = if (len(list) == 0) { 1 } else { len(list) * 2 };
    |    newArray =
    |        Array<mut, Opt<E>>(newLen, &IFunction1<mut, Int, Opt<E>>((index){
    |          = if (index < len(list)) {
    |              = (mut list.array[index] = None<E>());
    |            } else {
    |              result Opt<E> = None<E>();
    |              = result;
    |            }
    |        }));
    |    mut list.array = newArray;
    |  }
    |  mut list.array[list.size] = Some<E>(newElement);
    |  mut list.size = list.size + 1;
    |}
    |// todo make that return a &E
    |fn get<E>(list &List<E>, index Int) &Opt<E> {
    |  a = list.array;
    |  = a[index];
    |}
    |fn set<E>(list &List<E>, index Int, value E) Void {
    |  mut list.array[index] = Some(value);
    |}
    |fn toArray<M, E>(list &List<E>) Array<M, E> rules(M Mutability) {
    |  Array<M, E>(list.len(), &IFunction1<mut, Int, E>((i){ list.get(i).get()}))
    |}
    |fn toList<E>(arr &Array<_, E>) List<E> {
    |  list = List<E>();
    |  arr each (elem){
    |    list.add(elem);
    |  };
    |  = list;
    |}
    |""".stripMargin
}
