package fi.dy.masa.litematica.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import fi.dy.masa.litematica.gui.base.GuiLitematicaBase;
import fi.dy.masa.litematica.gui.base.GuiLitematicaBase.InfoType;
import fi.dy.masa.litematica.util.ItemUtils;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;

public class SchematicVerifier
{
    private static MutablePair<IBlockState, IBlockState> mutablePair = new MutablePair<>();
    private static long clientTickStart;

    private final ArrayListMultimap<Pair<IBlockState, IBlockState>, BlockPos> missingBlocksPositions = ArrayListMultimap.create();
    private final ArrayListMultimap<Pair<IBlockState, IBlockState>, BlockPos> extraBlocksPositions = ArrayListMultimap.create();
    private final ArrayListMultimap<Pair<IBlockState, IBlockState>, BlockPos> mismatchedBlocksPositions = ArrayListMultimap.create();
    private final ArrayListMultimap<Pair<IBlockState, IBlockState>, BlockPos> mismatchedStatesPositions = ArrayListMultimap.create();
    private final HashSet<Pair<IBlockState, IBlockState>> ignoredMismatches = new HashSet<>();
    private final Set<ChunkPos> requiredChunks = new HashSet<>();
    private WorldClient worldClient;
    private WorldSchematic worldSchematic;
    private SchematicPlacement schematicPlacement;
    @Nullable
    private ICompletionListener completionListener;
    private boolean verificationActive;
    private boolean finished;
    private int totalRequiredChunks;
    private long schematicBlocks;
    private long clientBlocks;
    private long missingBlocks;
    private long extraBlocks;
    private long matchingBlocks;
    private long mismatchedBlocks;
    private long mismatchedStates;

    public static void onClientTickStart()
    {
        clientTickStart = System.nanoTime();
    }

    public boolean isActive()
    {
        return this.verificationActive;
    }

    public int getTotalChunks()
    {
        return this.totalRequiredChunks;
    }

    public int getUnseenChunks()
    {
        return this.requiredChunks.size();
    }

    public boolean isFinished()
    {
        return this.finished;
    }

    public long getSchematicTotalBlocks()
    {
        return this.schematicBlocks;
    }

    public long getRealWorldTotalBlocks()
    {
        return this.clientBlocks;
    }

    public long getMissingBlocks()
    {
        return this.missingBlocks;
    }

    public long getExtraBlocks()
    {
        return this.extraBlocks;
    }

    public long getMatchingBlocks()
    {
        return this.matchingBlocks;
    }

    public long getMismatchedBlocks()
    {
        return this.mismatchedBlocks;
    }

    public long getMismatchedStates()
    {
        return this.mismatchedStates;
    }

    public void reset()
    {
        this.stopVerification();

        this.finished = false;
        this.missingBlocksPositions.clear();
        this.extraBlocksPositions.clear();
        this.mismatchedBlocksPositions.clear();
        this.mismatchedStatesPositions.clear();
    }

    public void startVerification(WorldClient worldClient, WorldSchematic worldSchematic,
            SchematicPlacement schematicPlacement, ICompletionListener completionListener)
    {
        this.worldClient = worldClient;
        this.worldSchematic = worldSchematic;
        this.schematicPlacement = schematicPlacement;
        this.requiredChunks.clear();
        this.missingBlocksPositions.clear();
        this.extraBlocksPositions.clear();
        this.mismatchedBlocksPositions.clear();
        this.mismatchedStatesPositions.clear();
        this.requiredChunks.addAll(schematicPlacement.getTouchedChunks());
        this.totalRequiredChunks = this.requiredChunks.size();
        this.completionListener = completionListener;
        this.verificationActive = true;
    }

    public void stopVerification()
    {
        this.worldClient = null;
        this.worldSchematic = null;
        this.schematicPlacement = null;
        this.totalRequiredChunks = 0;
        this.requiredChunks.clear();
        this.missingBlocksPositions.clear();
        this.extraBlocksPositions.clear();
        this.mismatchedBlocksPositions.clear();
        this.mismatchedStatesPositions.clear();
        this.verificationActive = false;
        this.finished = false;
    }

    public boolean verifyChunks()
    {
        if (this.verificationActive)
        {
            Iterator<ChunkPos> iter = this.requiredChunks.iterator();

            while (iter.hasNext())
            {
                if ((System.nanoTime() - clientTickStart) >= 50000000L)
                {
                    break;
                }

                ChunkPos pos = iter.next();

                if (this.worldClient.getChunkProvider().isChunkGeneratedAt(pos.x, pos.z) &&
                    this.worldSchematic.getChunkProvider().isChunkGeneratedAt(pos.x, pos.z))
                {
                    Chunk chunkClient = this.worldClient.getChunkFromChunkCoords(pos.x, pos.z);
                    Chunk chunkSchematic = this.worldSchematic.getChunkFromChunkCoords(pos.x, pos.z);
                    Map<String, StructureBoundingBox> boxes = this.schematicPlacement.getBoxesWithinChunk(pos.x, pos.z);

                    for (StructureBoundingBox box : boxes.values())
                    {
                        this.verifyChunk(chunkClient, chunkSchematic, box);
                    }

                    iter.remove();
                }
            }

            if (this.requiredChunks.isEmpty())
            {
                this.verificationActive = false;
                this.finished = true;

                if (this.completionListener != null)
                {
                    this.completionListener.onTaskCompleted();
                }
            }
        }

        return this.verificationActive == false; // finished or stopped
    }

    public void ignoreStateMismatch(IBlockState stateExpected, IBlockState stateFound)
    {
        this.ignoreStateMismatch(Pair.of(stateExpected, stateFound));
    }

    public void ignoreStateMismatch(Pair<IBlockState, IBlockState> ignore)
    {
        this.ignoredMismatches.add(ignore);

        this.missingBlocksPositions.removeAll(ignore);
        this.extraBlocksPositions.removeAll(ignore);
        this.mismatchedBlocksPositions.removeAll(ignore);
        this.mismatchedStatesPositions.removeAll(ignore);
    }

    public void addIgnoredStateMismatches(Collection<Pair<IBlockState, IBlockState>> ignore)
    {
        for (Pair<IBlockState, IBlockState> pair : ignore)
        {
            this.ignoreStateMismatch(pair);
        }
    }

    public void setIgnoredStateMismatches(Collection<Pair<IBlockState, IBlockState>> ignore)
    {
        this.ignoredMismatches.clear();
        this.addIgnoredStateMismatches(ignore);
    }

    public List<BlockMismatch> getMismatchOverviewCombined()
    {
        List<BlockMismatch> list = new ArrayList<>();

        this.addCountFor(this.missingBlocksPositions, list);
        this.addCountFor(this.extraBlocksPositions, list);
        this.addCountFor(this.mismatchedBlocksPositions, list);
        this.addCountFor(this.mismatchedStatesPositions, list);

        Collections.sort(list);

        return list;
    }

    public List<BlockMismatch> getMismatchOverviewFor(MismatchType type)
    {
        List<BlockMismatch> list = new ArrayList<>();

        switch (type)
        {
            case ALL:
                return this.getMismatchOverviewCombined();
            case MISSING:
                this.addCountFor(this.missingBlocksPositions, list);
                break;
            case EXTRA:
                this.addCountFor(this.extraBlocksPositions, list);
                break;
            case WRONG_BLOCK:
                this.addCountFor(this.mismatchedBlocksPositions, list);
                break;
            case WRONG_STATE:
                this.addCountFor(this.mismatchedStatesPositions, list);
                break;
        }

        return list;
    }

    public List<Pair<IBlockState, IBlockState>> getIgnoredStateMismatchPairs(GuiLitematicaBase gui)
    {
        List<Pair<IBlockState, IBlockState>> list = Lists.newArrayList(this.ignoredMismatches);

        try
        {
            Collections.sort(list, new Comparator<Pair<IBlockState, IBlockState>>() {
                @Override
                public int compare(Pair<IBlockState, IBlockState> o1, Pair<IBlockState, IBlockState> o2)
                {
                    String name1 = Block.REGISTRY.getNameForObject(o1.getLeft().getBlock()).toString();
                    String name2 = Block.REGISTRY.getNameForObject(o2.getLeft().getBlock()).toString();

                    int val = name1.compareTo(name2);

                    if (val < 0)
                    {
                        return -1;
                    }
                    else if (val > 0)
                    {
                        return 1;
                    }
                    else
                    {
                        name1 = Block.REGISTRY.getNameForObject(o1.getRight().getBlock()).toString();
                        name2 = Block.REGISTRY.getNameForObject(o2.getRight().getBlock()).toString();

                        return name1.compareTo(name2);
                    }
                }
            });
        }
        catch (Exception e)
        {
            gui.addMessage(InfoType.ERROR, "litematica.error.generic.failed_to_sort_list_of_ignored_states");
        }

        return list;
    }

    private void addCountFor(ArrayListMultimap<Pair<IBlockState, IBlockState>, BlockPos> map, List<BlockMismatch> list)
    {
        for (Pair<IBlockState, IBlockState> pair : map.keySet())
        {
            list.add(new BlockMismatch(pair, map.get(pair).size()));
        }
    }

    private void verifyChunk(Chunk chunkClient, Chunk chunkSchematic, StructureBoundingBox box)
    {
        final IBlockState air = Blocks.AIR.getDefaultState();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

        for (int y = box.minY; y <= box.maxY; ++y)
        {
            for (int z = box.minZ; z <= box.maxZ; ++z)
            {
                for (int x = box.minX; x <= box.maxX; ++x)
                {
                    posMutable.setPos(x, y, z);
                    IBlockState stateClient = chunkClient.getBlockState(x, y, z).getActualState(chunkClient.getWorld(), posMutable);
                    IBlockState stateSchematic = chunkSchematic.getBlockState(x, y, z);

                    if (stateClient == stateSchematic)
                    {
                        if (stateSchematic != air)
                        {
                            this.schematicBlocks++;
                            this.clientBlocks++;
                            this.matchingBlocks++;
                        }
                    }
                    else
                    {
                        mutablePair.setLeft(stateSchematic);
                        mutablePair.setRight(stateClient);

                        if (this.ignoredMismatches.contains(mutablePair) == false)
                        {
                            BlockPos pos = new BlockPos(x, y, z);

                            if (stateSchematic != air)
                            {
                                if (stateClient == air)
                                {
                                    this.missingBlocksPositions.put(Pair.of(stateSchematic, stateClient), pos);
                                    this.missingBlocks++;
                                }
                                else
                                {
                                    if (stateSchematic.getBlock() != stateClient.getBlock())
                                    {
                                        this.mismatchedBlocksPositions.put(Pair.of(stateSchematic, stateClient), pos);
                                        this.mismatchedBlocks++;
                                    }
                                    else
                                    {
                                        this.mismatchedStatesPositions.put(Pair.of(stateSchematic, stateClient), pos);
                                        this.mismatchedStates++;
                                    }

                                    this.clientBlocks++;
                                }

                                this.schematicBlocks++;
                            }
                            else
                            {
                                this.extraBlocksPositions.put(Pair.of(stateSchematic, stateClient), pos);
                                this.clientBlocks++;
                                this.extraBlocks++;
                            }

                            ItemUtils.setItemForBlock(this.worldClient, pos, stateClient);
                            ItemUtils.setItemForBlock(this.worldSchematic, pos, stateSchematic);
                        }
                        else
                        {
                            if (stateSchematic != air)
                            {
                                this.schematicBlocks++;
                            }
                            else
                            {
                                this.clientBlocks++;
                            }
                        }
                    }
                }
            }
        }
    }

    public static class BlockMismatch implements Comparable<BlockMismatch>
    {
        public final Pair<IBlockState, IBlockState> statePair;
        public final int count;

        public BlockMismatch(Pair<IBlockState, IBlockState> statePair, int count)
        {
            this.statePair = statePair;
            this.count = count;
        }

        @Override
        public int compareTo(BlockMismatch other)
        {
            return this.count > other.count ? -1 : (this.count < other.count ? 1 : 0);
        }
    }

    public enum MismatchType
    {
        ALL         ("litematica.gui.label.schematic_verifier_display_type.all"),
        MISSING     ("litematica.gui.label.schematic_verifier_display_type.missing"),
        EXTRA       ("litematica.gui.label.schematic_verifier_display_type.extra"),
        WRONG_BLOCK ("litematica.gui.label.schematic_verifier_display_type.wrong_blocks"),
        WRONG_STATE ("litematica.gui.label.schematic_verifier_display_type.wrong_state");

        private final String unlocName;

        private MismatchType(String unlocName)
        {
            this.unlocName = unlocName;
        }

        public String getDisplayname()
        {
            return I18n.format(this.unlocName);
        }
    }
}