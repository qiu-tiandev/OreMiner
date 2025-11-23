package xyz.dhajksdhs.hmmm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import java.util.*;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.particle.BlockStateParticleEffect;

public class OreMiner implements ModInitializer {
    public static final String MOD_ID = "ore-miner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Settings that will be saved/loaded
    public static int MaxDepth = 100;
    public static boolean globalEnable = true;
    public static final Map<UUID, Boolean> enabled = new HashMap<>();

    // Default ore blocks
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

    // This will be modified and saved
    public static Set<Block> configuredOres = new HashSet<>(ores);

    // Mod loads -> Config system initializes -> Config loads
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing OreMiner mod");

        // Initialize and load config
        ConfigManager.initialize();
        ConfigManager.loadConfig();

        // Auto Save on Server Stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping, saving config...");
            ConfigManager.saveConfig();
        });

        // Register block break event
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!enabled.getOrDefault(player.getUuid(), true)) return true;
            if (!(world instanceof ServerWorld serverWorld)) return true;

            ItemStack tool = player.getMainHandStack();
            if (!tool.isSuitableFor(state)) return true;

            Block block = state.getBlock();
            if (configuredOres.contains(block)) {
                helper(serverWorld, block, pos, player);
                return true;
            }
            return true;
        });

        // Register /oreminertoggle command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("oreminertoggle").executes(context -> {
                PlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;

                boolean playerState = enabled.getOrDefault(player.getUuid(), true);
                enabled.put(player.getUuid(), !playerState);

                player.sendMessage(Text.literal(String.format("Ore Miner is %s.",
                        !playerState ? "§aEnabled" : "§cDisabled")), false);

                // Save config when player changes preference (So wont be lost even on shut down)
                ConfigManager.saveConfig();
                return 1;
            }));
        });

        // Register /oreminer command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("oreminer")
                    .requires(source -> source.hasPermissionLevel(3))

                    // /oreminer list
                    .then(CommandManager.literal("list").executes(context -> {
                        listTargetedOres(context.getSource().getPlayer());
                        return 1;
                    }))

                    // /oreminer add <block>
                    .then(CommandManager.literal("add")
                            .then(CommandManager.argument("block", BlockStateArgumentType.blockState(registryAccess))
                                    .executes(context -> {
                                        addTargetedOres(
                                                BlockStateArgumentType.getBlockState(context, "block").getBlockState().getBlock(),
                                                context
                                        );
                                        ConfigManager.saveConfig(); // Save after modification
                                        return 1;
                                    })))

                    // /oreminer remove <block>
                    .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("block", BlockStateArgumentType.blockState(registryAccess))
                                    .executes(context -> {
                                        removeTargetedOres(
                                                BlockStateArgumentType.getBlockState(context, "block").getBlockState().getBlock(),
                                                context
                                        );
                                        ConfigManager.saveConfig(); // Save after modification
                                        return 1;
                                    })))

                    // /oreminer reset
                    .then(CommandManager.literal("reset").executes(context -> {
                        resetOres(context);
                        ConfigManager.saveConfig(); // Save after reset
                        return 1;
                    }))

                    // /oreminer toggle
                    .then(CommandManager.literal("toggle").executes(context -> {
                        globalEnable = !globalEnable;
                        context.getSource().sendFeedback(
                                () -> Text.literal(String.format("OreMiner globally %s.",
                                        globalEnable ? "§aEnabled" : "§cDisabled")),
                                true
                        );
                        ConfigManager.saveConfig(); // Save after toggle
                        return 1;
                    }))

                    // /oreminer set <setting> <value>
                    .then(CommandManager.literal("set")
                            .then(CommandManager.argument("setting", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("MaxAutomaticMiningDepth");
                                        return builder.buildFuture();
                                    })
                                    .then(CommandManager.argument("value", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                switch (StringArgumentType.getString(context, "setting")) {
                                                    case "MaxAutomaticMiningDepth":
                                                        int val = IntegerArgumentType.getInteger(context, "value");
                                                        MaxDepth = val;
                                                        context.getSource().sendFeedback(
                                                                () -> Text.literal(String.format(
                                                                        "Max mining depth is now set to %d blocks.", val)),
                                                                true
                                                        );
                                                        ConfigManager.saveConfig(); // Save after change
                                                        return 1;
                                                    default:
                                                        context.getSource().sendFeedback(
                                                                () -> Text.literal("§cUnknown setting."),
                                                                false
                                                        );
                                                        return 0;
                                                }
                                            }))))

                    // /oreminer reload
                    // Calls ConfigManager.reloadConfig()
                    // Re-reads the Json file from disk
                    // Updates all Java variables with new values
                    // Sends green success message
                    // Broadcasts to all operators
                    .then(CommandManager.literal("reload").executes(context -> {
                        ConfigManager.reloadConfig();
                        context.getSource().sendFeedback(
                                () -> Text.literal("§aOreMiner config reloaded successfully!"),
                                true
                        );
                        return 1;
                    }))
            );
        });
    }

    public void helper(ServerWorld world, Block Ore, BlockPos pos, PlayerEntity player) {
        Queue<SimpleEntry<BlockPos, Integer>> visit = new LinkedList<>();
        visit.add(new SimpleEntry<>(pos, 1));
        Set<BlockPos> visited = new HashSet<>();
        int depth = 1;

        while (!visit.isEmpty()) {
            SimpleEntry<BlockPos, Integer> p = visit.poll();
            depth = p.getValue();
            if (depth > MaxDepth) return;
            if (visited.contains(p.getKey())) continue;

            var state = world.getBlockState(p.getKey());
            if (state.getBlock().equals(Ore)) {
                world.setBlockState(p.getKey(), Blocks.AIR.getDefaultState());
                Block.dropStacks(state, world, p.getKey(), null, player, player.getMainHandStack());
                world.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        p.getKey().getX() + 0.5, p.getKey().getY() + 0.5, p.getKey().getZ() + 0.5,
                        20, 0.25, 0.25, 0.25, 0.5
                );

                visit.add(new SimpleEntry<>(p.getKey().up(), depth + 1));
                visit.add(new SimpleEntry<>(p.getKey().down(), depth + 1));
                visit.add(new SimpleEntry<>(p.getKey().east(), depth + 1));
                visit.add(new SimpleEntry<>(p.getKey().west(), depth + 1));
                visit.add(new SimpleEntry<>(p.getKey().north(), depth + 1));
                visit.add(new SimpleEntry<>(p.getKey().south(), depth + 1));
            }
            visited.add(p.getKey());
        }
    }

    public static void listTargetedOres(PlayerEntity player) {
        String msg = "Effective blocks:\n";
        for (Block i : configuredOres) {
            msg += i.getName().getString() + "\n";
        }
        player.sendMessage(Text.literal(msg), false);
    }

    public static void addTargetedOres(Block ore, CommandContext<ServerCommandSource> context) {
        configuredOres.add(ore);
        context.getSource().sendFeedback(
                () -> Text.literal(String.format("Added %s to vein-mineable blocks.",
                        ore.getName().getString())),
                true
        );
    }

    public static void removeTargetedOres(Block ore, CommandContext<ServerCommandSource> context) {
        if (configuredOres.remove(ore)) {
            context.getSource().sendFeedback(
                    () -> Text.literal(String.format("Removed %s from vein-mineable blocks.",
                            ore.getName().getString())),
                    true
            );
        } else {
            context.getSource().sendFeedback(
                    () -> Text.literal("§cBlock not in vein-mineable list."),
                    false
            );
        }
    }

    public static void resetOres(CommandContext<ServerCommandSource> context) {
        configuredOres = new HashSet<>(ores);
        context.getSource().sendFeedback(
                () -> Text.literal("Vein-mineable list has been reset."),
                true
        );
    }
}