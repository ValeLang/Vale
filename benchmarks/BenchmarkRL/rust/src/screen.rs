use crossterm::{cursor::MoveTo, ExecutableCommand};
use std::io::stdout;

#[allow(dead_code)]
#[derive(PartialEq)]
pub enum ScreenColor {
    Black,
    DarkGray,
    Turquoise,
    Red,
    LightGray,
    Orange,
    Yellow,
    OrangeYellow,
    Green,
    White,
    Gray,
}

#[derive(new)]
struct ScreenCell {
    fg_color: ScreenColor,
    bg_color: ScreenColor,
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
                    ScreenColor::White,
                    ScreenColor::Black,
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
        bg_color: ScreenColor,
        fg_color: ScreenColor,
        character: String,
    ) {
        let cell = &mut self.cells[x][y];

        if bg_color != cell.bg_color {
            cell.bg_color = bg_color;
            cell.dirty = true;
        }

        if fg_color != cell.fg_color {
            cell.fg_color = fg_color;
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

        // For precise colors:
        // https://askubuntu.com/questions/558280/changing-colour-of-text-and-bg-of-terminal
        // For standard colors:
        // https://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux

        let (fg_red, fg_green, fg_blue) =
            match cell.bg_color {
                ScreenColor::DarkGray => (16, 16, 16),
                ScreenColor::Orange => (255, 96, 0),
                ScreenColor::Red => (255, 0, 0),
                ScreenColor::Black => (0, 0, 0),
                _ => panic!("Unimplemented"),
            };

        let (bg_red, bg_green, bg_blue) =
            match cell.fg_color {
                ScreenColor::Red => (255, 0, 0),
                ScreenColor::Turquoise => (0, 128, 255),
                ScreenColor::Orange => (255, 96, 0),
                ScreenColor::Green => (0, 196, 0),
                ScreenColor::Yellow => (255, 255, 0),
                ScreenColor::OrangeYellow => (255, 186, 0),
                ScreenColor::LightGray => (224, 224, 224),
                ScreenColor::Gray => (150, 150, 150),
                ScreenColor::White => (255, 255, 255),
                _ => panic!("Unimplemented"),
            };

        let character = &cell.character;

        match stdout().execute(MoveTo(x as u16, y as u16)) {
            Ok(_) => {}
            Err(_) => panic!("Couldn't move cursor!"),
        }

        println!(
            "\x1b[38;2;{};{};{};48;2;{};{};{}m{}\x1b[0m",
            bg_red, bg_green, bg_blue,
            fg_red, fg_green, fg_blue,
            character
        );
    }
}
