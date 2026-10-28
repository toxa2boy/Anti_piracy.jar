package net.mcreator.antipiracyjar;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class UiVisitGate {
    public static void receive(ServerPlayer player,int action) {
        if (action==1 || action==4) {
            boolean test=action==4 && player.hasPermissions(2);
            if (!test && !HorrorDirector.roll(player,"inventory",12,600,1)) return;
            if (HorrorDirector.start(player,"inventory",100,test))
                PacketDistributor.sendToPlayer(player,new HorrorNetwork.EffectPayload(26));
        } else if (action==3) HorrorDirector.renew(player,"inventory",100);
        else if (action==0 || action==2) HorrorDirector.finish(player,"inventory",action==2);
    }
}
