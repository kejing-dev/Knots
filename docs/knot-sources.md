# Knot index and sources (v0.1.3)

Every knot's path is drawn by the app from control points; none is copied from a third-party animation. Tying methods
were checked against public references. ABOK = Clifford W. Ashley, *The Ashley Book of Knots* (1944); numbers are given
only where the entry is unambiguous, otherwise "—".

| id | 中文 | English | ABOK | Reference for the tying method | How the path is built |
|---|---|---|---|---|---|
| figure8 | 8 字结 | Figure-Eight Knot | #570 | Animated Knots "Figure 8 Knot" | design station control points (v0.1) |
| reef | 平结 | Square / Reef Knot | #1204 | Animated Knots "Square Knot" | design station (v0.1) |
| clove | 丁香结 | Clove Hitch | #1245 | Animated Knots "Clove Hitch (tied with the end)" | design station (v0.1) |
| bowline | 单套结 | Bowline | #1010 | Animated Knots "Bowline" | design station (v0.1) |
| overhand | 单结 | Overhand Knot | #514 | Animated Knots "Overhand Knot" | tools/knotlab.py: an open trefoil (3 alternating crossings) |
| doubleoverhand | 双重单结 | Double Overhand Knot | — | Animated Knots "Double Overhand Stopper" | knotlab: a loop with the end passed through it twice (v0.1.3 fix: the passes now wrap with the loop, see below) |
| halfhitches | 双半结 | Two Half Hitches | — | Animated Knots "Two Half Hitches" | knotlab: around the post, then a clove hitch on the standing part (two half hitches = a clove hitch around the standing part) |
| roundturn | 圈绕双半结 | Round Turn & Two Half Hitches | #1720 | Animated Knots "Round Turn and Two Half Hitches" | knotlab: a round turn on the post, then as above |
| overhandloop | 单结环 | Overhand Follow-Through Loop | — | Animated Knots "Overhand Loop (follow-through)" | knotlab: the overhand, the tail round the object, retraced 1.05 d beside its own path |
| fig8follow | 8 字绕圈结 | Figure-Eight Follow-Through | #1047 (figure-eight loop) | Animated Knots "Figure 8 Follow Through" | knotlab: the app's figure eight drawn 1.85× larger, round the harness, retraced beside it |
| tautline | 营钉结 | Taut-Line Hitch | #1734 (rolling hitch, the version taught in Scouting) | Animated Knots "Rolling Hitch" (version 1) and "Midshipman's Hitch" (lists "taut line hitch" as another name; ABOK #1855) | knotlab: round the stake, then a rolling hitch on the standing part = the half-hitches path with one more turn, in the same direction, on the stake side (inside the loop) |
| sheetbend | 接绳结 | Sheet Bend | #1431 (weaver's knot #485) | Animated Knots "Sheet Bend": bight in the thicker rope, the other through it, behind the bight's tail then its standing part, tucked under itself, both tails on the same side | knotlab: the app's bowline with its eye cut (a sheet bend is a bowline whose loop is cut), drawn mirrored front-to-back so the red rope goes up through the bight from behind |
| fishermans | 双渔人结 | Double Fisherman's Bend | Animated Knots cites #294 | Animated Knots "Double Fisherman's Bend": each end two full turns round both ropes, back through the turns; the two knots pulled together | knotlab: two strangle knots (2¾ turns round both ropes back towards the rope's own side, the end back through the turns), the red one the blue one turned 180° |

## Checks

- `tools/knotlab.py check <id>`: gaps and crossing order at 4 / 8 / 11 mm.
- `tools/knottopo.py <id>`: the knot type of each rope (its ends joined above everything), as the knot determinant
  |Δ(−1)| from three slightly turned views: overhand 3, figure eight 5, double overhand 5 (5₁), fisherman's 5 in each rope.
- `KnotEngineTest` (11 tests): gaps ≥ 0.9 d at every step end and while tightening, length kept, clove clear of the post,
  square knot not a granny, both ropes of a bend drawn and ≥ 0.9 d apart, and the knot types above.

Sheet bend, checked crossing by crossing along the red rope (front view): under the blue bight's bend (through the bight),
over the blue tail, under the blue tail, under the blue standing part (behind both legs, tail first), over the standing
part, under its own standing part (tucked under itself), out on the same side as the blue tail. That is the order of the
Animated Knots steps, seen from the other side.

## v0.1.3 fix: the double overhand

The knot-type check found that v0.1.2's double overhand closed to a plain overhand (determinant 3): its two passes wrapped
against the loop, so the second undid the first. The passes now wrap the other way (determinant 5, a real double
overhand). The first fisherman's draft had the same fault (1¾ turns): it now makes 2¾.

## Left out (v0.1.3)

- Trucker's hitch (卡车司机结): it starts with a loop made in the middle of the rope (a slipped overhand or other fixed
  loop in the standing part). The app shows every knot as the rope's end moving step by step, so it cannot show that loop
  being made; the end would appear to pass through a loop that was never tied. Needs a new kind of step (shaping the
  middle of the rope) before it can be shown honestly.
- Prusik (普鲁士结): it is tied with a closed loop of cord, both strands wrapped round the rope together, three times.
  There is no single end to follow, so the step-by-step "end moves" demo cannot show it correctly.
