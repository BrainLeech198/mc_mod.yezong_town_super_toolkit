package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.AdminCommands;
import com.yztc.auth.command.LocationCommand;
import com.yztc.auth.command.MailCommand;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.title.TitlePanel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 常用功能大厅：把分散的玩家入口与管理员工具放进同一张分区菜单。 */
public final class HomePanel {
    private static final int PLAYER_SLOTS = 45;
    private static final int ADMIN_SLOTS = 54;

    private HomePanel() {}

    public static void open(ServerPlayer viewer, AuthManager auth, GateManager gate, CommandSourceStack source) {
        boolean admin = Permissions.isAdmin(viewer);
        int size = admin ? ADMIN_SLOTS : PLAYER_SLOTS;
        List<ButtonPanel.Btn> buttons = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            Item frame = slot < 9 || (admin && slot >= 45)
                    ? Items.BLACK_STAINED_GLASS_PANE
                    : Items.GRAY_STAINED_GLASS_PANE;
            buttons.add(button(frame, Component.empty(), null, slot));
        }

        replace(buttons, 4, Items.COMPASS,
                Lang.msg("home_header", viewer.getGameProfile().getName()), null);
        replace(buttons, 13, Items.BOOK, Lang.msg("home_section_account"), null);
        replace(buttons, 10, Items.PLAYER_HEAD, Lang.msg("home_account"), p -> AccountPanel.open(p, auth));
        replace(buttons, 12, Items.WRITABLE_BOOK, Lang.msg("home_inbox"), MailCommand::showList);
        replace(buttons, 14, Items.FEATHER, Lang.msg("home_write"), MailPanel::openCompose);
        replace(buttons, 16, Items.CHEST, Lang.msg("home_manage"), MailPanel::openManage);

        replace(buttons, 31, Items.COMPASS, Lang.msg("home_section_tools"), null);
        replace(buttons, 36, Items.BOOK, Lang.msg("home_board"), MailCommand::showBoard);
        replace(buttons, 38, Items.MAP, Lang.msg("home_last_location"), HomePanel::showLastLocation);
        replace(buttons, 40, Items.NAME_TAG, Lang.msg("home_titles"), TitlePanel::open);
        replace(buttons, 42, Items.BELL, Lang.msg("home_shout"), com.yztc.auth.broadcast.BroadcastPrompt::openShout);
        replace(buttons, 44, Items.KNOWLEDGE_BOOK, Lang.msg("home_guide"), BookGuide::give);

        if (admin) {
            replace(buttons, 49, Items.PAPER, Lang.msg("home_section_admin"), null);
            replace(buttons, 46, Items.COMMAND_BLOCK,
                    Lang.msg("home_admin_panel"), p -> AdminPanel.open(p.server, p, auth, gate, source));
            replace(buttons, 52, Items.REDSTONE,
                    Lang.msg("home_risk_records"), RiskPanel::open);
        }

        ButtonPanel.open(viewer, Lang.msg("home_menu_title"), buttons);
    }

    private static ButtonPanel.Btn button(Item item, Component label,
                                           java.util.function.Consumer<ServerPlayer> action, int slot) {
        return new ButtonPanel.Btn(item, label, action, slot);
    }

    private static void replace(List<ButtonPanel.Btn> buttons, int slot, Item item, Component label,
                                java.util.function.Consumer<ServerPlayer> action) {
        buttons.set(slot, button(item, label, action, slot));
    }

    private static void showLastLocation(ServerPlayer viewer) {
        LastSeenStore store = StoreHolder.lastSeen();
        String name = viewer.getGameProfile().getName();
        LastSeen lastSeen = store == null ? null : store.get(name.toLowerCase(Locale.ROOT));
        List<Component> lines = List.of(LocationCommand.whereText(lastSeen, name));
        for (Component line : ChatBox.box(Lang.msg("home_last_location_title"), lines)) {
            viewer.sendSystemMessage(line);
        }
    }
}
