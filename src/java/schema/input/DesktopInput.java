package schema.input;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.ConstructBlock.*;
import schema.ui.polygon.Polygon;

import static arc.Core.*;
import static mindustry.Vars.*;
import static schema.Main.*;

import java.util.*;

/** Handles keyboard input via keybinds. */
public class DesktopInput extends InputSystem {

    /** Amount of scrolls in one direction and the direction itself. */
    private int scrolls, dir;
    /** Amount of scrolls after which the zoom speed increases by one tile per scroll. */
    private int aspect = 2;

    /** Block that is being rotated. */
    private Rotatable toRotate;
    /** Whether a block is being rotated. */
    private boolean rotating;

    /** Build plan used to draw the selected block. */
    private BuildPlan temp = new BuildPlan() {{ animScale = 1f; }};

    @Override
    protected void update() {
        if (player.isBuilder())
            player.unit().updateBuilding(building);

        if (scene.hasField() || scene.hasDialog()) {
            updateAI();
            return;
        }

        if (scene.getKeyboardFocus() instanceof Polygon p) {
            if (Keybind.select.tap()) p.select();
            if (Keybind.deselect.tap()) p.hide();

            updateAI();
            return;
        }

        updateMovement();
        updateZoom();
        updateCommand();
        updateView();

        // dead players do not build
        if (!player.dead()) updateBuilding();
    }

    protected void updateAI() {
        if (state.isPaused()) return;

        var unit = player.unit();
        var type = unit.type;

        // TODO implement miner and builder AI

        var rect = camera.bounds(Tmp.r1).grow(-64f);
        if (rect.contains(unit.x, unit.y))
            unit.wobble();
        else {
            Tmp.v4.set(
                unit.x < rect.x ? rect.x : unit.x < rect.x + rect.width  ? unit.x : rect.x + rect.width,
                unit.y < rect.y ? rect.y : unit.y < rect.y + rect.height ? unit.y : rect.y + rect.height).sub(unit);

            // length of the breaking distance
            var len = unit.vel.len2() / 2f / type.accel;
            // distance from the unit to the edge of the screen
            var dst = Math.max(0f, Tmp.v4.len() - len);

            // TODO implement path finder that is not gonna kill the unit while moving across enemy turrets
            unit.movePref(Tmp.v4.limit(dst).limit(type.speed));
        }
    }

    protected void updateMovement() { // TODO null unit is going to be removed
        var unit = player.unit();
        var type = unit.type;

        Vec2 mov = Tmp.v1.set(Keybind.move_x.axis(), Keybind.move_y.axis()).nor();
        Vec2 pan = Keybind.pan_mv.down()
            ? Tmp.v2.set(input.mouse()).sub(graphics.getWidth() / 2f, graphics.getHeight() / 2f).scl(.004f).limit2(1f)
            : Tmp.v2.setZero();
        Vec2 flw = Keybind.mouse_mv.down()
            ? Tmp.v3.set(input.mouseWorld()).sub(player).scl(.016f).limit2(1f)
            : Tmp.v3.setZero();

        if (units.coreUnit || player.dead()) {
            // this type of movement is active most of the time
            // the unit simply follows the camera and performs the commands of the player

            moveCam(mov.add(pan).limit2(1f).scl(settings.getInt("schema-pan-speed", 6) * (Keybind.boost.down() ? 2.4f : 1f) * Time.delta));
            unit.movePref(flw.scl(type.speed));

            if (Keybind.mouse_mv.down())
                unit.updateBuilding(false);
            else
                updateAI();
        } else {
            // this type of movement is activate only when the player controls a combat unit
            // inherently, this is the classical movement

            lerpCam(pan.scl(64f * tilesize).add(player));
            unit.movePref(mov.add(flw).limit2(1f).scl(unit.speed()));
        }

        if (Keybind.teleport.tap()) unit.set(input.mouseWorld());

        if (state.isPlaying()) {
            if (Keybind.look_at.down())
                unit.rotation = Angles.mouseAngle(unit.x, unit.y);
            else {
                if (player.shooting && type.omniMovement && type.faceTarget && type.hasWeapons())
                    unit.lookAt(input.mouseWorld());
                else
                    unit.lookAt(unit.prefRotation());
            }

            unit.aim(input.mouseWorld());
            unit.controlWeapons(true, player.shooting);
        }

        if (Keybind.respawn.tap()) Call.unitClear(player);
        if (Keybind.despawn.tap()); // TODO admins/hacky functions

        if (unit instanceof Payloadc pay && state.isPlaying()) {
            if (Keybind.pick_cargo.tap()) {

                var target = Units.closest(unit.team, unit.x, unit.y, u -> u.isGrounded() && u.within(unit, (u.hitSize + type.hitSize) * 2f) && pay.canPickup(u));
                if (target != null) Call.requestUnitPayload(player, target);

                else {
                    var build = world.buildWorld(unit.x, unit.y);
                    if (build != null && state.teams.canInteract(unit.team, build.team)) Call.requestBuildPayload(player, build);
                }
            }
            if (Keybind.drop_cargo.tap()) Call.requestDropPayload(player, player.x, player.y);
        }

        player.mouseX = unit.aimX;
        player.mouseY = unit.aimY;
        player.boosting = Keybind.boost.down();
    }

    protected void updateZoom() {
        // both scroll and direction can be used to rotate
        if (rotating) return;

        int scroll = (int) Keybind.scroll();
        if (scroll == 0 || scene.hasMouse()) return;

        if (dir != scroll) {
            dir = scroll;
            scrolls = 0;
        } else
            scrolls++;

        dest -= scroll * (4f + (scrolls / aspect));
        dest = Mathf.clamp(dest, minZoom, maxZoom);
    }

    protected void updateCommand() {
        if (commandMode = Keybind.command.down() && !mapfrag.shown) {
            commandUnits.retainAll(Unitc::isCommandable).retainAll(Healthc::isValid);

            // little fix for edge cases
            if (Keybind.command.tap()) commandRect = null;

            if (Keybind.select.tap() && !scene.hasMouse()) commandRect = input.mouseWorld().cpy();
            if (Keybind.select.release() && commandRect != null) {

                if (commandRect.within(input.mouseWorld(), 8f)) {
                    var unit = selectedUnit();
                    var build = selectedBuilding();

                    if (unit != null) {
                        commandBuildings.clear();
                        if (!commandUnits.remove(unit)) commandUnits.add(unit);
                    }
                    else if (build != null && build.team == player.team() && build.block.commandable) {
                        commandUnits.clear();
                        if (!commandBuildings.remove(build)) commandBuildings.add(build);
                    }
                } else {
                    commandBuildings.clear();
                    selectedRegion(commandUnits::addUnique);
                }

                commandRect = null;
            }

            if (Keybind.select_all_units.tap()) {
                commandUnits.clear();
                commandBuildings.clear();

                player.team().data().units.each(Unitc::isCommandable, commandUnits::add);
            }
            if (Keybind.select_all_factories.tap()) {
                commandUnits.clear();
                commandBuildings.clear();

                player.team().data().buildings.each(b -> b.block.commandable, commandBuildings::add);
            }
            if (Keybind.deselect.tap()) {
                commandUnits.clear();
                commandBuildings.clear();
            }

            if (Keybind.attack.tap() && !scene.hasMouse()) {
                if (commandUnits.any()) {
                    var unit = selectedEnemy();
                    var build = selectedBuilding();

                    if (build != null && build.team == player.team()) build = null;

                    int[] ids = commandUnits.mapInt(Unit::id).toArray();
                    int chunkSize = 128;

                    if (ids.length <= chunkSize)
                        Call.commandUnits(player, ids, build, unit, input.mouseWorld().cpy());

                    else for (int i = 0; i < ids.length; i += chunkSize) {
                        int[] chunk = Arrays.copyOfRange(ids, i, Math.min(i + chunkSize, ids.length));
                        Call.commandUnits(player, chunk, build, unit, input.mouseWorld().cpy());
                    }
                }
                if (commandBuildings.any()) Call.commandBuilding(player, commandBuildings.mapInt(Building::pos).toArray(), input.mouseWorld().cpy());
            }
        }
        if (controlMode = Keybind.control.down() && !scene.hasMouse() && state.rules.possessionAllowed) {
            if (!Keybind.select.tap()) return;

            var unit = selectedUnit();
            var build = selectedBuilding();

            if (build != null && build.team != player.team()) build = null;

            if (unit != null)
                Call.unitControl(player, unit);
            else if (build != null && build instanceof ControlBlock c && c.canControl() && c.unit().isAI())
                Call.unitControl(player, c.unit());
            else if (build != null)
                Call.buildingControlSelect(player, build);
        }
    }

    protected void updateView() {
        if (Keybind.menu.tap()) {

            if (ui.chatfrag.shown())
                ui.chatfrag.hide();

            else if (mapfrag.shown)
                mapfrag.shown = false;

            else {
                ui.paused.show();
                if (!net.active()) state.set(GameState.State.paused);
            }
        }

        if (Keybind.sector_map.tap()) mapfrag.toggle();
        if (Keybind.planet_map.tap() && state.isCampaign()) ui.planet.show();
        if (Keybind.research.tap() && state.isCampaign()) ui.research.show();
        if (Keybind.database.tap()) ui.database.show();

        if (Keybind.block_info.tap()) {
            var build = selectedBuilding();
            var hover = insys.block != null ? insys.block : build == null ? null : build instanceof ConstructBuild c ? c.current : build.block;

            if (hover != null && hover.unlockedNow()) ui.content.show(hover);
        }

        if (Keybind.tgl_menus.tap()) hudfrag.shown = !hudfrag.shown;
        if (Keybind.tgl_power_lasers.tap()) {
            if (settings.getInt("lasersopacity") == 0)
                settings.put("lasersopacity", settings.getInt("preferredlaseropacity", 100));
            else {
                settings.put("preferredlaseropacity", settings.getInt("lasersopacity"));
                settings.put("lasersopacity", 0);
            }
        }
        if (Keybind.tgl_block_status.tap()) settings.put("blockstatus", !settings.getBool("blockstatus"));
        if (Keybind.tgl_block_health.tap()) settings.put("blockhealth", !settings.getBool("blockhealth"));
    }

    protected void updateBuilding() {
        var plans = player.unit().plans;

        if (Keybind.hexblock.tap()) polyblock.show();
        if (Keybind.srcblock.tap()); // TODO block search fragments

        if (Keybind.pause_bd.tap()) building = !building;
        if (Keybind.clear_bd.tap()) plans.clear();

        if (Keybind.drop.tap()); // TODO ai.drop = building
        if (Keybind.drop.release()); // TODO ai.drop = null

        if (Keybind.pick.tap()) {

            var build = selectedBuilding();
            if (build != null && build.inFogTo(player.team())) build = null; // questionable, but cheating is bad, right?

            var recipe = build == null ? null : build instanceof ConstructBuild c ? c.current : build.block;
            var config = build == null ? null : build.block.copyConfig ? build.config() : null;

            var index = plans.indexOf(p -> !p.breaking && p.block.bounds(p.x, p.y, Tmp.r1).contains(input.mouseWorld()));
            if (index != -1) {
                recipe = plans.get(index).block;
                config = plans.get(index).config;
            }

            if (recipe != null && polyblock.unlocked(recipe)) {
                block = recipe;
                recipe.lastConfig = config;
            }
        }

        if (Keybind.rotate.tap()) toRotate = new Rotatable(selectedBuilding(), block != null ? temp : null);
        if (Keybind.rotate.release()) toRotate = null;

        temp.block = block; // rotatable uses the plan
        rotating = toRotate != null && toRotate.valid();

        if (rotating) {
            if (input.mouseWorld().within(toRotate, toRotate.radius())) {
                int scroll = (int) Keybind.scroll();
                if (scroll != 0) toRotate.rotateBy(scroll);
            } else
                toRotate.rotateTo(Mathf.round(Angles.angle(toRotate.getX(), toRotate.getY(), input.mouseWorldX(), input.mouseWorldY()) / 90f) % 4);
        }

        if (Keybind.sel_schematic.tap()) ui.schematics.show();
        if (Keybind.hex_schematic.tap()) polyschema.show();
    }

    @Override
    protected void updateState() {
        if (state.isMenu()) {
            block = null;
            building = true;
            toRotate = null;
        }
        if (Keybind.tgl_fullscreen.tap()) {
            if (settings.getBool("fullscreen")) {
                settings.put("fullscreen", false);
                graphics.setWindowedMode(graphics.getWidth(), graphics.getHeight());
            } else {
                settings.put("fullscreen", true);
                graphics.setFullscreen();
            }
        }
    }

    @Override
    public void drawPlans() {
        var plans = player.unit().plans;

        // TODO draw plans

        if (block == null) return;

        var tx = rotating ? toRotate.x : World.toTile(input.mouseWorldX() - block.offset);
        var ty = rotating ? toRotate.y : World.toTile(input.mouseWorldY() - block.offset);

        var rot = temp.rotation;
        var valid = control.input.validPlace(tx, ty, block, 0);

        temp.set(tx, ty, rot, block);
        temp.config = block.lastConfig;

        block.drawPlan(temp, plans, valid);
        block.drawPlace(tx, ty, rot, valid);

        if (block.saveConfig) {
            Draw.mixcol(valid ? Color.white : Pal.breakInvalid, (valid ? .2f : .4f) + Mathf.absin(Time.globalTime, 6f, .3f));
            block.drawPlanConfig(temp, plans);
            Draw.reset();
        }

        // honestly, I have no clue what it is
        control.input.drawOverlapCheck(block, tx, ty, valid);
    }

    @Override
    public void drawOverlay() {
        if (commandMode) drawCommand();
        if (controlMode) drawControl();
        else controlFade = 0f;

        if (rotating) {
            Lines.stroke(1f, Pal.accent);
            overlay.capture(4f);

            for (int i = 0; i < 4; i++) {
                Tmp.v1.trns(i * 90f, toRotate.radius() + 1f).add(toRotate);

                Lines.arc(toRotate.getX(), toRotate.getY(), toRotate.radius(), .2f, i * 90f - 36f);
                Fill.poly(Tmp.v1.x,        Tmp.v1.y,     3, 2f,                     i * 90f);
            }

            overlay.render();
            Draw.reset();
        }
    }
}
