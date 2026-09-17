
pub fn get_rune_types_from_pattern<'s>(
    pattern: &'s AtomSP<'s>,
) -> Vec<(IRuneS<'s>, ITemplataType<'s>)> {
    let mut runes_from_destructures: Vec<(
        IRuneS<'s>,
        ITemplataType<'s>,
    )> = Vec::new();
    if let Some(destructure) = pattern.destructure {
        for sub_pattern in destructure {
            runes_from_destructures.extend(get_rune_types_from_pattern(sub_pattern));
        }
    }
    if let Some(coord_rune) = pattern.coord_rune {
        runes_from_destructures.push((
            coord_rune.rune,
            ITemplataType::CoordTemplataType(
                CoordTemplataType {},
            ),
        ));
    }
    let mut result: Vec<(
        IRuneS<'s>,
        ITemplataType<'s>,
    )> = Vec::new();
    for item in runes_from_destructures {
        if !result.contains(&item) {
            result.push(item);
        }
    }
    result
}

use crate::postparsing::patterns::patterns::AtomSP;
use crate::postparsing::names::IRuneS;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::postparsing::itemplatatype::CoordTemplataType;