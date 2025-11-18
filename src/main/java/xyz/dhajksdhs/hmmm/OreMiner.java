package xyz.dhajksdhs.hmmm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;

import java.awt.*;
import java.util.*;
import net.minecraft.server.command.CommandManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
    public static final Map<UUID,Boolean> enabled = new HashMap<>();
    public static final Set<Block> ores = Set.of(
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
    public static Set<Block> configuredOres = new HashSet<>(ores);
	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity)->{
            if (!enabled.getOrDefault(player.getUuid(),true))return true;
            if (!(world instanceof ServerWorld serverWorld)) return true;
            ItemStack tool = player.getMainHandStack();
            if (!tool.isSuitableFor(state))return true; //check if player is menance
            Block block = state.getBlock();
            if (configuredOres.contains(block)) {
                helper(serverWorld,block,pos,new HashSet<>(),player);
                return true;
            }
            return true;
        });
        CommandRegistrationCallback.EVENT.register(((d, commandRegistryAccess, registrationEnvironment) ->{
            d.register(CommandManager.literal("oreminertoggle").executes(con -> {
                PlayerEntity player =  con.getSource().getPlayer();
                if (player == null)return 0;
                boolean playerstate = enabled.getOrDefault(player.getUuid(),true);
                enabled.put(player.getUuid(),!playerstate);// flip preference
                player.sendMessage(Text.literal(String.format("Ore Miner is %s.",playerstate?"§Enabled":"§Disabled")), false);
                return 1;
            }));
        } ));
        CommandRegistrationCallback.EVENT.register(((d, commandRegistryAccess, registrationEnvironment) ->{
            d.register(CommandManager.literal("oreminer").requires(source -> source.hasPermissionLevel(3)).then(CommandManager.literal("list").executes(con ->{
                listTargetedOres(con.getSource().getPlayer());
                return 0;
            })).then(CommandManager.literal("add").then(CommandManager.argument("block", BlockStateArgumentType.blockState(commandRegistryAccess)).executes(con ->{
                PlayerEntity player = con.getSource().getPlayer();
                addTargetedOres(BlockStateArgumentType.getBlockState(con, "block").getBlockState().getBlock(), player);
                return 0;
            }))).then(CommandManager.literal("remove").then(CommandManager.argument("block",BlockStateArgumentType.blockState(commandRegistryAccess)).executes(con ->{
                removeTargetedOres(BlockStateArgumentType.getBlockState(con,"block").getBlockState().getBlock(),con.getSource().getPlayer());
                return 0;
            }))).then(CommandManager.literal("reset").executes(con->{
                resetOres(con.getSource().getPlayer());
                return 0;
            })));
        }));
	}
    public void helper(ServerWorld world, Block Ore, BlockPos pos, Set<BlockPos> visited, PlayerEntity player) {
        if (visited.contains(pos))return;
        visited.add(pos);
        var state = world.getBlockState(pos);
        Block target = world.getBlockState(pos).getBlock();
        if (!target.equals(Ore))return; // check if ore is same as ore mined
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        Block.dropStacks(state,world,pos,null,player,player.getMainHandStack());
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,20,0.25,0.25,0.25,0.5); //spawn breaking particles
        //recur
        helper(world,target,pos.up(),visited, player);
        helper(world,target,pos.down(),visited,player);
        helper(world,target,pos.north(),visited,player);
        helper(world,target,pos.south(),visited,player);
        helper(world,target,pos.east(),visited,player);
        helper(world,target,pos.west(),visited,player);
    }
    public static void listTargetedOres(PlayerEntity player) {
        String msg ="Effective blocks:\n";
        for (Block i : configuredOres){
            msg+=i.getName().getString()+"\n";
        }
        player.sendMessage(Text.literal(msg),false);
    }
    public static void addTargetedOres(Block ore, PlayerEntity player) {
        configuredOres.add(ore);
        player.sendMessage(Text.literal(String.format("Added %s to vein-mineable blocks.",ore.getName().getString())),false);
    }
    public static void removeTargetedOres(Block ore, PlayerEntity player){
        if (configuredOres.remove(ore)){
            player.sendMessage(Text.literal(String.format("Removed %s from vein-mineable blocks.",ore.getName().getString())),false);
        }else{
            player.sendMessage(Text.literal("Block not in vein-mineable list."),false); //wtf is user doing
        }
    }
    public static void resetOres(PlayerEntity player){
        configuredOres = new HashSet<>(ores);
        player.sendMessage(Text.literal("Vein-mineable list has been reset."),false);
    }
}