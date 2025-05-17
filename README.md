# AI Checkers

An academic checkers framework that lets you pit human players and AIs against each other under a strict time-per-move limit.  
The referee is written in C and can launch engines written in **any** language—sample bots are provided in C (`myprog.c`) and Java (`MyProg.java`). An optional X11 / Motif front-end (`xcheck`) gives you a clickable board.

---

## Directory structure

| File / folder | Purpose |
|---------------|---------|
| `checkers.c`, `checkers.h` | Command-line referee and game loop |
| `graphics.c`, `graphics.h` | X/Motif GUI (built only when graphics headers are present) |
| `myprog.c`, `myprog.h` | Example C engine (iterative deepening + heuristics) |
| `MyProg.java` | Example Java engine |
| `hang.c` | Utility that purposefully exceeds its time budget (for testing) |
| `Makefile` | Build targets for referee, GUI, and sample engines |

---

## Building

### Requirements
* GCC / Clang and **make**  
* POSIX threads (`-lpthread`)  
* *GUI (optional)* – X11 & OpenMotif development headers  
* *Java engine* – JDK 8+

### Compile everything

```bash
# Native binaries
make            # builds  checkers  (CLI) and  xcheck  (GUI, if deps found)

# Java engine
javac MyProg.java
