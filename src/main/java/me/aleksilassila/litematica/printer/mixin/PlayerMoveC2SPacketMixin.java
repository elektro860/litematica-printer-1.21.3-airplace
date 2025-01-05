package me.aleksilassila.litematica.printer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

@Mixin(PlayerMoveC2SPacket.class)
public class PlayerMoveC2SPacketMixin {

    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 3, argsOnly = true)
    private static boolean modifyChangedLook(boolean changedLook) {
        Printer printer = LitematicaMixinMod.printer;
        if (printer == null) {
            return changedLook;
        }
        PrepareAction prep_Action = printer.actionHandler.lookAction;
        if (prep_Action == null)
            return changedLook;

        Printer.printDebug("CHANGED_LOOK: {}", prep_Action.modifyPitch || prep_Action.modifyYaw);
        return prep_Action.modifyPitch || prep_Action.modifyYaw;
    }

    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static float modifyLookYaw(float yaw) {
        Printer printer = LitematicaMixinMod.printer;
        if (printer == null) {
            return yaw;
        }

        PrepareAction action = printer.actionHandler.lookAction;
        if (action != null && action.modifyYaw) {
            Printer.printDebug("YAW: {}", action.yaw);
            return action.yaw;
        } else {
            return yaw;
        }
    }

    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private static float modifyLookPitch(float pitch) {
        Printer printer = LitematicaMixinMod.printer;
        if (printer == null) {
            return pitch;
        }

        PrepareAction action = printer.actionHandler.lookAction;
        if (action != null && action.modifyPitch) {
            Printer.printDebug("PITCH: {}", action.pitch);
            return action.pitch;
        } else {
            return pitch;
        }
    }

}
