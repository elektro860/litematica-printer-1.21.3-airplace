package fi.dy.masa.litematica.gui;

import java.io.File;
import javax.annotation.Nullable;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.World;

public class GuiSchematicSave extends GuiSchematicSaveBase
{
    private final SelectionManager selectionManager;

    public GuiSchematicSave()
    {
        this(null);
    }

    public GuiSchematicSave(@Nullable LitematicaSchematic schematic)
    {
        super(schematic);

        if (schematic != null)
        {
            this.title = I18n.format("litematica.gui.title.save_schematic_from_memory");
        }
        else
        {
            this.title = I18n.format("litematica.gui.title.create_schematic_from_selection");
        }

        this.selectionManager = DataManager.getInstance().getSelectionManager();

        AreaSelection area = this.selectionManager.getCurrentSelection();

        if (area != null)
        {
            this.defaultText = area.getName();
        }
    }

    @Override
    public String getBrowserContext()
    {
        return "schematic_save";
    }

    @Override
    public File getDefaultDirectory()
    {
        return DataManager.ROOT_SCHEMATIC_DIRECTORY;
    }

    @Override
    protected IButtonActionListener<ButtonGeneric> createButtonListener(ButtonType type)
    {
        return new ButtonListener(type, this.selectionManager, this);
    }

    private static class ButtonListener implements IButtonActionListener<ButtonGeneric>
    {
        private final GuiSchematicSave gui;
        private final SelectionManager selectionManager;
        private final ButtonType type;

        public ButtonListener(ButtonType type, SelectionManager selectionManager, GuiSchematicSave gui)
        {
            this.type = type;
            this.selectionManager = selectionManager;
            this.gui = gui;
        }

        @Override
        public void actionPerformed(ButtonGeneric control)
        {
            if (this.type == ButtonType.SAVE)
            {
                File dir = this.gui.widget.getCurrentDirectory();
                String fileName = this.gui.textField.getText();

                if (dir.isDirectory() == false)
                {
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_directory", dir.getAbsolutePath());
                    return;
                }

                if (fileName.isEmpty())
                {
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_schematic_name", fileName);
                    return;
                }

                boolean takeEntities = true; // TODO
                LitematicaSchematic schematic = this.gui.schematic;

                if (schematic == null)
                {
                    schematic = this.createSchematicFromWorld(takeEntities);
                }
                else
                {
                    schematic.getMetadata().setTimeModified(System.currentTimeMillis());
                }

                if (schematic != null)
                {
                    if (schematic.writeToFile(dir, fileName, GuiScreen.isShiftKeyDown(), this.gui))
                    {
                        this.gui.addMessage(MessageType.SUCCESS, "litematica.message.schematic_saved_as", fileName);
                        this.gui.widget.refreshEntries();
                    }
                }
                else
                {
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.schematic_creation_failed");
                }
            }
            else if (this.type == ButtonType.CREATE_DIRECTORY)
            {
                File dir = this.gui.widget.getCurrentDirectory();
                String title = "litematica.gui.title.create_directory";
                this.gui.mc.displayGuiScreen(new GuiTextInput(256, title, "", this.gui, new DirectoryCreator(dir, this.gui, this.gui.widget)));
            }
        }

        @Override
        public void actionPerformedWithButton(ButtonGeneric control, int mouseButton)
        {
            this.actionPerformed(control);
        }

        @Nullable
        private LitematicaSchematic createSchematicFromWorld(boolean takeEntities)
        {
            AreaSelection area = this.selectionManager.getCurrentSelection();

            if (area != null)
            {
                String author = this.gui.mc.player.getName();
                World world = WorldUtils.getBestWorld(this.gui.mc);

                if (world != null)
                {
                    return LitematicaSchematic.createFromWorld(world, area, takeEntities, author, this.gui);
                }
            }

            return null;
        }
    }

    public static class InMemorySchematicCreator implements IStringConsumer
    {
        private final AreaSelection area;
        private final Minecraft mc;

        public InMemorySchematicCreator(AreaSelection area)
        {
            this.area = area;
            this.mc = Minecraft.getMinecraft();
        }

        @Override
        public void setString(String string)
        {
            boolean takeEntities = true; // TODO
            String author = this.mc.player.getName();
            LitematicaSchematic schematic = LitematicaSchematic.createFromWorld(this.mc.world, this.area, takeEntities, author, InfoUtils.INFO_MESSAGE_CONSUMER);

            if (schematic != null)
            {
                schematic.getMetadata().setName(string);
                SchematicHolder.getInstance().addSchematic(schematic, true);
                StringUtils.printActionbarMessage("litematica.message.in_memory_schematic_created", string);
            }
        }
    }
}
