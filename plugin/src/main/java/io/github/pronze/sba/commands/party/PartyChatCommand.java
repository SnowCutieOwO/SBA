package io.github.pronze.sba.commands.party;

import cloud.commandframework.annotations.CommandMethod;
import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.wrapper.PlayerSetting;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import io.github.pronze.sba.commands.CommandManager;
import io.github.pronze.sba.lib.lang.LanguageService;

@Service
public class PartyChatCommand {
    static boolean init = false;
    @OnPostEnable
    public void onPostEnabled() {
        if (init)
            return;
        CommandManager.getInstance().getAnnotationParser().parse(this);
        init = true;
    }
    @CommandMethod("party|p chat")
    private void commandChat(
            final @NotNull Player playerArg
    ) {
        final var player = SBA.getInstance().getPlayerWrapper((playerArg));

        player.getSettings().toggle(PlayerSetting.PARTY_CHAT_ENABLED);

        LanguageService
                .getInstance()
                .get(MessageKeys.PARTY_MESSAGE_CHAT_ENABLED_OR_DISABLED)
                .replace("%mode%", player.getSettings().isToggled(PlayerSetting.PARTY_CHAT_ENABLED) ? "enabled" : "disabled")
                .send(player);
    }
}
