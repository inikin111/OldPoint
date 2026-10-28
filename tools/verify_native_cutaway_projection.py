"""Independent geometry experiment; does not import, build, patch or deploy OldPoint.

The golden masks are read from the installed game's bytecode. Only the indoor
wall-to-ground footprint and its room-mask composition are tested here, not the
game's entire cutaway state machine, renderer, fence or peeking rules.
"""
import argparse
import hashlib
import json
import math
import random
import subprocess
from pathlib import Path

HEIGHT = math.sqrt(6)
EPS = 1e-9


def area(poly):
    if len(poly) < 3:
        return 0.0
    # Translate first to avoid cancellation at large map coordinates.
    ox, oy = poly[0]
    p = [(x - ox, y - oy) for x, y in poly]
    return abs(sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(p, p[1:] + p[:1]))) / 2


def clip(poly, axis, boundary, keep_greater):
    if not poly:
        return []
    output = []
    previous = poly[-1]
    previous_inside = (previous[axis] >= boundary) if keep_greater else (previous[axis] <= boundary)
    for current in poly:
        inside = (current[axis] >= boundary) if keep_greater else (current[axis] <= boundary)
        if inside != previous_inside:
            t = (boundary - previous[axis]) / (current[axis] - previous[axis])
            output.append(tuple(previous[i] + t * (current[i] - previous[i]) for i in range(2)))
        if inside:
            output.append(current)
        previous, previous_inside = current, inside
    return output


def grid_cover(poly, epsilon=EPS):
    if area(poly) <= epsilon:
        return set()
    result = set()
    for y in range(math.floor(min(p[1] for p in poly)), math.ceil(max(p[1] for p in poly))):
        for x in range(math.floor(min(p[0] for p in poly)), math.ceil(max(p[0] for p in poly))):
            piece = poly
            for axis, bound, greater in ((0, x, True), (0, x + 1, False), (1, y, True), (1, y + 1, False)):
                piece = clip(piece, axis, bound, greater)
            if area(piece) > epsilon:
                result.add((x, y))
    return result


def endpoints(kind, x=0, y=0):
    return (x, y), (x + (kind == "north"), y + (kind == "west"))


def orthographic(kind, yaw=45, pitch=30, x=0, y=0, height=HEIGHT):
    # Project a vertical wall onto its walking-floor plane along parallel rays.
    reach = height / math.tan(math.radians(pitch))
    dx = -reach * math.cos(math.radians(yaw))
    dy = -reach * math.sin(math.radians(yaw))
    a, b = endpoints(kind, x, y)
    return [a, b, (b[0] + dx, b[1] + dy), (a[0] + dx, a[1] + dy)]


def perspective(kind, camera, x=0, y=0, height=HEIGHT, floor=0):
    cx, cy, cz = camera
    ground = floor * HEIGHT
    if cz <= ground + height:
        raise ValueError("This finite-ground-footprint experiment requires camera above wall top")
    factor = (cz - ground) / (cz - ground - height)
    a, b = endpoints(kind, x, y)
    def top(p):
        return cx + factor * (p[0] - cx), cy + factor * (p[1] - cy)
    return [a, b, top(b), top(a)]


def rotated_tile(tile, turns):
    x, y = tile[0] + 0.5, tile[1] + 0.5
    for _ in range(turns % 4):
        x, y = -y, x
    return math.floor(x), math.floor(y)


def intersect_convex(subject, boundary):
    """Independent screen-space overlap check with arbitrary clip edges."""
    orientation = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(boundary, boundary[1:] + boundary[:1]))
    if orientation < 0:
        boundary = list(reversed(boundary))
    for a, b in zip(boundary, boundary[1:] + boundary[:1]):
        if not subject:
            break
        def side(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        output = []
        previous, dp = subject[-1], side(subject[-1])
        for current in subject:
            dc = side(current)
            if (dc >= 0) != (dp >= 0):
                t = dp / (dp - dc)
                output.append(tuple(previous[i] + t * (current[i] - previous[i]) for i in range(2)))
            if dc >= 0:
                output.append(current)
            previous, dp = current, dc
        subject = output
    return subject


def screen_projector(yaw, pitch, camera=None, scale=1):
    yaw, pitch = math.radians(yaw), math.radians(pitch)
    right = (-math.sin(yaw), math.cos(yaw), 0)
    up = (-math.cos(yaw) * math.sin(pitch), -math.sin(yaw) * math.sin(pitch), math.cos(pitch))
    forward = (-math.cos(yaw) * math.cos(pitch), -math.sin(yaw) * math.cos(pitch), -math.sin(pitch))
    def project(p):
        vector = p if camera is None else tuple(p[i] - camera[i] for i in range(3))
        sx = sum(right[i] * vector[i] for i in range(3))
        sy = sum(up[i] * vector[i] for i in range(3))
        if camera is not None:
            depth = sum(forward[i] * vector[i] for i in range(3))
            assert depth > 0
            sx, sy = sx * scale / depth, sy * scale / depth
        return sx, sy
    return project


def screen_cover(kind, footprint, project):
    a, b = endpoints(kind)
    wall = [project((*a, 0)), project((*b, 0)), project((*b, HEIGHT)), project((*a, HEIGHT))]
    result = set()
    for y in range(math.floor(min(p[1] for p in footprint)) - 1, math.ceil(max(p[1] for p in footprint)) + 1):
        for x in range(math.floor(min(p[0] for p in footprint)) - 1, math.ceil(max(p[0] for p in footprint)) + 1):
            tile = [project((x, y, 0)), project((x + 1, y, 0)),
                    project((x + 1, y + 1, 0)), project((x, y + 1, 0))]
            if area(intersect_convex(tile, wall)) > EPS:
                result.add((x, y))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-jar", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, help="Export independent screen-overlap queries for production Java tests")
    args = parser.parse_args()
    reference = Path(__file__).with_name("NativeCutawayReference.java")
    process = subprocess.run(
        [str(args.jdk / "bin/java.exe"), "--class-path", str(args.game_jar), str(reference)],
        check=True, capture_output=True, text=True, encoding="utf-8",
    )
    values = json.loads(process.stdout.strip().splitlines()[-1])
    native = {kind: set(zip(raw[::2], raw[1::2])) for kind, raw in values.items()}
    checks = {}
    fixture_rows = []

    def fixture(kind, poly, covered, camera, w):
        if not args.fixtures:
            return
        for y in range(math.floor(min(p[1] for p in poly)) - 1, math.ceil(max(p[1] for p in poly)) + 1):
            for x in range(math.floor(min(p[0] for p in poly)) - 1, math.ceil(max(p[0] for p in poly)) + 1):
                row = [1 if kind == "north" else 2, 0, 0, 0, w, *camera, x, y, x + 1, y + 1,
                       int((x, y) in covered)]
                fixture_rows.append("\t".join(str(value) for value in row))
    for kind in ("north", "west"):
        actual = grid_cover(orthographic(kind))
        assert actual == native[kind], (kind, sorted(actual), sorted(native[kind]))
        checks[kind + "_native_exact"] = {"cells": sorted(actual), "count": len(actual)}
    print("PASS original bytecode masks == orthographic swept-wall footprint (north and west)")

    rng = random.Random(20261027)
    translated = 0
    for _ in range(200):
        x, y = rng.randint(-100000, 100000), rng.randint(-100000, 100000)
        for kind in native:
            expected = {(x + a, y + b) for a, b in native[kind]}
            assert grid_cover(orthographic(kind, x=x, y=y)) == expected
            translated += 1
    checks["integer_translation_cases"] = translated
    print(f"PASS {translated} translated native-mask cases")

    # Compare the actual masks' room/visibility/object gates with the geometry
    # substitution. This is a reduced composition fixture, not the game engine.
    mask_cases = 1000
    for _ in range(mask_cases):
        kind = rng.choice(list(native))
        length = rng.randint(1, 8)
        reference_sets, geometry_sets = [], []
        for i in range(length):
            x, y = (i, 0) if kind == "north" else (0, i)
            reference_sets.append({(x + a, y + b) for a, b in native[kind]})
            geometry_sets.append(grid_cover(orthographic(kind, x=x, y=y)))
        coordinates = set().union(*reference_sets, *geometry_sets)
        scene = {p: (rng.randint(-1, 3), bool(rng.getrandbits(1)), rng.randint(0, 3)) for p in coordinates}
        selected_rooms = {r for r in range(4) if rng.getrandbits(1)}
        def mask(sets):
            result = 0
            for i, covered in enumerate(sets):
                if any(scene[p][0] in selected_rooms and scene[p][1] and scene[p][2] > 0 for p in covered):
                    result |= 1 << i
            return result
        assert mask(reference_sets) == mask(geometry_sets)
    checks["reduced_room_mask_cases"] = mask_cases
    print(f"PASS {mask_cases} reduced room/visibility/object mask fixtures")

    angle_cases = 0
    angle_samples = []
    for pitch in (15, 30, 45, 60, 75):
        for yaw in range(360):
            for kind in native:
                poly = orthographic(kind, yaw=yaw, pitch=pitch)
                assert all(math.isfinite(value) for point in poly for value in point)
                covered = grid_cover(poly)
                if pitch == 30 and yaw % 45 == 0:
                    angle_samples.append({"yaw": yaw, "pitch": pitch, "wall": kind, "tiles": len(covered)})
                angle_cases += 1
    checks["angle_cases"] = angle_cases
    checks["angle_samples"] = angle_samples
    print(f"PASS {angle_cases} yaw/pitch footprint evaluations")

    # A quarter turn changes wall direction and grid origin; rotating just the
    # six offsets around a fixed origin would be the wrong covariance test.
    rotation_cases = 0
    for yaw in range(0, 360, 3):
        original = grid_cover(orthographic("north", yaw=yaw))
        rotated = grid_cover(orthographic("west", yaw=yaw + 90))
        assert rotated == {rotated_tile(p, 1) for p in original}, yaw
        rotation_cases += 1
    checks["quarter_turn_covariance_cases"] = rotation_cases
    print(f"PASS {rotation_cases} quarter-turn wall/grid covariance cases")

    projection_cases = 0
    for _ in range(100):
        yaw, pitch = rng.uniform(0, 360), rng.uniform(20, 75)
        for kind in native:
            poly = orthographic(kind, yaw=yaw, pitch=pitch)
            covered = screen_cover(kind, poly, screen_projector(yaw, pitch))
            assert grid_cover(poly) == covered
            fixture(kind, poly, covered,
                    (math.cos(math.radians(yaw)) * math.cos(math.radians(pitch)),
                     math.sin(math.radians(yaw)) * math.cos(math.radians(pitch)), math.sin(math.radians(pitch))), 0)
            projection_cases += 1
            a, b = endpoints(kind)
            distance = 40
            camera = ((a[0] + b[0]) / 2 + distance * math.cos(math.radians(yaw)),
                      (a[1] + b[1]) / 2 + distance * math.sin(math.radians(yaw)),
                      distance * math.tan(math.radians(pitch)))
            poly = perspective(kind, camera)
            covered = screen_cover(kind, poly, screen_projector(yaw, pitch, camera, distance))
            assert grid_cover(poly) == covered
            fixture(kind, poly, covered, camera, 1)
            projection_cases += 1
    checks["independent_screen_overlap_cases"] = projection_cases
    print(f"PASS {projection_cases} ground-footprint vs independent screen-polygon overlap cases")

    perspective_samples = []
    for distance in (10, 20, 40, 80, 160, 10000):
        for kind in native:
            a, b = endpoints(kind)
            camera = ((a[0] + b[0]) / 2 + distance / math.sqrt(2),
                      (a[1] + b[1]) / 2 + distance / math.sqrt(2),
                      distance * math.tan(math.pi / 6))
            poly = perspective(kind, camera)
            covered = grid_cover(poly)
            corner_error = max(math.dist(a, b) for a, b in zip(poly, orthographic(kind)))
            perspective_samples.append({"distance": distance, "wall": kind, "tiles": len(covered),
                                        "native_equal": covered == native[kind], "corner_error": round(corner_error, 8)})
    assert any(not row["native_equal"] for row in perspective_samples)
    north_errors = [r["corner_error"] for r in perspective_samples if r["wall"] == "north"]
    assert all(a > b for a, b in zip(north_errors, north_errors[1:]))
    checks["perspective_samples"] = perspective_samples
    print("PASS finite perspective differs from native; footprint converges geometrically at long distance")
    perturbation = []
    for delta in (-0.01, -0.0001, 0, 0.0001, 0.01):
        covered = grid_cover(orthographic("north", yaw=45 + delta))
        perturbation.append({"yaw_delta": delta, "tiles": len(covered),
                             "changed_tiles": len(covered ^ native["north"])})
    checks["native_angle_perturbation"] = perturbation
    checks["numeric_area_epsilon"] = EPS
    checks["boundary_tolerance_samples"] = [
        {"yaw_delta": delta, "area_epsilon": epsilon,
         "tiles": len(grid_cover(orthographic("north", yaw=45 + delta), epsilon))}
        for delta in (-0.01, -0.0001, 0, 0.0001, 0.01)
        for epsilon in (1e-6, 1e-9, 1e-12)
    ]

    # A plane at the camera height has no finite projected footprint. Make this
    # domain restriction explicit rather than claiming all cameras are solved.
    try:
        perspective("north", (3, 3, HEIGHT))
        raise AssertionError("horizon case must be rejected")
    except ValueError:
        pass
    checks["wall_top_horizon_rejected"] = True
    checks["game_jar_sha256"] = hashlib.sha256(args.game_jar.read_bytes()).hexdigest()
    checks["scope"] = "Wall-to-walking-floor projection only; not full native state-machine or renderer equivalence"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(checks, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.fixtures:
        args.fixtures.parent.mkdir(parents=True, exist_ok=True)
        args.fixtures.write_text("\n".join(fixture_rows) + "\n", encoding="utf-8")
        print("Exported", len(fixture_rows), "independent screen-overlap queries")
    print("Saved", args.output)


if __name__ == "__main__":
    main()
