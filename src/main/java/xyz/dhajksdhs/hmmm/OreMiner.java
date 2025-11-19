package xyz.dhajksdhs.hmmm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import java.util.AbstractMap.SimpleEntry;
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
	public static int MaxDepth = 100;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Map<UUID,Boolean> enabled = new HashMap<>();
    public static boolean globalEnable = true;
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
                helper(serverWorld,block,pos,player);
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
                return 1;
            })).then(CommandManager.literal("add").then(CommandManager.argument("block", BlockStateArgumentType.blockState(commandRegistryAccess)).executes(con ->{
                addTargetedOres(BlockStateArgumentType.getBlockState(con, "block").getBlockState().getBlock(), con);
                return 1;
            }))).then(CommandManager.literal("remove").then(CommandManager.argument("block",BlockStateArgumentType.blockState(commandRegistryAccess)).executes(con ->{
                removeTargetedOres(BlockStateArgumentType.getBlockState(con,"block").getBlockState().getBlock(),con);
                return 1;
            }))).then(CommandManager.literal("reset").executes(con->{
                resetOres(con);
                return 1;
            })).then(CommandManager.literal("toggle").executes(con->{
                globalEnable = !globalEnable;
                return 1;
            })).then(CommandManager.literal("set").then(CommandManager.argument("setting", StringArgumentType.word()).suggests((con, builder)-> {builder.suggest("MaxAutomaticMiningDepth");return builder.buildFuture();}).then(CommandManager.argument("value", IntegerArgumentType.integer()).executes(con ->{
                switch (StringArgumentType.getString(con, "setting")) {
                    case "MaxAutomaticMiningDepth": //controls how many layers oreminer can mine at once
                        int val = IntegerArgumentType.getInteger(con,"value");
                        MaxDepth = val;
                        con.getSource().sendFeedback(()->Text.literal(String.format("Max mining depth is now set to %d blocks.",val)),true);
                        return 1;
                    default:
                        con.getSource().sendFeedback(() -> Text.literal("Command not found."),false);
                        return 1;
                }
            })))));
        }));
	}
    public void helper(ServerWorld world, Block Ore, BlockPos pos, PlayerEntity player) {
        Queue<SimpleEntry<BlockPos,Integer>> visit = new LinkedList<>();
        visit.add(new SimpleEntry<>(pos,1));
        Set<BlockPos>visited = new HashSet<>();
        int depth = 1;
        while (!visit.isEmpty()){
            SimpleEntry<BlockPos, Integer> p = visit.poll();
            depth = p.getValue();
            if (depth>MaxDepth)return;
            if (visited.contains(p.getKey()))continue;
            var state = world.getBlockState(p.getKey());
            if (state.getBlock().equals(Ore)){
                world.setBlockState(p.getKey(), Blocks.AIR.getDefaultState());
                Block.dropStacks(state,world,p.getKey(),null,player,player.getMainHandStack());
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), p.getKey().getX()+0.5,p.getKey().getY()+0.5,p.getKey().getZ()+0.5,20,0.25,0.25,0.25,0.5); //spawn breaking particles
                visit.add(new SimpleEntry<>(p.getKey().up(),depth+1));
                visit.add(new SimpleEntry<>(p.getKey().down(),depth+1));
                visit.add(new SimpleEntry<>(p.getKey().east(),depth+1));
                visit.add(new SimpleEntry<>(p.getKey().west(),depth+1));
                visit.add(new SimpleEntry<>(p.getKey().north(),depth+1));
                visit.add(new SimpleEntry<>(p.getKey().south(),depth+1));
            }
            visited.add(p.getKey());
        }

    }
    public static void listTargetedOres(PlayerEntity player) {
        String msg ="Effective blocks:\n";
        for (Block i : configuredOres){
            msg+=i.getName().getString()+"\n";
        }
        player.sendMessage(Text.literal(msg),false);
    }
    public static void addTargetedOres(Block ore, CommandContext<ServerCommandSource> context) {
        configuredOres.add(ore);
        context.getSource().sendFeedback(()->Text.literal(String.format("Added %s to vein-mineable blocks.",ore.getName().getString())),true);
    }
    public static void removeTargetedOres(Block ore, CommandContext<ServerCommandSource> context){
        if (configuredOres.remove(ore)){
            context.getSource().sendFeedback(()->Text.literal(String.format("Removed %s from vein-mineable blocks.",ore.getName().getString())),true);
        }else{
            context.getSource().sendFeedback(() ->Text.literal("Block not in vein-mineable list."),false); //wtf is user doing
        }
    }
    public static void resetOres(CommandContext<ServerCommandSource> context){
        configuredOres = new HashSet<>(ores);
        context.getSource().sendFeedback(()->Text.literal("Vein-mineable list has been reset."),true);
    }
}