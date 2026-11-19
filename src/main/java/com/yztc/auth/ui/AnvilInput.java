package com.yztc.auth.ui;

import com.yztc.auth.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

/** Server-side text input using an anvil menu; clicking the result slot submits, Esc cancels. */
public final class AnvilInput {
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");

    private AnvilInput() {}

    /** Pure logic: only nonblank input can be submitted. */
    public static boolean shouldCommit(String value) {
        return value != null && !value.isBlank();
    }

    /** One-shot input used by existing single-step flows. */
    public static void open(ServerPlayer player, Component title, UiSession uiSession) {
        Objects.requireNonNull(uiSession, "uiSession");
        Session session = new Session(
                (p, value) -> {
                    return Response.close(() -> uiSession.commit(value));
                },
                uiSession::cancel,
                () -> UiSession.forget(player.getUUID()));
        open(player, title, session, null);
    }

    /**
     * Opens a multi-step input flow. A response may finish, retry the current step, or move to
     * another step while keeping this same server menu open. Prompts for later steps are shown in chat.
     */
    public static void openFlow(ServerPlayer player, Component title, Component firstPrompt,
                                SubmitHandler firstHandler, Runnable onCancel) {
        Objects.requireNonNull(firstHandler, "firstHandler");
        Session session = new Session(firstHandler, onCancel == null ? () -> {} : onCancel, () -> {});
        open(player, title, session, firstPrompt);
    }

    private static void open(ServerPlayer player, Component title, Session session, Component firstPrompt) {
        if (firstPrompt != null) player.sendSystemMessage(firstPrompt);
        player.sendSystemMessage(Lang.msg("anvil_howto"));
        Sounds.prompt(player);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new CaptureAnvilMenu(id, inv, session, player), title));
        if (player.containerMenu instanceof CaptureAnvilMenu menu) menu.refreshSubmitButton();
    }

    @FunctionalInterface
    public interface SubmitHandler {
        Response submit(ServerPlayer player, String value);
    }

    /** Result returned by one step of a flow. */
    public static final class Response {
        private enum Kind { CLOSE, KEEP, RETRY, NEXT }

        private final Kind kind;
        private final Component prompt;
        private final SubmitHandler nextHandler;
        private final Runnable afterClose;

        private Response(Kind kind, Component prompt, SubmitHandler nextHandler, Runnable afterClose) {
            this.kind = kind;
            this.prompt = prompt;
            this.nextHandler = nextHandler;
            this.afterClose = afterClose;
        }

        public static Response close() {
            return close(() -> {});
        }

        /** Close this menu before running an action that may open another menu. */
        public static Response close(Runnable afterClose) {
            return new Response(Kind.CLOSE, null, null, Objects.requireNonNull(afterClose, "afterClose"));
        }

        /** Keep the menu and current text unchanged, optionally showing a prompt. */
        public static Response keep(Component prompt) {
            return new Response(Kind.KEEP, prompt, null, null);
        }

        /** Clear the input and keep the same step open, optionally showing a new chat prompt. */
        public static Response retry(Component prompt) {
            return new Response(Kind.RETRY, prompt, null, null);
        }

        /** Clear the input, show the next prompt, and keep the same menu open for the next step. */
        public static Response next(Component prompt, SubmitHandler nextHandler) {
            return new Response(Kind.NEXT, prompt, Objects.requireNonNull(nextHandler, "nextHandler"), null);
        }
    }

    private static final class Session {
        private SubmitHandler handler;
        private final Runnable onCancel;
        private final Runnable onFinish;
        private boolean active = true;

        private Session(SubmitHandler handler, Runnable onCancel, Runnable onFinish) {
            this.handler = handler;
            this.onCancel = onCancel;
            this.onFinish = onFinish;
        }

        private void cancel() {
            if (!active) return;
            active = false;
            try {
                onCancel.run();
            } finally {
                onFinish.run();
            }
        }

        private void finish() {
            if (!active) return;
            active = false;
            onFinish.run();
        }
    }

    public static final class CaptureAnvilMenu extends AnvilMenu {
        private final Session session;
        private final ServerPlayer owner;
        private String last;
        private boolean completed;
        private boolean submitting;
        private boolean refreshQueued;

        private CaptureAnvilMenu(int id, Inventory inv, Session session, ServerPlayer owner) {
            super(id, inv, ContainerLevelAccess.NULL);
            this.session = session;
            this.owner = owner;
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.setHoverName(Lang.msg("anvil_input_hint"));
            getSlot(INPUT_SLOT).set(paper);
            createResult();
        }

        /** The rename packet is sent per keystroke; keep the latest text and mirror it on the paper. */
        @Override
        public boolean setItemName(String name) {
            boolean changed = super.setItemName(name);
            if (name != null) {
                last = name;
                ItemStack input = getSlot(INPUT_SLOT).getItem();
                if (!input.isEmpty()) input.setHoverName(Component.literal(name));
            }
            refreshSubmitButton();
            return changed;
        }

        @Override
        public void sendAllDataToRemote() {
            super.sendAllDataToRemote();
            refreshSubmitButton();
        }

        // 原版在本地收到左槽或编辑名称时会重算铁砧配方，可能清空不属于配方的提交按钮。
        // 输入同步完成后只重发右槽，避免重设文本光标或再次触发重命名包。
        private void refreshSubmitButton() {
            if (refreshQueued || owner == null || owner.containerMenu != this) return;
            refreshQueued = true;
            Defer.nextTick(owner, () -> {
                refreshQueued = false;
                owner.connection.send(new ClientboundContainerSetSlotPacket(containerId,
                        incrementStateId(), RESULT_SLOT, getSlot(RESULT_SLOT).getItem()));
            });
        }

        /** Result slot is a submit button, not a renamed item. */
        @Override
        public void createResult() {
            ItemStack submit = new ItemStack(Items.LIME_DYE);
            submit.setHoverName(Lang.msg("anvil_submit_hint"));
            resultSlots.setItem(0, submit);
            setData(0, 0);
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, Player player) {
            if (slotId == RESULT_SLOT) {
                if (type == ClickType.PICKUP) submit();
                else sendAllDataToRemote();
                return;
            }
            if (slotId == INPUT_SLOT || slotId == ADDITIONAL_SLOT) {
                sendAllDataToRemote();
                return;
            }
            super.clicked(slotId, button, type, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        /** Let the client send output-slot clicks; the server handler consumes them as submit actions. */
        @Override
        protected boolean mayPickup(Player player, boolean hasStack) {
            return hasStack;
        }

        private void submit() {
            if (completed || submitting || !session.active) return;
            String value = last;
            if (!shouldCommit(value)) {
                owner.sendSystemMessage(Lang.msg("anvil_empty"));
                sendAllDataToRemote();
                return;
            }
            submitting = true;
            sendAllDataToRemote();
            Defer.nextTick(owner, () -> apply(value));
        }

        private void apply(String value) {
            if (completed || !session.active) return;
            Response response;
            try {
                response = session.handler.submit(owner, value);
                if (response == null) response = Response.close();
            } catch (RuntimeException e) {
                LOGGER.error("[yzt_super] 铁砧输入处理失败", e);
                owner.sendSystemMessage(Lang.msg("anvil_submit_error"));
                response = Response.retry(null);
            }

            switch (response.kind) {
                case CLOSE -> {
                    session.finish();
                    completed = true;
                    owner.closeContainer();
                    try {
                        response.afterClose.run();
                    } catch (RuntimeException e) {
                        LOGGER.error("[yzt_super] 铁砧输入关闭后回调失败", e);
                        owner.sendSystemMessage(Lang.msg("anvil_submit_error"));
                    }
                }
                case KEEP -> {
                    if (response.prompt != null) owner.sendSystemMessage(response.prompt);
                    sendAllDataToRemote();
                }
                case RETRY -> {
                    if (response.prompt != null) owner.sendSystemMessage(response.prompt);
                    resetInput();
                }
                case NEXT -> {
                    session.handler = response.nextHandler;
                    if (response.prompt != null) owner.sendSystemMessage(response.prompt);
                    resetInput();
                }
            }
            submitting = false;
        }

        private void resetInput() {
            setItemName("");
            last = null;
            sendAllDataToRemote();
        }

        /** Closing the menu cancels; it never submits typed text. */
        @Override
        public void removed(Player player) {
            inputSlots.clearContent();
            resultSlots.clearContent();
            if (completed || !session.active) return;
            session.cancel();
            owner.sendSystemMessage(Lang.msg("anvil_cancelled"));
        }
    }
}
