package com.soundmixer.client;

import com.soundmixer.config.SoundMixerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The main configuration screen. It deliberately does not depend on Cloth
 * Config or any other UI library, so Mod Menu only has to supply the parent
 * screen.
 */
public final class SoundMixerScreen extends Screen {
	private static final int MAX_ROWS = 10;
	private static final int ROW_HEIGHT = 26;
	private static final int WIDGET_HEIGHT = 18;
	private static final int ACCENT = 0xFF55D6A5;
	private static final int MODIFIED_ACCENT = 0xFFFFC857;
	private static final int TEXT_PRIMARY = 0xFFF2F2F2;
	private static final int TEXT_SECONDARY = 0xFF9AA0A6;
	private static final int CARD_BACKGROUND = 0x66101010;
	private static final int CARD_BORDER = 0x22FFFFFF;

	private final Screen parent;
	private final VolumeSlider[] sliders = new VolumeSlider[MAX_ROWS];
	private final Button[] resetButtons = new Button[MAX_ROWS];
	private final SoundCatalog.SoundEntry[] rowEntries = new SoundCatalog.SoundEntry[MAX_ROWS];

	private List<SoundCatalog.SoundEntry> catalog;
	private List<SoundCatalog.SoundEntry> filtered = List.of();
	private EditBox search;
	private Button categoryButton;
	private Button modifiedButton;
	private Button previousButton;
	private Button nextButton;
	private Button resetAllButton;
	private Button doneButton;

	private SoundCatalog.Category category = SoundCatalog.Category.ALL;
	private boolean modifiedOnly;
	private int scrollRow;
	private int rowsPerPage = 4;
	private int filteredCount;
	private int left;
	private int contentWidth;
	private int listTop;
	private int footerButtonY;
	private int footerTextY;
	private int pageTextX;
	private int pageTextWidth;
	private int nameWidth;

	private Component status;
	private long statusUntil;
	private long resetConfirmUntil;

	public SoundMixerScreen(Screen parent) {
		super(Component.translatable("sound_mixer.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.catalog == null) {
			this.catalog = SoundCatalog.build();
		}

		this.clearWidgets();

		this.search = new EditBox(this.font, 0, 0, 120, WIDGET_HEIGHT, Component.translatable("sound_mixer.search"));
		this.search.setHint(Component.translatable("sound_mixer.search_hint"));
		this.search.setMaxLength(80);
		this.search.setResponder(value -> {
			this.scrollRow = 0;
			this.refresh();
		});
		this.addRenderableWidget(this.search);

		this.categoryButton = Button.builder(this.categoryLabel(), button -> {
			SoundCatalog.Category[] categories = {
					SoundCatalog.Category.ALL,
					SoundCatalog.Category.BLOCKS,
					SoundCatalog.Category.ENTITIES,
					SoundCatalog.Category.AMBIENT,
					SoundCatalog.Category.MUSIC,
					SoundCatalog.Category.UI,
					SoundCatalog.Category.OTHER
			};
			int index = 0;
			for (int i = 0; i < categories.length; i++) {
				if (categories[i] == this.category) {
					index = i;
					break;
				}
			}
			this.category = categories[(index + 1) % categories.length];
			this.scrollRow = 0;
			this.refresh();
		}).bounds(0, 0, 92, WIDGET_HEIGHT).build();
		this.addRenderableWidget(this.categoryButton);

		this.modifiedButton = Button.builder(this.modifiedLabel(), button -> {
			this.modifiedOnly = !this.modifiedOnly;
			this.scrollRow = 0;
			this.refresh();
		}).bounds(0, 0, 80, WIDGET_HEIGHT).build();
		this.addRenderableWidget(this.modifiedButton);

		for (int i = 0; i < MAX_ROWS; i++) {
			VolumeSlider slider = new VolumeSlider(0, 0, 110, WIDGET_HEIGHT);
			int row = i;
			slider.setOnRelease(this::refresh);
			this.sliders[i] = slider;
			this.addRenderableWidget(slider);

			Button reset = Button.builder(Component.literal("1x"), button -> this.resetRow(row))
					.bounds(0, 0, 22, WIDGET_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("sound_mixer.reset.tooltip")))
					.build();
			this.resetButtons[i] = reset;
			this.addRenderableWidget(reset);
		}

		this.previousButton = Button.builder(Component.literal("<"), button -> this.scrollBy(-this.rowsPerPage))
				.bounds(0, 0, 22, WIDGET_HEIGHT).build();
		this.addRenderableWidget(this.previousButton);

		this.nextButton = Button.builder(Component.literal(">"), button -> this.scrollBy(this.rowsPerPage))
				.bounds(0, 0, 22, WIDGET_HEIGHT).build();
		this.addRenderableWidget(this.nextButton);

		this.resetAllButton = Button.builder(Component.translatable("sound_mixer.reset_all"), button -> this.resetAll())
				.bounds(0, 0, 88, WIDGET_HEIGHT).build();
		this.addRenderableWidget(this.resetAllButton);

		this.doneButton = Button.builder(Component.translatable("sound_mixer.done"), button -> this.onClose())
				.bounds(0, 0, 58, WIDGET_HEIGHT).build();
		this.addRenderableWidget(this.doneButton);

		this.layout();
		this.refresh();
		this.clearFocus();
	}

	private Component categoryLabel() {
		return Component.translatable("sound_mixer.filter", this.category.label());
	}

	private Component modifiedLabel() {
		return Component.translatable(this.modifiedOnly ? "sound_mixer.modified.on" : "sound_mixer.modified.off");
	}

	private void layout() {
		this.contentWidth = Math.min(this.width - 24, 560);
		this.left = (this.width - this.contentWidth) / 2;

		int searchY = 40;
		int filterY = 66;
		this.listTop = 90;
		this.footerButtonY = this.height - 24;
		this.footerTextY = this.footerButtonY - 14;

		int available = this.footerTextY - this.listTop - 2;
		this.rowsPerPage = Mth.clamp(available / ROW_HEIGHT, 1, MAX_ROWS);

		int categoryWidth = 92;
		int modifiedWidth = 80;
		int searchWidth = Math.max(70, this.contentWidth - categoryWidth - modifiedWidth - 16);
		// 26.2's setRectangle signature is (width, height, x, y).
		this.search.setRectangle(searchWidth, WIDGET_HEIGHT, this.left, searchY);
		this.categoryButton.setRectangle(categoryWidth, WIDGET_HEIGHT, this.left + searchWidth + 8, searchY);
		this.modifiedButton.setRectangle(modifiedWidth, WIDGET_HEIGHT,
				this.left + searchWidth + 8 + categoryWidth + 8, searchY);

		int sliderWidth = Mth.clamp(this.contentWidth / 3, 78, 140);
		int resetWidth = 22;
		int rowRight = this.left + this.contentWidth;
		int sliderX = rowRight - resetWidth - 6 - sliderWidth;
		this.nameWidth = Math.max(40, sliderX - (this.left + 10) - 8);

		for (int i = 0; i < MAX_ROWS; i++) {
			int rowY = this.listTop + i * ROW_HEIGHT;
			this.sliders[i].setRectangle(sliderWidth, WIDGET_HEIGHT, sliderX, rowY + 4);
			this.resetButtons[i].setRectangle(resetWidth, WIDGET_HEIGHT, rowRight - resetWidth, rowY + 4);
		}

		int pageWidth = 80;
		this.previousButton.setRectangle(22, WIDGET_HEIGHT, this.left, this.footerButtonY);
		this.pageTextX = this.left + 26;
		this.pageTextWidth = pageWidth;
		this.nextButton.setRectangle(22, WIDGET_HEIGHT, this.pageTextX + pageWidth + 4, this.footerButtonY);

		int doneWidth = 58;
		int resetAllWidth = 88;
		this.doneButton.setRectangle(doneWidth, WIDGET_HEIGHT,
				this.left + this.contentWidth - doneWidth, this.footerButtonY);
		this.resetAllButton.setRectangle(resetAllWidth, WIDGET_HEIGHT,
				this.left + this.contentWidth - doneWidth - 8 - resetAllWidth, this.footerButtonY);
	}

	private void refresh() {
		String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
		List<SoundCatalog.SoundEntry> matching = new ArrayList<>();
		for (SoundCatalog.SoundEntry entry : this.catalog) {
			if (this.modifiedOnly && !SoundMixerConfig.isModified(entry.id())) {
				continue;
			}
			if (this.category != SoundCatalog.Category.ALL && entry.category() != this.category) {
				continue;
			}
			if (!entry.matches(query)) {
				continue;
			}
			matching.add(entry);
		}

		this.filtered = matching;
		this.filteredCount = matching.size();
		int maxScroll = Math.max(0, matching.size() - this.rowsPerPage);
		this.scrollRow = Mth.clamp(this.scrollRow, 0, maxScroll);

		for (int i = 0; i < MAX_ROWS; i++) {
			int index = this.scrollRow + i;
			boolean visible = i < this.rowsPerPage && index < matching.size();
			this.sliders[i].visible = visible;
			this.resetButtons[i].visible = visible;
			if (visible) {
				SoundCatalog.SoundEntry entry = matching.get(index);
				this.rowEntries[i] = entry;
				this.sliders[i].bind(entry);
			} else {
				this.rowEntries[i] = null;
			}
		}

		this.previousButton.active = this.scrollRow > 0;
		this.nextButton.active = this.scrollRow < maxScroll;
		this.categoryButton.setMessage(this.categoryLabel());
		this.modifiedButton.setMessage(this.modifiedLabel());
	}

	private void scrollBy(int rows) {
		int maxScroll = Math.max(0, this.filtered.size() - this.rowsPerPage);
		int target = Mth.clamp(this.scrollRow + rows, 0, maxScroll);
		if (target != this.scrollRow) {
			this.scrollRow = target;
			this.refresh();
		}
	}

	private void resetRow(int row) {
		SoundCatalog.SoundEntry entry = this.rowEntries[row];
		if (entry == null) {
			return;
		}
		SoundMixerConfig.reset(entry.id());
		this.sliders[row].resetToDefault();
		this.setStatus(Component.translatable("sound_mixer.status.reset", entry.name()));
		this.refresh();
	}

	private void resetAll() {
		long now = Util.getMillis();
		if (now <= this.resetConfirmUntil) {
			SoundMixerConfig.resetAll();
			SoundMixerConfig.save();
			this.resetConfirmUntil = 0;
			this.resetAllButton.setMessage(Component.translatable("sound_mixer.reset_all"));
			this.setStatus(Component.translatable("sound_mixer.status.reset_all"));
			this.scrollRow = 0;
			this.refresh();
			return;
		}

		this.resetConfirmUntil = now + 3000L;
		this.resetAllButton.setMessage(Component.translatable("sound_mixer.reset_all.confirm"));
	}

	private void setStatus(Component message) {
		this.status = message;
		this.statusUntil = Util.getMillis() + 2500L;
	}

	@Override
	protected void setInitialFocus() {
		// Keep the search box unfocused so its hint is visible; Tab/Ctrl+F still work.
	}

	@Override
	public void tick() {
		super.tick();
		long now = Util.getMillis();
		if (this.status != null && now > this.statusUntil) {
			this.status = null;
		}
		if (this.resetConfirmUntil != 0L && now > this.resetConfirmUntil) {
			this.resetConfirmUntil = 0L;
			this.resetAllButton.setMessage(Component.translatable("sound_mixer.reset_all"));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.drawHeader(graphics);
		this.drawRows(graphics);
		this.drawFooter(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawHeader(GuiGraphicsExtractor graphics) {
		graphics.fill(this.left, 10, this.left + 3, 28, ACCENT);
		graphics.text(this.font, this.title, this.left + 11, 11, 0xFFFFFFFF, true);
		graphics.text(this.font, Component.translatable("sound_mixer.subtitle"), this.left, 25, TEXT_SECONDARY);

		String total = Component.translatable("sound_mixer.total", this.catalog.size()).getString();
		graphics.text(this.font, total, this.left + this.contentWidth - this.font.width(total), 11, TEXT_SECONDARY);
		graphics.fill(this.left, 35, this.left + this.contentWidth, 36, CARD_BORDER);
	}

	private void drawRows(GuiGraphicsExtractor graphics) {
		if (this.filteredCount == 0) {
			graphics.centeredText(this.font, Component.translatable("sound_mixer.empty"),
					this.left + this.contentWidth / 2,
					this.listTop + Math.max(8, this.rowsPerPage * ROW_HEIGHT / 2 - 4), TEXT_SECONDARY);
			return;
		}

		for (int i = 0; i < this.rowsPerPage; i++) {
			SoundCatalog.SoundEntry entry = this.rowEntries[i];
			if (entry == null) {
				continue;
			}
			int rowY = this.listTop + i * ROW_HEIGHT;
			boolean modified = SoundMixerConfig.isModified(entry.id());
			graphics.fill(this.left, rowY, this.left + this.contentWidth, rowY + ROW_HEIGHT - 4, CARD_BACKGROUND);
			graphics.fill(this.left, rowY, this.left + this.contentWidth, rowY + 1, CARD_BORDER);
			graphics.fill(this.left, rowY, this.left + 2, rowY + ROW_HEIGHT - 4,
					modified ? MODIFIED_ACCENT : ACCENT);

			String name = this.font.plainSubstrByWidth(entry.name(), this.nameWidth);
			graphics.text(this.font, name, this.left + 10, rowY + 3, TEXT_PRIMARY);
			String id = this.font.plainSubstrByWidth(entry.idText(), this.nameWidth);
			graphics.text(this.font, id, this.left + 10, rowY + 13, TEXT_SECONDARY);
		}
	}

	private void drawFooter(GuiGraphicsExtractor graphics) {
		Component text = this.status != null
				? this.status
				: Component.translatable("sound_mixer.count", this.filteredCount, this.catalog.size(),
						SoundMixerConfig.modifiedCount());
		int color = this.status != null ? ACCENT : TEXT_SECONDARY;
		graphics.text(this.font, text, this.left, this.footerTextY, color);

		int start = this.filtered.isEmpty() ? 0 : this.scrollRow + 1;
		int end = Math.min(this.filtered.size(), this.scrollRow + this.rowsPerPage);
		Component range = Component.translatable("sound_mixer.range", start, end, this.filtered.size());
		graphics.centeredText(this.font, range, this.pageTextX + this.pageTextWidth / 2,
				this.footerButtonY + 5, TEXT_SECONDARY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= this.left && mouseX <= this.left + this.contentWidth
				&& mouseY >= this.listTop && mouseY <= this.footerTextY) {
			if (scrollY > 0.0D) {
				this.scrollBy(-2);
				return true;
			}
			if (scrollY < 0.0D) {
				this.scrollBy(2);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_F && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			this.setFocused(this.search);
			this.search.setCursorPosition(this.search.getValue().length());
			return true;
		}
		if (!this.search.isFocused()) {
			if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
				this.scrollBy(-this.rowsPerPage);
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
				this.scrollBy(this.rowsPerPage);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		SoundMixerConfig.saveIfDirty();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		SoundMixerConfig.saveIfDirty();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
