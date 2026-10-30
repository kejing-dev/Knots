"""Generates app/src/main/java/io/embodit/knots/domain/knot/KnotLibraryExtra.kt (the v0.1.2 knots) from tools/knotlab.py.

    python -I tools/gen_extra.py

Paths are built in knotlab.py (exact constructions: a trefoil, a clove hitch on the rope's own standing part, a knot
retraced alongside itself) and checked there and by KnotEngineTest. Sources: docs/knot-sources.md.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "knotlab.py"), encoding="utf-8").read().replace('if __name__ == "__main__":\n    main()', "")
ns = {"__name__": "knotlab"}
exec(compile(src, "knotlab.py", "exec"), ns)
SHAPES = ns["SHAPES"]

META = {
    "overhand": dict(
        zh="单结", en="Overhand Knot", aliasZh="反手结 · 止索结", aliasEn="Thumb knot",
        cats=["BASIC", "CAMPING"], diff=1,
        useZh="最简单的止索结：防止绳头从孔里滑出，也是很多绳结的第一步。", useEn="The simplest stopper: keeps an end from slipping through a hole; the first move of many knots.",
        cautionZh="受力后很难解开；需要好解的止索结用 8 字结。", cautionEn="Jams under load; use a figure eight when it must untie easily.",
        checkZh="看得出一个三瓣的结，尾巴和固定端从结的两头出来。", checkEn="A small three-lobed knot, the two ends leaving from opposite sides.",
        steps=[("工作端压过固定端，做一个圈", "Cross the end over the standing part to make a loop", "lay"),
               ("绳头从圈里穿过来", "Pass the end through the loop", "lay"),
               ("两头拉紧", "Pull both ends tight", "tighten")],
    ),
    "doubleoverhand": dict(
        zh="双重单结", en="Double Overhand Knot", aliasZh="防脱结 · 双反手结", aliasEn="Double overhand stopper",
        cats=["BASIC", "CLIMBING"], diff=2,
        useZh="比单结更大更牢的止索结；攀登里常用来给 8 字结的尾巴打保险。", useEn="A bulkier, more secure stopper; climbers tie it in the tail as a backup.",
        cautionZh="攀登保险结要在专业指导下使用。", cautionEn="Use climbing backups only with proper training.",
        checkZh="绳头在圈里绕了两圈，拉紧后是一个圆鼓鼓的结。", checkEn="The end wraps twice inside the loop; tightened it forms a round barrel.",
        steps=[("工作端压过固定端，做一个圈", "Cross the end over the standing part to make a loop", "lay"),
               ("绳头从圈里穿过去", "Pass the end through the loop", "lay"),
               ("再穿一次，一共绕两圈", "Pass it through once more: two wraps", "lay"),
               ("两头拉紧，理顺两圈", "Pull both ends and dress the wraps", "tighten")],
    ),
    "halfhitches": dict(
        zh="双半结", en="Two Half Hitches", aliasZh="两个半结", aliasEn="Two half hitches",
        cats=["CAMPING", "BOATING"], diff=1,
        useZh="把绳子系在柱子、树或环上：挂吊床、拴船、拉天幕，好打好解。", useEn="Ties a line to a post, tree or ring: hammocks, mooring, tarps; quick to tie and untie.",
        cautionZh="受力方向一直不变时最好；会晃动的用圈绕双半结。", cautionEn="Best under steady load; for a moving load use a round turn first.",
        checkZh="固定端上并排两个一样的半结，推到柱子边。", checkEn="Two matching hitches side by side on the standing part, pushed up to the post.",
        steps=[("工作端绕过柱子", "Take the end around the post", "lay"),
               ("绕固定端一圈，从圈里穿出：第一个半结", "Around the standing part and through: the first hitch", "lay"),
               ("同方向再绕一圈，穿出：第二个半结", "Again in the same direction: the second hitch", "lay"),
               ("拉紧，把两个半结推向柱子", "Pull tight and slide the hitches to the post", "tighten")],
    ),
    "roundturn": dict(
        zh="圈绕双半结", en="Round Turn & Two Half Hitches", aliasZh="绕柱双半结", aliasEn="Round turn and two half hitches",
        cats=["BOATING", "CAMPING"], diff=2,
        useZh="系船、拴重物最常用：先在柱子上绕一整圈分担拉力，受力时也能打和解。", useEn="The go-to for mooring and heavy loads: the round turn takes the strain, so it can be tied and untied under load.",
        cautionZh="绕柱子的那一整圈不能少，否则就只是双半结。", cautionEn="Don't skip the full turn around the post; without it this is just two half hitches.",
        checkZh="柱子上绕了一整圈，固定端上并排两个半结。", checkEn="A full turn around the post and two hitches side by side on the standing part.",
        steps=[("工作端绕柱子一整圈再半圈", "Take the end around the post one and a half times", "lay"),
               ("绕固定端一圈，从圈里穿出：第一个半结", "Around the standing part and through: the first hitch", "lay"),
               ("同方向再绕一圈，穿出：第二个半结", "Again in the same direction: the second hitch", "lay"),
               ("拉紧，把两个半结推向柱子", "Pull tight and slide the hitches to the post", "tighten")],
    ),
    "overhandloop": dict(
        zh="单结环", en="Overhand Follow-Through Loop", aliasZh="反穿单结环", aliasEn="Overhand loop",
        cats=["BASIC", "CAMPING"], diff=2,
        useZh="在绳头做一个固定的圈，套在环、栏杆或背包带上；简单好记。", useEn="A fixed loop in the end, threaded through a ring, rail or strap; simple to remember.",
        cautionZh="受力后很难解开；不用于攀登。", cautionEn="Hard to untie after loading; not for climbing.",
        checkZh="结里每一股都成对并排，圈的大小拉不动。", checkEn="Every strand in the knot runs in a pair; the loop keeps its size.",
        steps=[("在绳上打一个单结，尾巴留长", "Tie an overhand knot, leaving a long tail", "lay"),
               ("尾巴绕过要系的东西，做个圈", "Take the tail around the object to make a loop", "lay"),
               ("沿着原来的单结反穿回去", "Follow the overhand back along its path", "lay"),
               ("拉紧，检查每一股都成对", "Pull tight; check every strand is paired", "tighten")],
    ),
    "fig8follow": dict(
        zh="8 字绕圈结", en="Figure-Eight Follow-Through", aliasZh="反穿 8 字结 · 攀登系结", aliasEn="Rewoven figure eight",
        cats=["CLIMBING"], diff=3,
        useZh="攀登时把绳子系到安全带上的标准结；也用来做牢靠的固定圈。", useEn="The standard climbing tie-in to a harness; also a dependable fixed loop.",
        cautionZh="仅供学习。真正攀登前必须由合格教练当面检查；尾巴至少留 10 cm。", cautionEn="For learning only. Have a qualified instructor check it before climbing; leave a 10 cm tail.",
        checkZh="一个完整的 8 字，每一股都成对并排、没有交叉错位，尾巴留够。", checkEn="A clean eight with every strand paired and parallel, and a long enough tail.",
        steps=[("在绳上打一个 8 字结，尾巴留长约一臂", "Tie a figure eight, leaving an arm's length of tail", "lay"),
               ("尾巴穿过安全带，或绕过固定点", "Pass the tail through the harness or around the anchor", "lay"),
               ("沿着原来的 8 字反穿回去", "Follow the eight back along its path", "lay"),
               ("拉紧，检查每一股都成对，尾巴留 10 cm", "Pull tight; check each strand is paired and leave 10 cm of tail", "tighten")],
    ),
    "tautline": dict(
        zh="营钉结", en="Taut-Line Hitch", aliasZh="帐篷结 · 拉线结", aliasEn="Tautline hitch",
        cats=["CAMPING", "BOATING"], diff=2,
        useZh="拉帐篷风绳、晾衣绳：结能顺着绳滑动来调松紧，受力时卡住不动。", useEn="Guylines and clotheslines: slide the knot to adjust the tension; under load it holds.",
        cautionZh="那两圈要绕在靠木桩的一侧（圈里面），绕反了一拉就滑。", cautionEn="The two turns go on the stake side, inside the loop; the other way round it slips.",
        checkZh="靠木桩一侧并排两圈，外侧一个半结；推结能滑，拉绳不动。", checkEn="Two turns on the stake side, one hitch outside them; the knot slides when pushed and grips when loaded.",
        steps=[("工作端绕过木桩（或帐篷环）", "Take the end around the stake (or tent ring)", "lay"),
               ("在圈里绕固定端两圈，朝木桩方向绕", "Inside the loop, wrap twice round the standing part, towards the stake", "lay"),
               ("斜着压过这两圈，在外侧再绕一圈穿出", "Cross over both turns and make one more hitch outside them", "lay"),
               ("拉紧；推动结可以调松紧", "Pull tight; slide the knot to set the tension", "tighten")],
    ),
    "sheetbend": dict(
        zh="接绳结", en="Sheet Bend", aliasZh="织布结 · 单编结", aliasEn="Weaver's knot",
        cats=["BASIC", "CAMPING", "BOATING"], diff=2,
        useZh="把两根绳接在一起，粗细不同也能接：接长晾衣绳、补绳网、接帐篷拉绳。粗的那根做绳耳。", useEn="Joins two ropes, even of different sizes: lengthen a line, mend a net. The thicker rope makes the bight.",
        cautionZh="两个尾巴要在同一侧；不受力时会松，重要场合用双接绳结。", cautionEn="Both tails on the same side; it can loosen when slack, so use a double sheet bend when it matters.",
        checkZh="蓝绳是一个绳耳，红绳在绳耳脖子上绕一圈、压住自己；两个尾巴在同一侧。", checkEn="Blue forms a bight; red goes once round its neck and under itself; both tails on the same side.",
        steps=[("红绳从上往下，从蓝色绳耳后面穿过去", "Bring the red end down through the blue bight, behind its bend", "lay"),
               ("绕到蓝绳两股后面：先尾巴，再固定端", "Take it behind both blue legs: the tail, then the standing part", "lay"),
               ("绕回前面，从红绳自己下面塞过去", "Come round to the front and tuck it under itself", "lay"),
               ("捏住蓝色绳耳，拉红绳固定端，拉紧", "Hold the blue bight and pull the red standing part", "tighten")],
    ),
    "fishermans": dict(
        zh="双渔人结", en="Double Fisherman's Bend", aliasZh="双渔夫结 · 葡萄藤结", aliasEn="Grapevine bend",
        cats=["CLIMBING", "CAMPING"], diff=3,
        useZh="牢靠地连接两根绳，也用来把一段绳子接成绳圈；受力后很难解开。", useEn="A secure join of two ropes, also used to close a loop of cord; very hard to untie after loading.",
        cautionZh="仅供学习。攀登等保护用途必须由合格教练当面检查；尾巴至少留绳径的 10 倍。", cautionEn="For learning only. Have a qualified instructor check it before any safety use; leave tails of 10× the diameter.",
        checkZh="两个结各绕两圈、紧紧挨在一起，两个尾巴各朝外。", checkEn="Two knots of two turns each, snug together, tails pointing away from each other.",
        steps=[("蓝绳头绕两根绳两圈，往自己那边绕", "Wrap the blue end twice round both ropes, back towards its own side", "lay"),
               ("从两圈里面穿回来", "Tuck it back through the two turns", "lay"),
               ("红绳头同样绕两圈，往自己那边绕", "Do the same with the red end: two turns round both ropes", "lay"),
               ("也从两圈里面穿回来", "Tuck it back through its turns", "lay"),
               ("拉两根绳，让两个结靠在一起", "Pull the ropes apart so the two knots meet", "tighten")],
    ),
}


def step_ends(name, k):
    """Control indices where the lay steps end (the last lay step ends at the last point)."""
    last = len(k.pts) - 1
    if name == "overhand":
        return [4 + 16, last]
    if name == "doubleoverhand":
        loop_end, helix_end = k.ends
        return [loop_end, loop_end + 1 + 11, last]
    if name in ("halfhitches", "roundturn"):
        return [k.ends[0], k.ends[1], last]
    if name in ("tautline", "sheetbend", "fishermans"):
        return list(k.ends)
    return [k.ends[0], k.ends[1], last]


def tighten_ranges(name, k):
    n = len(k.pts)
    if name in ("halfhitches", "roundturn"):
        return [(k.ends[0] + 5, n - 4)], -100.0, 0.0, 0.85, 1.0
    if name in ("overhandloop", "fig8follow"):
        a_end, loop_end = k.ends
        return [(4, a_end - 2), (loop_end + 2, n - 2)], 0.0, 0.0, 0.88, 0.88
    if name == "doubleoverhand":
        return [(5, n - 4)], 0.0, 0.0, 0.85, 0.85
    if name == "tautline":
        return [(k.ends[0] + 5, n - 4)], -110.0, 0.0, 0.85, 1.0
    if name == "sheetbend":
        return [(2, k.split - 2), (k.split + 4, n - 3)], 0.0, -9.0, 0.8, 0.8
    if name == "fishermans":
        return [(k.start - 1, k.split - 3), (k.shown2 - 1, n - 3)], 0.0, 30.0, 0.75, 1.0
    return [(4, n - 6)], 0.0, 5.0, 0.8, 0.8


def kt(v):
    return ("%.3f" % v).rstrip("0").rstrip(".").replace("-0", "-0") + "f" if v != int(v) else "%d" % int(v)


def fl(v):
    s = "%.3f" % v
    s = s.rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "f"


def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


LAMBDA = {"tautline": 0.8, "sheetbend": 0.6, "fishermans": 0.8, "overhand": 0.8, "doubleoverhand": 0.6, "halfhitches": 0.8, "roundturn": 0.8, "overhandloop": 0.2, "fig8follow": 0.6}
PULLS = {
    "tautline": [(-200, 0, -1, 0), (-40, 40, 1, 0)],
    "sheetbend": [(0, 150, 0, 1), (42, -200, 0, -1)],
    "fishermans": [(-160, 30, -1, 0), (160, 30, 1, 0)],
    "overhand": [(0, 110, 0, 1), (55, -110, 0, -1)],
    "doubleoverhand": [(4, 110, 0, 1), (30, -110, 0, -1)],
    "halfhitches": [(-200, 0, -1, 0), (-40, 40, 1, 0)],
    "roundturn": [(-200, 0, -1, 0), (-40, 40, 1, 0)],
    "overhandloop": [(0, 120, 0, 1), (10, -330, 0, -1)],
    "fig8follow": [(0, 120, 0, 1), (10, -390, 0, -1)],
}


def main():
    out = []
    out.append("// GENERATED by tools/gen_extra.py from tools/knotlab.py; do not edit. Sources: docs/knot-sources.md.")
    out.append("package io.embodit.knots.domain.knot\n")
    out.append("/** The v0.1.2 / v0.1.3 knots: paths built in tools/knotlab.py (checked there and by KnotEngineTest). */")
    out.append("internal object KnotLibraryExtra {")
    out.append("    private fun Z(x: Float, y: Float, z: Float, dx: Float, dy: Float, zd: Float) = Ctl.Free(x, y, z, dy, dx, zd)\n")
    names = []
    for name, m in META.items():
        k = SHAPES[name]()
        ends = step_ends(name, k)
        ranges, cx, cy, kk, ky = tighten_ranges(name, k)
        pts = ", ".join("Z(%s, %s, %s, %s, %s, %s)" % tuple(fl(v) for v in p) for p in k.pts)
        steps = []
        ei = 0
        for (zh, en, typ) in m["steps"]:
            if typ == "lay":
                steps.append('KnotStep("%s", "%s", "", StepType.LAY, %d)' % (esc(zh), esc(en), ends[ei]))
                ei += 1
            else:
                steps.append('KnotStep("%s", "%s", "", StepType.TIGHTEN)' % (esc(zh), esc(en)))
        assert ei == len(ends), (name, ei, ends)
        lam = LAMBDA[name]
        rng = ", ".join("%d..%d" % r for r in ranges)
        pulls = ", ".join("PullArrow(%s, %s, %s, %s)" % tuple(fl(v) for v in p) for p in PULLS[name])
        cats = ", ".join("Category." + c for c in m["cats"])
        out.append("    private val %sPts: List<Ctl> = listOf(%s)\n" % (name, pts))
        out.append('''    private val %s =
        Knot(
            id = "%s",
            zh = "%s",
            en = "%s",
            aliasZh = "%s",
            aliasEn = "%s",
            categories = setOf(%s),
            difficulty = %d,
            useZh = "%s",
            useEn = "%s",
            cautionZh = "%s",
            cautionEn = "%s",
            checkZh = "%s",
            checkEn = "%s",
            pts = %sPts,
            start = %d,
            steps = listOf(%s),
            tighten = Tighten(listOf(%s), %s, %s, %s, %s, mapOf(4 to %s, 8 to %s, 11 to %s)),
            pulls = listOf(%s),
            post = %s,
            anchors = %d,
            rope2 = %d,
            rope2Shown = %d,
        )
''' % (name, name, esc(m["zh"]), esc(m["en"]), esc(m["aliasZh"]), esc(m["aliasEn"]), cats, m["diff"], esc(m["useZh"]), esc(m["useEn"]),
       esc(m["cautionZh"]), esc(m["cautionEn"]), esc(m["checkZh"]), esc(m["checkEn"]), name, k.start, ", ".join(steps), rng,
       fl(cx), fl(cy), fl(kk), fl(ky), fl(lam), fl(lam), fl(lam), pulls, "true" if k.post else "false", k.anchors, k.split, k.shown2))
        names.append(name)
    out.append("    val knots: List<Knot> = listOf(%s)" % ", ".join(names))
    out.append("}")
    dst = os.path.join(HERE, "..", "app", "src", "main", "java", "io", "embodit", "knots", "domain", "knot", "KnotLibraryExtra.kt")
    open(dst, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print("written", dst)


if __name__ == "__main__":
    main()
