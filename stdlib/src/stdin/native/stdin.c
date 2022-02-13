#include <stdint.h>
#include <stdio.h>

#include "stdlib/stdinReadInt.h"
#include "stdlib/getch.h"

#ifdef _WIN32
#include <conio.h>
#else
#include <termios.h>
#endif

#ifndef _WIN32
/* Initialize new terminal i/o settings */
static void initTermios(int echo, struct termios* old, struct termios* current) {
  tcgetattr(0, old); /* grab old terminal i/o settings */
  *current = *old; /* make new settings same as old settings */
  current->c_lflag &= ~ICANON; /* disable buffered i/o */
  if (echo) {
      current->c_lflag |= ECHO; /* set echo mode */
  } else {
      current->c_lflag &= ~ECHO; /* set no echo mode */
  }
  tcsetattr(0, TCSANOW, current); /* use these new terminal i/o settings now */
}

/* Restore old terminal i/o settings */
static void resetTermios(struct termios* old) {
  tcsetattr(0, TCSANOW, old);
}

/* Read 1 character - echo defines echo mode */
static char getch_(int echo) {
  struct termios old, current;
  char ch;
  initTermios(echo, &old, &current);
  ch = getchar();
  resetTermios(&old);
  return ch;
}

/* Read 1 character without echo */
static char getch(void) {
  return getch_(0);
}

/* Read 1 character with echo */
static char getche(void) {
  return getch_(1);
}
#endif

ValeInt stdlib_stdinReadInt() {
  ValeInt x = 0;
  scanf("%d", &x);
  return x;
}

ValeInt stdlib_getch() {
  return getch();
}
