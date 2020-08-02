#![allow(non_snake_case)]
#![allow(unused_imports)]

use std::io::stdout;
use crossterm::{ExecutableCommand, cursor::MoveTo};

#[derive(PartialEq)]
pub enum ScreenColor {
  BLACK,
  DARKGRAY,
  TURQUOISE,
  RED,
  LIGHTGRAY,
  ORANGE,
  GREEN,
  WHITE
}

#[derive(new)]
struct ScreenCell {
  foregroundColor: ScreenColor,
  backgroundColor: ScreenColor,
  character: String,
  dirty: bool
}

pub struct Screen {
  width: usize,
  height: usize,
  cells: Vec<Vec<ScreenCell>>,
  statusLine: String,
}

impl Screen {
  pub fn new(width: usize, height: usize) -> Screen {
    println!("\x1b[1;1H\x1b[2J");

    let mut cells = Vec::new();
    for x in 0..width {
      cells.push(Vec::new());
      for _ in 0..height {
        cells[x as usize].push(ScreenCell::new(
          ScreenColor::WHITE,
          ScreenColor::BLACK,
          " ".to_string(),
          true, // All cells start as dirty, so we can display them all now
        ));
      }
    }

    return Screen{
      width: width,
      height: height,
      cells: cells,
      statusLine: "".to_string(),
    };
  }

  pub fn setStatusLine(&mut self, newLine: String) {
    self.statusLine = newLine;
  }

  pub fn setCell(
      &mut self,
      x: usize,
      y: usize,
      backgroundColor: ScreenColor,
      foregroundColor: ScreenColor,
      character: String) {
    let cell = &mut self.cells[x][y];

    if backgroundColor != cell.backgroundColor {
      cell.backgroundColor = backgroundColor;
      cell.dirty = true;
    }

    if foregroundColor != cell.foregroundColor {
      cell.foregroundColor = foregroundColor;
      cell.dirty = true;
    }

    if character != cell.character {
      cell.character = character;
      cell.dirty = true;
    }
  }

  pub fn paintScreen(&self) {
    for x in 0..self.width {
      for y in 0..self.height {
        if self.cells[x][y].dirty {
          self.paintCell(x, y);
        }
      }
    }
    match stdout().execute(MoveTo(0, self.height as u16)) {
      Ok(_) => {}
      Err(_) => { panic!("wat"); }
    }
    println!("{}", self.statusLine);
  }

  fn paintCell(
      &self,
      x: usize,
      y: usize) {

    let cell = &self.cells[x][y];

    let backgroundColorStr =
      match cell.backgroundColor {
        ScreenColor::DARKGRAY => "\x1b[40m",
        ScreenColor::BLACK => "\x1b[0m",
        _ => panic!("Unimplemented"),
      };

    let foregroundColorStr =
      match cell.foregroundColor {
        ScreenColor::RED => "\x1b[1;31m",
        ScreenColor::TURQUOISE => "\x1b[36m",
        ScreenColor::ORANGE => "\x1b[33m",
        ScreenColor::GREEN => "\x1b[32m",
        ScreenColor::LIGHTGRAY => "\x1b[37m",
        ScreenColor::WHITE => "\x1b[39m",
        _ => panic!("Unimplemented"),
      };

    let character = &cell.character;

    match stdout().execute(MoveTo(x as u16, y as u16)) {
      Ok(_) => {}
      Err(_) => { panic!("wat"); }
    }
    println!("{}{}{}\x1b[0m", backgroundColorStr, foregroundColorStr, character)
  }
}
