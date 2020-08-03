use crossterm::{cursor::MoveTo, ExecutableCommand};
use std::io::stdout;

#[derive(PartialEq)]
pub enum ScreenColor {
    BLACK,
    DARKGRAY,
    TURQUOISE,
    RED,
    LIGHTGRAY,
    ORANGE,
    GREEN,
    WHITE,
}

#[derive(new)]
struct ScreenCell {
    foreground_color: ScreenColor,
    background_color: ScreenColor,
    character: String,
    dirty: bool,
}

pub struct Screen {
    width: usize,
    height: usize,
    cells: Vec<Vec<ScreenCell>>,
    status_line: String,
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

        return Screen {
            width: width,
            height: height,
            cells: cells,
            status_line: "".to_string(),
        };
    }

    pub fn set_status_line(&mut self, new_line: String) {
        self.status_line = new_line;
    }

    pub fn set_cell(
        &mut self,
        x: usize,
        y: usize,
        background_color: ScreenColor,
        foreground_color: ScreenColor,
        character: String,
    ) {
        let cell = &mut self.cells[x][y];

        if background_color != cell.background_color {
            cell.background_color = background_color;
            cell.dirty = true;
        }

        if foreground_color != cell.foreground_color {
            cell.foreground_color = foreground_color;
            cell.dirty = true;
        }

        if character != cell.character {
            cell.character = character;
            cell.dirty = true;
        }
    }

    pub fn paint_screen(&self) {
        for x in 0..self.width {
            for y in 0..self.height {
                if self.cells[x][y].dirty {
                    self.paint_cell(x, y);
                }
            }
        }
        match stdout().execute(MoveTo(0, self.height as u16)) {
            Ok(_) => {}
            Err(_) => panic!("Couldn't move cursor!"),
        }
        println!("{}", self.status_line);
    }

    fn paint_cell(&self, x: usize, y: usize) {
        let cell = &self.cells[x][y];

        // https://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux

        let background_color_str = match cell.background_color {
            ScreenColor::DARKGRAY => "\x1b[40m",
            ScreenColor::ORANGE => "\x1b[43m",
            ScreenColor::RED => "\x1b[41m",
            ScreenColor::BLACK => "\x1b[0m",
            _ => panic!("Unimplemented"),
        };

        let foreground_color_str = match cell.foreground_color {
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
            Err(_) => panic!("Couldn't move cursor!"),
        }
        println!(
            "{}{}{}\x1b[0m",
            background_color_str, foreground_color_str, character
        )
    }
}
