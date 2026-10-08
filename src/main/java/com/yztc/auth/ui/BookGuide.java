package com.yztc.auth.ui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 指南书：成书页里塞 JSON 文本的 Component，第 1/2 页带 suggest_command 点击。 */
public final class BookGuide {
    private BookGuide() {}

    public static void give(ServerPlayer p) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.setTag(bookTag());
        p.getInventory().add(book);
        p.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
    }

    /**
     * 纯 NBT 组装（不触碰物品注册表，故自测内可用）。
     * 成书页存的是 JSON 文本；{@code ItemStack} 的构造依赖 {@code BuiltInRegistries}，只在 {@link #give} 里做。
     */
    public static CompoundTag bookTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", "yztauth 指南");
        tag.putString("author", "服务器");
        tag.put("pages", pages());
        return tag;
    }

    public static ListTag pages() {
        ListTag pages = new ListTag();
        pages.add(page("{\"text\":\"第 1 步：登录\\n点下方命令即可自动填入：\\n\","
                + "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/yztauth login \"}}"));
        pages.add(page("{\"text\":\"第 2 步：注册\\n首次进服使用：\\n\","
                + "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/yztauth register \"}}"));
        pages.add(page("{\"text\":\"忘记密码？\\n请联系管理员重置。\\n也可用 /yztauth email 预留邮箱。\"}"));
        return pages;
    }

    private static StringTag page(String json) {
        return StringTag.valueOf(json);
    }
}
