package com.yztc.auth.ui;

import com.google.gson.JsonObject;
import com.yztc.auth.lang.ServerLang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.IntPredicate;

/**
 * 指南书：成书页里塞 JSON 文本的 Component，纯说明文本，不带点击。
 *
 * <p>1.20.1 原版在书页里对 {@code suggest_command} 的处理是 {@code Screen.insertText}，
 * 而该方法在 1.20.1 是空实现（已由字节码核实），故书内点击**无任何效果**；
 * 与其留一个点了没反应的假点击，不如全用纯文本，并让正文直接指路可用的聊天栏绿色按钮。
 *
 * <p>翻书由 {@code ClientboundOpenBookPacket(MAIN_HAND)} 打开**当前选中格**，故 {@link #give}
 * 先把书放进可用的热栏格并同步选中，避免书落在背包里、点了却翻不开。
 */
public final class BookGuide {
    private BookGuide() {}

    private static final int HOTBAR_SIZE = 9;

    public static void give(ServerPlayer p) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.setTag(bookTag());
        Inventory inv = p.getInventory();
        int slot = hotbarSlotFor(inv.selected, i -> inv.getItem(i).isEmpty());
        if (slot < 0) {
            inv.add(book); // 热栏全满：放背包，不自动翻页，玩家自行打开
            return;
        }
        inv.setItem(slot, book);
        if (inv.selected != slot) {
            inv.selected = slot;
            p.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
        p.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
    }

    /**
     * 选热栏（0-8）里能放书的空格：优先当前选中格，其次任一空格，全满返回 -1。
     * 纯逻辑，便于自测。
     */
    public static int hotbarSlotFor(int selected, IntPredicate isEmpty) {
        if (selected >= 0 && selected < HOTBAR_SIZE && isEmpty.test(selected)) return selected;
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && isEmpty.test(i)) return i;
        }
        return -1;
    }

    /**
     * 纯 NBT 组装（不触碰物品注册表，故自测内可用）。
     * 成书页存的是 JSON 文本；{@code ItemStack} 的构造依赖 {@code BuiltInRegistries}，只在 {@link #give} 里做。
     */
    public static CompoundTag bookTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", ServerLang.template("book_title"));
        tag.putString("author", ServerLang.template("book_author"));
        tag.put("pages", pages());
        return tag;
    }

    public static ListTag pages() {
        ListTag pages = new ListTag();
        pages.add(page(plainPage("book_p1")));
        pages.add(page(plainPage("book_p2")));
        pages.add(page(plainPage("book_p3")));
        pages.add(page(plainPage("book_p4")));
        return pages;
    }

    private static String plainPage(String textKey) {
        JsonObject text = new JsonObject();
        text.addProperty("text", ServerLang.template(textKey));
        return text.toString();
    }

    private static StringTag page(String json) {
        return StringTag.valueOf(json);
    }
}
