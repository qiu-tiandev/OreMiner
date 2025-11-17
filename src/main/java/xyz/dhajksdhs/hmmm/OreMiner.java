package xyz.dhajksdhs.hmmm;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.particle.BlockStateParticleEffect;
import javax.swing.*;

public class OreMiner implements ModInitializer {
	public static final String MOD_ID = "ore-miner";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        final Set<Block> ores = Set.of(
                Blocks.COAL_ORE,
                Blocks.DEEPSLATE_COAL_ORE,
                Blocks.IRON_ORE,
                Blocks.DEEPSLATE_IRON_ORE,
                Blocks.GOLD_ORE,
                Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.COPPER_ORE,
                Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.EMERALD_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE,
                Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.NETHER_QUARTZ_ORE,
                Blocks.NETHER_GOLD_ORE
        );
		LOGGER.info("Hello Fabric world!");
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity)->{
            if (!(world instanceof ServerWorld serverWorld)) return true;
            ItemStack tool = player.getMainHandStack();
            if (!tool.isSuitableFor(state))return true; //check if player is menance
            Block block = state.getBlock();
            if (ores.contains(block)) {
                helper(serverWorld,block,pos,new HashSet<>());
                return true;
            }
            return true;
        });
	}
    public void helper(ServerWorld world, Block Ore,BlockPos pos, Set<BlockPos> visited){
        if (visited.contains(pos))return;
        visited.add(pos);
        var state = world.getBlockState(pos);
        Block target = world.getBlockState(pos).getBlock();
        if (!target.equals(Ore))return; // check if ore is same as ore mined
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        world.spawnEntity(new ItemEntity(world,pos.getX(),pos.getY(),pos.getZ(),new ItemStack(target.asItem()))); //drop item at desired ore location
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,20,0.25,0.25,0.25,0.5); //spawn breaking particles
        //recur
        helper(world,target,pos.up(),visited);
        helper(world,target,pos.down(),visited);
        helper(world,target,pos.north(),visited);
        helper(world,target,pos.south(),visited);
        helper(world,target,pos.east(),visited);
        helper(world,target,pos.west(),visited);
    }
}