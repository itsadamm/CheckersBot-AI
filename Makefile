CC              := gcc
CFLAGS          := -Wall -ggdb
CPPFLAGS        := -I./ -I/usr/X11R6/include/Xm -I/usr/X11R6/include -I/usr/include/openmotif
# CPPFLAGS 		:= -I/usr/local/include -I/opt/homebrew/include
# LDFLAGS         := -L/usr/lib/X11R6 -lXm -lXaw -lXmu -lXt -lX11
LDFLAGS         := -L/usr/X11R6/lib -L /usr/X11R6/LessTif/Motif1.2/lib -lXm -lXaw -lXmu -lXt -lX11 -lICE -lSM -pthread -L/usr/lib64/openmotif/
# LDFLAGS 		:= -L/usr/local/lib -L/opt/homebrew/lib -lXm -lXt -lX11


# Uncomment this next line if you'd like to compile the graphical version of the checkers server.
# CFLAGS          += -DGRAPHICS

# Java compiler settings
JAVAC       := javac

all: checkers computer MyProg.class
checkers: checkers.o graphics.o
	${CC} ${CPPFLAGS} ${CFLAGS} -o $@ checkers.o graphics.o ${LDFLAGS}
computer: myprog.o 
	${CC} ${CPPFLAGS} ${CFLAGS} -o $@ myprog.o ${LDFLAGS}

MyProg.class: MyProg.java
	${JAVAC} MyProg.java

%.o: %.c
	${CC} ${CPPFLAGS} ${CFLAGS} -c -o $@ $<

.PHONY: clean
clean:	
	@-rm -f checkers computer *.o *.class
