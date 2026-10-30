#!/usr/bin/env bash
# Every designed state -> .scratch/shots/<name>.png (then copied to the factory's apps/fold-guide/build/shots).
cd "$(dirname "$0")/.."
Q="bash tools/qa_shot.sh"
$Q "S00 hold" S00
$Q "S01" S01
$Q "S07" S07
SETTLE=3 $Q "S02 hold" S02
$Q "S03" S03
SETTLE=8 $Q "S08 masu 3" S08-masu3
SETTLE=8 $Q "S08 cup 2" S08-cup2
SETTLE=8 $Q "S08 basics 2" S08-basics2
SETTLE=8 $Q "S08 dog 5" S08-dog5
SETTLE=6 $Q "S08 masu 3" S08-ghost "ghost 0.5"
SETTLE=12 $Q "S08 dog 1" S08-finished
PRE=0.2 SETTLE=0.8 $Q "S09" S09-mid
SETTLE=8 $Q "S09" S09
SETTLE=7 $Q "S10" S10
SETTLE=5 $Q - S10-rot90 "rotate 90"
SETTLE=8 $Q "S12 cup" S12-cup
SETTLE=8 $Q "S12 masu" S12-masu
$Q "S13" S13
SETTLE=8 $Q "S08 masu 3" S08-zh "lang zh"
SETTLE=8 $Q "S08 masu 3" S08-left "hand left"
$Q "S07" S07-done "lang system" "hand right"
$Q "S14" S14
$Q "S15" S15
$Q "S17" S17
$Q "S18" S18
echo ALL DONE
