package net.mcft.copy.backpacks.client.gui.config;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;

import net.minecraftforge.fml.client.config.GuiMessageDialog;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.mcft.copy.backpacks.WearableBackpacks;
import net.mcft.copy.backpacks.client.gui.*;
import net.mcft.copy.backpacks.client.gui.control.*;
import net.mcft.copy.backpacks.client.gui.test.GuiTestScreen;
import net.mcft.copy.backpacks.config.Setting;
import net.mcft.copy.backpacks.config.Setting.ChangeRequiredAction;

@SideOnly(Side.CLIENT)
public class BackpacksConfigScreen extends GuiContainerScreen {
	
	private final GuiScreen _parentScreen;
	
	protected GuiButton buttonDone;
	protected GuiButton buttonReset;
	protected GuiButton buttonUndo;
	protected EntryList entryList;
	
	/** Creates a config GUI screen for Wearable Backpacks (and its GENERAL category). */
	public BackpacksConfigScreen(GuiScreen parentScreen) {
		this(parentScreen, (String)null);
		
		// Add all settings from the GENERAL category to the entry list.
		for (Setting<?> setting : WearableBackpacks.CONFIG.getSettingsGeneral())
			addEntry(CreateEntryFromSetting(setting));
		
		// After adding all settings from the GENERAL category, add its sub-categories.
		for (String cat : WearableBackpacks.CONFIG.getCategories())
			addEntry(new EntryCategory(this, cat));
	}
	
	/** Creates a config GUI screen for a sub-category. */
	public BackpacksConfigScreen(GuiScreen parentScreen, EntryCategory category) {
		this(parentScreen, category.getLanguageKey());
		
		// Add all settings for this category to the entry list.
		for (Setting<?> setting : WearableBackpacks.CONFIG.getSettings(category.category))
			addEntry(CreateEntryFromSetting(setting));
	}
	
	public BackpacksConfigScreen(GuiScreen parentScreen, String title) {
		_parentScreen = parentScreen;
		
		container.add(new GuiButton(18, 18, "T") {
			{
				setRight(3); setTop(3);
				setAction(() -> display(new GuiTestScreen(BackpacksConfigScreen.this)));
			}
			@Override public boolean isVisible()
				{ return (super.isVisible() && GuiContext.DEBUG); }
		});
		
		container.add(new GuiLayout(Direction.VERTICAL) {{
			setFill();
			setSpacing(0);
			
			addFixed(new GuiLayout(Direction.VERTICAL) {{
				setFillHorizontal();
				setPaddingVertical(7);
				setSpacing(1);
				
				addFixed(new GuiLabel(WearableBackpacks.MOD_NAME) {{ setCenteredHorizontal(); }});
				if (title != null) addFixed(new GuiLabel(I18n.format(title)) {{ setCenteredHorizontal(); }});
			}});
			
			addWeighted(new EntryListScrollable(entryList = new EntryList()));
			
			addFixed(new GuiLayout(Direction.HORIZONTAL) {{
				setCenteredHorizontal();
				setPaddingVertical(3, 9);
				setSpacing(5);
				
				buttonDone = new GuiButton(I18n.format("gui.done"));
				if (buttonDone.getWidth() < 100) buttonDone.setWidth(100);
				buttonDone.setAction(() -> doneClicked());
				addFixed(buttonDone);
				
				buttonUndo = new GuiButtonGlyph(GuiUtils.UNDO_CHAR, I18n.format("fml.configgui.tooltip.undoChanges"));
				buttonUndo.setAction(() -> undoChanges());
				addFixed(buttonUndo);
				
				buttonReset = new GuiButtonGlyph(GuiUtils.RESET_CHAR, I18n.format("fml.configgui.tooltip.resetToDefault"));
				buttonReset.setAction(() -> setToDefault());
				addFixed(buttonReset);
			}});
		}});
		
	}
	
	
	public static <T> GuiElementBase CreateEntryFromSetting(Setting<T> setting) {
		String entryClassName = setting.getConfigEntryClass();
		if (entryClassName == null) throw new RuntimeException(
			"Setting '" + setting.getFullName() + "' has no entry class defined");
		try {
			Class<?> entryClass = Class.forName(entryClassName);
			if (!GuiElementBase.class.isAssignableFrom(entryClass))
				throw new Exception("Not a subclass of GuiElementBase");
			// Find a constructor that has exactly one parameter of a compatible setting type.
			Constructor<?> constructor = Arrays.stream(entryClass.getConstructors())
				.filter(c -> (c.getParameterCount() == 1) && c.getParameterTypes()[0].isAssignableFrom(setting.getClass()))
				.findFirst().orElseThrow(() -> new Exception("No compatible constructor found"));
			// Create and return a new instance of this entry class.
			return (GuiElementBase)constructor.newInstance(setting);
		} catch (Exception ex) { throw new RuntimeException(
			"Exception while instanciating setting entry for '" +
				setting.getFullName() + "' (entry class '" + entryClassName + "')", ex); }
	}
	
	
	/** Adds an entry to this screen's entry list. */
	public void addEntry(GuiElementBase entry) { entryList.addFixed(entry); }
	
	
	/** Returns whether any of this screen's entries were changed from their previous values. */
	public boolean isChanged() { return entryList.getEntries().anyMatch(IConfigEntry::isChanged); }
	/** Returns whether all of this screen's entries are equal to their default values. */
	public boolean isDefault() { return entryList.getEntries().allMatch(IConfigEntry::isDefault); }
	/** Returns whether all of this screen's entries represent a valid value. */
	public boolean isValid() { return entryList.getEntries().allMatch(IConfigEntry::isValid); }
	
	/** Sets all of this screen's entries back to their previous values. */
	public void undoChanges() { entryList.getEntries().forEach(IConfigEntry::undoChanges); }
	/** Sets all of this screen's entries to their default values. */
	public void setToDefault() { entryList.getEntries().forEach(IConfigEntry::setToDefault); }
	
	/** Applies changes made to this screen's entries.
	 *  Called when clicking "Done" on the main config screen. */
	public ChangeRequiredAction applyChanges() {
		return entryList.getEntries()
			.map(e -> e.applyChanges())
			.max(ChangeRequiredAction::compareTo)
			.orElse(ChangeRequiredAction.None);
	}
	
	/** Called when the "Done" buttons is clicked. */
	protected void doneClicked() {
		GuiScreen nextScreen = _parentScreen;
		// If this is the root config screen, apply the changes!
		if (!(_parentScreen instanceof BackpacksConfigScreen)) {
			if (applyChanges() == ChangeRequiredAction.RestartMinecraft)
				nextScreen = new GuiMessageDialog(_parentScreen,
					"fml.configgui.gameRestartTitle",
					new TextComponentString(I18n.format("fml.configgui.gameRestartRequired")),
					"fml.configgui.confirmRestartMessage");
			WearableBackpacks.CONFIG.save();
		}
		GuiElementBase.display(nextScreen);
	}
	
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		buttonDone.setEnabled(isValid());
		buttonUndo.setEnabled(isChanged());
		buttonReset.setEnabled(!isDefault());
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
	
	
	public static class EntryListScrollable extends GuiScrollable {
		
		public final EntryList entryList;
		
		public EntryListScrollable(EntryList entryList) {
			super(Direction.VERTICAL);
			setFillHorizontal();
			getScrollbar().setAlign(Direction.HORIZONTAL, new GuiScrollable.ContentMax(4));
			add(this.entryList = entryList);
		}
		
		@Override
		protected void updateChildSizes(Direction direction) {
			super.updateChildSizes(direction);
			// TODO: This could be simplified if the Alignment class contained logic for position / sizing of elements.
			if ((direction == Direction.HORIZONTAL) && (entryList != null))
				entryList.setWidth(entryList.maxLabelWidth + 8 + getWidth() / 2);
		}
		
	}
	
	public static class EntryList extends GuiLayout {
		
		public int maxLabelWidth;
		
		public EntryList() {
			super(Direction.VERTICAL);
			setCenteredHorizontal();
			setPaddingVertical(4, 3);
			setExpand(Direction.HORIZONTAL, false);
		}
		
		@Override
		protected void updateChildSizes(Direction direction) {
			super.updateChildSizes(direction);
			
			maxLabelWidth = getEntries()
				.map(e -> e.getLabel())
				.filter(Objects::nonNull)
				.mapToInt(GuiLabel::getWidth)
				.max().orElse(0);
			
			getEntries()
				.map(e -> e.getLabel())
				.filter(Objects::nonNull)
				.forEach(l -> l.setWidth(maxLabelWidth));
		}
		
		public Stream<GuiElementBase> getElements() { return children.stream(); }
		
		public Stream<IConfigEntry> getEntries() { return getElements()
			.filter(IConfigEntry.class::isInstance).map(IConfigEntry.class::cast); }
		
	}
	
}
