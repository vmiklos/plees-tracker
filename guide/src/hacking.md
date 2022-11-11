# Development notes

## Coding style

- Prefer initializing properties inline over spelling them out in a separate `init {` block.

- Use annotations (`@Foo`) only when it makes the code easier to read.

- Prefer references (`Foo`) over nullable references (`Foo?`).

- Prefer variable names which are not keywords over working the problem around with backticks
  ('`is`').

- Prefer constructs like `foo?.let {` over non-null assertions (`!!`).

## Complex parts of the app

The app code is quite simple, this is just fancy stopwatch after all. But some parts are nontrivial:

- Making sure that the timer doesn't stop, by launching a proper background service was hard to
  figure out.

- The icon was surprisingly challenging to add, mostly because every SVG editor will just scale your
  image, but exactly scaling is ignored by Android Studio's SVG import.

- The recycler view was tricky to set up: most examples are overcomplicated, when really what was
  needed here is just an adapter and a holder class.

## Kotlin

If you are used to Java, then not spelling out type names all over the place is confusing in Kotlin.
See
<https://stackoverflow.com/questions/54851861/how-do-i-activate-type-annotations-hints-in-kotlin-like-depicted>
on how to let Android Studio show these types for you without polluting the code.
