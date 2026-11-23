# A graph library for Rosetta Code

`graphs.incl.a68` is a library of graphs, a priority queue and disjoint
sets in plain Algol 68, for programs written for Algol 68 Genie, the
notation of the ALGOL 68 solutions on Rosetta Code. A program reads it
with `PR read "graphs.incl.a68" PR`, as the solutions there read
`primes.incl.a68` or `rows.incl.a68`. It runs with Algol 68 Genie and with
a68j (`-fgenie`) and gives the same results with both.

Many of the Rosetta Code tasks without an ALGOL 68 solution are graph
algorithms: Tarjan, Kosaraju, Gabow, Johnson, Borůvka, Christofides,
Hopcroft-Karp, Hierholzer, Bron–Kerbosch, topological sort, A*, maze
solving. The library provides the common parts; the algorithm that a task
is about stays in its solution unless the library has it already (Tarjan,
topological sort).

## Contents

| File | |
|---|---|
| `graphs.incl.a68` | the library |
| `topological-sort.a68` | the task "Topological sort": a compile order of VHDL libraries, and the libraries that cannot be ordered once dw01 depends on dw04 |
| `a-star-search-algorithm.a68` | the task "A* search algorithm": a route across an 8 × 8 grid with a barrier, its cost and a picture |
| `test-paths.a68` | Dijkstra (directed and undirected), Bellman-Ford, Johnson's algorithm, negative cycles, A* |
| `test-structure.a68` | Tarjan, Kosaraju (from `depth first` and `reversed`), topological order, breadth first and depth first search, components, Kruskal, the heap, disjoint sets, 5000 named vertices |
| `test-real.a68` | `WEIGHT = REAL`: a graph of Euclidean distances |
| `test-taxicab.a68` | `WEIGHT = LONG INT`: taxicab numbers with the heap (the 1st to 25th and the 2000th to 2006th, as in the task "Taxicab numbers") |
| `test-large.a68` | a grid of 200 × 200 squares, 40 000 vertices and 132 749 edges |
| `NAME.out` | the output and exit status of `NAME.a68`, recorded with Algol 68 Genie 3.13.4 |

The data of the solutions and of the tests of Dijkstra, Tarjan and
topological sort come from the task descriptions on Rosetta Code (GNU Free
Documentation License 1.3); those of Johnson's algorithm and Kruskal from
the examples of the Wikipedia articles.

```sh
sh tools/graphs_tests.sh [JAVA]              # run with a68j -fgenie, compare with NAME.out
sh tools/graphs_tests.sh --record [A68G]     # record NAME.out with Algol 68 Genie
```

## Using the library

Algol 68 has no generic modes, so the program declares the mode of the
weights before it reads the library, as for the templates of Rosetta
Code:

```
MODE WEIGHT = INT;                    # or LONG INT, REAL, LONG REAL #
WEIGHT infinite weight = max int;
PR read "graphs.incl.a68" PR
```

The library needs only `+`, `-` and `<` of `WEIGHT`. The choice matters
for a68j, whose `INT` and `REAL` have 32 bits, where Algol 68 Genie has 64:
weights and sums of weights above `max int` of a68j (2147483647) need
`LONG INT`, and a `REAL` of a68j holds whole numbers exactly only up to
16777216 (2 ** 24). The taxicab numbers, sums of two cubes up to about
3.5e9, are keys of mode `LONG INT` for this reason.

Vertices are the numbers 1 to `n OF g`, edges 1 to `m OF g`. An edge of an
undirected graph is stored once and is an out edge of both its ends;
`other end (g, e, v)` gives the end that is not `v`. A vertex may have a
name: `vertex (g, "a")` finds it or adds it, and `add edges` takes the
edges as a display of names and weights.

| Procedure | |
|---|---|
| `new graph (directed) REF GRAPH` | an empty graph |
| `vertex (g, name) INT` | the vertex of a name, added if it is new; an unnamed vertex for `""` |
| `add vertices (g, count) INT` | adds unnamed vertices, yields the first of them |
| `add edge (g, tail, head, weight)` | adds an edge; its number is then `m OF g` |
| `add edges (g, (("a", "b", 7), ...))` | adds edges between named vertices (`[] LABELLED`) |
| `vertex name (g, v) STRING` | the name, or the number for an unnamed vertex |
| `out edges (g, v) REF [] INT` | the out edges of `v`, as a slice of `adjacent OF g` |
| `other end (g, e, v) INT` | the other end of edge `e` |
| `reversed (g) REF GRAPH` | the graph with every edge reversed, with the same numbers |
| `new heap REF MINHEAP`, `heap push (h, key, item)`, `heap pop (h) ENTRY`, `heap top (h) ENTRY`, `heap size (h) INT` | a binary min-heap of (key, item), ordered by key and then by item; no decrease-key: push again and skip stale entries |
| `new partition (n) REF PARTITION`, `find set (p, x) INT`, `unite (p, x, y) BOOL` | disjoint sets with union by rank and path halving; `unite` yields `FALSE` if x and y are in the same set, `count OF p` is the number of sets |
| `breadth first (g, s) SEARCH` | the order of the vertices reached from `s`, the edge by which each was reached (`via`) and its depth (-1: not reached) |
| `unseen (g) REF [] BOOL`, `depth first (g, s, seen, enter, leave)` | an iterative depth first search from `s` over the vertices not seen yet; `enter (v)` when a vertex is reached, `leave (v)` when its out edges are done |
| `topological order (g, order) BOOL` | Kahn's algorithm; `FALSE` if there is a cycle, `order` then holds the vertices that could be ordered |
| `connected components (g)`, `strong components (g) COMPONENTS` | the number of components and the component of each vertex; strong components with Tarjan's algorithm, iterative, numbered in reverse topological order |
| `shortest paths (g, s) PATHS`, `shortest paths by (g, s, weight)` | Dijkstra's algorithm, with the weights of the edges or with `weight (e)`, which must not be negative (Johnson's algorithm reweights the edges this way) |
| `bellman ford (g, s, paths) BOOL` | `FALSE` if a negative cycle can be reached from `s` |
| `path to (g, paths, t) [] INT` | the vertices of a shortest path to `t`, empty if `t` cannot be reached |
| `a star (g, s, t, estimate) [] INT` | A* with an estimate that never overestimates and is consistent |
| `minimum spanning tree (g) [] INT` | Kruskal's algorithm: the edges of a minimum spanning forest |
| `path text (g, path) STRING` | `a -> b -> c` |

The results are deterministic: the heap orders entries with the same key
by their item, so a68j and Algol 68 Genie choose the same path among
paths of the same length.

## Algol 68 Genie

Two things came up with large graphs in Algol 68 Genie 3.13.4:

- The garbage collector lost a row that was only an element of a
  structure display: `HEAP PARTITION := (graph zeros (n), graph zeros (n),
  n)` with n = 40 000, after a large graph, gave a row with the bounds
  `16:1`. With a larger heap (`--heap 2G`) the same program ran correctly.
  The library binds every row to an identifier before it puts it into a
  display. A small program that shows the defect has not been found yet.
- A slice of a row is a new object on the heap, also `out edges`. When the
  live data fill the heap almost completely, every such object starts a
  garbage collection: the 40 000 calls of `out edges` in `test-large`
  took 90 s instead of 0.5 s, until the row of names was made only for
  graphs with names (a string of a row is an object of its own, and the
  graph had 80 000 empty ones). The algorithms of the library use
  `first OF g` and `adjacent OF g` directly, without slices; so can a
  program, after `graph index (g)`:

  ```
  graph index (g);
  FOR k FROM (first OF g)[v] TO (first OF g)[v + 1] - 1 DO
     INT e = (adjacent OF g)[k];
     ...
  OD
  ```
